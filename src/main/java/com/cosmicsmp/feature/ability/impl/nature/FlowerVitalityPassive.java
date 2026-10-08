package com.cosmicsmp.feature.ability.impl.nature;

import com.cosmicsmp.core.Keys;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.feature.ability.PassiveAbility;
import com.cosmicsmp.feature.star.AbilityDefinition;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Nature Star passive: standing near flowers grants +2 hearts. Uses a transient attribute modifier (never saved to
 * the player file), so the bonus can't survive a crash or the plugin being removed.
 */
public final class FlowerVitalityPassive implements PassiveAbility {

    public static final String ID = "flower_vitality";

    private final Map<UUID, Long> lastFill = new HashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int tickInterval() {
        return 40;
    }

    @Override
    public void tick(Player player, AbilityDefinition def) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth == null) {
            return;
        }
        boolean near = flowersNear(player.getLocation(), def.integer("radius", 3));
        boolean active = maxHealth.getModifier(Keys.FLOWER_VITALITY) != null;
        if (near && !active) {
            double bonus = def.num("health-bonus", 4);
            maxHealth.addTransientModifier(new AttributeModifier(Keys.FLOWER_VITALITY, bonus, AttributeModifier.Operation.ADD_NUMBER));
            long now = System.currentTimeMillis();
            Long last = lastFill.get(player.getUniqueId());
            if (def.bool("fill-hearts", true) && (last == null || now - last > def.integer("fill-cooldown-seconds", 60) * 1000L)) {
                lastFill.put(player.getUniqueId(), now);
                player.setHealth(Math.min(maxHealth.getValue(), player.getHealth() + bonus));
            }
            Location at = player.getLocation().add(0, 1, 0);
            ParticleSpec.of(Particle.HAPPY_VILLAGER, 8, 0.5, 0).spawn(at, Fx.viewers(at));
            ParticleSpec.of(Particle.CHERRY_LEAVES, 6, 0.6, 0).spawn(at, Fx.viewers(at));
            SoundSpec.of("block.azalea_leaves.place", 0.6f, 1.4f).play(player);
        } else if (!near && active) {
            remove(player);
        }
    }

    @Override
    public void onDeactivate(Player player, AbilityDefinition def) {
        remove(player);
        lastFill.remove(player.getUniqueId());
    }

    private static void remove(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        if (maxHealth != null && maxHealth.getModifier(Keys.FLOWER_VITALITY) != null) {
            maxHealth.removeModifier(Keys.FLOWER_VITALITY);
            if (player.getHealth() > maxHealth.getValue()) {
                player.setHealth(maxHealth.getValue());
            }
        }
    }

    private static boolean flowersNear(Location location, int radius) {
        World world = location.getWorld();
        int bx = location.getBlockX();
        int by = location.getBlockY();
        int bz = location.getBlockZ();
        for (int y = by - 1; y <= by + 2; y++) {
            for (int x = bx - radius; x <= bx + radius; x++) {
                for (int z = bz - radius; z <= bz + radius; z++) {
                    if (Tag.FLOWERS.isTagged(world.getBlockAt(x, y, z).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
