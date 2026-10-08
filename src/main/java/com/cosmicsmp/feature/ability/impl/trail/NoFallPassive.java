package com.cosmicsmp.feature.ability.impl.trail;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.feature.ability.PassiveAbility;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;

/** Trail Star passive: no fall damage, with a soft landing puff on big falls. */
public final class NoFallPassive implements PassiveAbility {

    public static final String ID = "no_fall";
    private static final ParticleSpec PUFF = ParticleSpec.of(Particle.CLOUD, 10, 0.4, 0.02);

    private final CosmicSMP plugin;

    public NoFallPassive(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && event.getEntity() instanceof Player player
                && plugin.passives().has(player, ID)) {
            event.setCancelled(true);
            if (player.getFallDistance() > 6) {
                PUFF.spawn(player.getLocation().add(0, 0.1, 0), Fx.viewers(player.getLocation()));
            }
        }
    }
}
