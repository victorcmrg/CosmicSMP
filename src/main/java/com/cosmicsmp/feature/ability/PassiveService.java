package com.cosmicsmp.feature.ability;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.feature.star.AbilityDefinition;
import com.cosmicsmp.feature.star.StarDefinition;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tracks which passives each online player has active and ticks periodic passives.
 * Lookups ({@link #has}) are O(1) map reads, so event-based passives cost nothing for players without them.
 */
public final class PassiveService implements Listener {

    private final CosmicSMP plugin;
    private final Map<UUID, Map<String, AbilityDefinition>> active = new HashMap<>();
    private BukkitTask task;
    private long tick;

    public PassiveService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        for (PassiveAbility passive : plugin.abilities().passives()) {
            Bukkit.getPluginManager().registerEvents(passive, plugin);
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
        for (PassiveAbility passive : plugin.abilities().passives()) {
            HandlerList.unregisterAll(passive);
        }
    }

    /** Lets an expansion register a passive after startup. */
    public void registerLate(PassiveAbility passive) {
        if (task != null) {
            Bukkit.getPluginManager().registerEvents(passive, plugin);
        }
    }

    public boolean has(Player player, String passiveId) {
        Map<String, AbilityDefinition> map = active.get(player.getUniqueId());
        return map != null && map.containsKey(passiveId);
    }

    public AbilityDefinition definition(Player player, String passiveId) {
        Map<String, AbilityDefinition> map = active.get(player.getUniqueId());
        return map == null ? null : map.get(passiveId);
    }

    /** Recomputes the active passives from the player's owned stars (call after any star change). */
    public void refresh(Player player) {
        PlayerData data = plugin.players().get(player);
        Map<String, AbilityDefinition> next = new HashMap<>();
        if (!plugin.settings().worldDisabled(player.getWorld().getName())) {
            for (Map.Entry<String, PlayerData.OwnedStar> entry : data.stars.entrySet()) {
                StarDefinition star = plugin.stars().get(entry.getKey());
                if (star == null || star.passive() == null || !entry.getValue().passive) {
                    continue;
                }
                AbilityDefinition def = star.passive();
                if (plugin.abilities().passive(def.abilityId()) != null) {
                    next.put(def.abilityId().toLowerCase(java.util.Locale.ROOT), def);
                }
            }
        }
        Map<String, AbilityDefinition> previous = active.getOrDefault(player.getUniqueId(), Map.of());
        for (Map.Entry<String, AbilityDefinition> old : previous.entrySet()) {
            if (!next.containsKey(old.getKey())) {
                safe(() -> plugin.abilities().passive(old.getKey()).onDeactivate(player, old.getValue()));
            }
        }
        if (next.isEmpty()) {
            active.remove(player.getUniqueId());
        } else {
            active.put(player.getUniqueId(), next);
        }
        for (Map.Entry<String, AbilityDefinition> now : next.entrySet()) {
            if (!previous.containsKey(now.getKey())) {
                safe(() -> plugin.abilities().passive(now.getKey()).onActivate(player, now.getValue()));
            }
        }
    }

    public void clear(Player player) {
        Map<String, AbilityDefinition> previous = active.remove(player.getUniqueId());
        if (previous != null) {
            previous.forEach((id, def) -> safe(() -> plugin.abilities().passive(id).onDeactivate(player, def)));
        }
    }

    private void tick() {
        tick++;
        if (active.isEmpty()) {
            return;
        }
        for (Map.Entry<UUID, Map<String, AbilityDefinition>> entry : active.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || player.isDead()) {
                continue;
            }
            for (Map.Entry<String, AbilityDefinition> passiveEntry : entry.getValue().entrySet()) {
                PassiveAbility passive = plugin.abilities().passive(passiveEntry.getKey());
                if (passive == null) {
                    continue;
                }
                int interval = passive.tickInterval();
                // spread players across ticks so periodic work never spikes on one tick
                if (interval > 0 && (tick + (entry.getKey().hashCode() & 0x7fff)) % interval == 0) {
                    safe(() -> passive.tick(player, passiveEntry.getValue()));
                }
            }
        }
    }

    private void safe(Runnable runnable) {
        try {
            runnable.run();
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Passive error", ex);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (event.getPlayer().isOnline()) {
                refresh(event.getPlayer());
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(org.bukkit.event.player.PlayerChangedWorldEvent event) {
        refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        clear(event.getPlayer());
    }
}
