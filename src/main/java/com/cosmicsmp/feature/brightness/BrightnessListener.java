package com.cosmicsmp.feature.brightness;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.api.event.BrightnessChangeEvent;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/** Kill / death brightness rules. */
public final class BrightnessListener implements Listener {

    private final CosmicSMP plugin;

    public BrightnessListener(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Settings s = plugin.settings();
        if (s.worldDisabled(victim.getWorld().getName())) {
            return;
        }
        Player killer = plugin.combat().resolveKiller(victim);
        plugin.combat().clearHits(victim.getUniqueId());
        PlayerData victimData = plugin.players().get(victim);
        victimData.stats.deaths++;

        if (killer != null) {
            PlayerData killerData = plugin.players().get(killer);
            killerData.stats.kills++;
            Long last = killerData.recentKills.get(victim.getUniqueId().toString());
            long now = System.currentTimeMillis();
            boolean farmed = s.antiFarm && last != null && now - last < s.antiFarmMillis;
            if (farmed) {
                plugin.messages().send(killer, "brightness.anti-farm", Placeholders.of("victim", victim.getName()));
            } else {
                killerData.recentKills.put(victim.getUniqueId().toString(), now);
                plugin.brightness().change(killer, s.gainOnKill, BrightnessChangeEvent.Cause.KILL);
            }
            plugin.players().saveSoon(killerData);
        }
        if (killer != null || s.loseOnAnyDeath) {
            plugin.brightness().change(victim, -s.loseOnDeath, BrightnessChangeEvent.Cause.DEATH);
        }
        plugin.players().saveSoon(victimData);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        plugin.brightness().updateLeaderboard(plugin.players().get(event.getPlayer()));
    }
}
