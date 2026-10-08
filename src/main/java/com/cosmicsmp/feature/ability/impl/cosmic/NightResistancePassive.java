package com.cosmicsmp.feature.ability.impl.cosmic;

import com.cosmicsmp.feature.ability.PassiveAbility;
import com.cosmicsmp.feature.star.AbilityDefinition;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Cosmic Star passive: Resistance II at night (refreshed short effect - never stuck after a crash). */
public final class NightResistancePassive implements PassiveAbility {

    public static final String ID = "night_resistance";
    private static final int DURATION = 60;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public int tickInterval() {
        return 20;
    }

    @Override
    public void tick(Player player, AbilityDefinition def) {
        World world = player.getWorld();
        long time = world.getTime();
        boolean night = world.getEnvironment() == World.Environment.NORMAL
                && time >= def.integer("night-start", 13000) && time <= def.integer("night-end", 23000);
        if (night) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.RESISTANCE);
            int amplifier = def.integer("amplifier", 1);
            if (current == null || (ours(current) && current.getDuration() < DURATION - 15)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, DURATION, amplifier, true, false, true));
            }
        } else {
            remove(player);
        }
    }

    @Override
    public void onDeactivate(Player player, AbilityDefinition def) {
        remove(player);
    }

    private static void remove(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.RESISTANCE);
        if (current != null && ours(current)) {
            player.removePotionEffect(PotionEffectType.RESISTANCE);
        }
    }

    private static boolean ours(PotionEffect effect) {
        return effect.isAmbient() && !effect.hasParticles() && effect.getDuration() <= DURATION;
    }
}
