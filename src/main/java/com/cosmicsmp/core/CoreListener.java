package com.cosmicsmp.core;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.task.EndReason;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Ends every effect a player takes part in when they leave or die (before their data is saved). */
public final class CoreListener implements Listener {

    private final CosmicSMP plugin;

    public CoreListener(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        plugin.effects().cancelFor(event.getPlayer().getUniqueId(), EndReason.QUIT);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        plugin.effects().cancelFor(event.getEntity().getUniqueId(), EndReason.DEATH);
    }
}
