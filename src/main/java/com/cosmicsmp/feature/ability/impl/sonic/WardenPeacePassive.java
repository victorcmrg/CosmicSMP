package com.cosmicsmp.feature.ability.impl.sonic;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.feature.ability.PassiveAbility;
import io.papermc.paper.event.entity.WardenAngerChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/** Sonic Star passive: natural Wardens are peaceful (no anger, no targeting, no hits, no darkness pulses). */
public final class WardenPeacePassive implements PassiveAbility {

    public static final String ID = "warden_peace";

    private final CosmicSMP plugin;

    public WardenPeacePassive(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    private boolean protectedFrom(Warden warden, Object target) {
        return target instanceof Player player && plugin.summons().get(warden) == null && plugin.passives().has(player, ID);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntity() instanceof Warden warden && protectedFrom(warden, event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onAnger(WardenAngerChangeEvent event) {
        if (protectedFrom(event.getEntity(), event.getTarget())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Warden warden && protectedFrom(warden, event.getEntity())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDarkness(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.WARDEN && event.getEntity() instanceof Player player
                && plugin.passives().has(player, ID)) {
            event.setCancelled(true);
        }
    }
}
