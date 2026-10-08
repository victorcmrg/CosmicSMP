package com.cosmicsmp.feature.ability;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Crash / restart protection for time-based effects.
 * <p>
 * An effect that changes a player in a way that outlives a tick (disguise, flight, phase...) writes a
 * {@link PlayerData.TimedState} with an absolute expiry before applying itself. When the player joins again
 * (after a quit, restart, crash or reload) every stored state is handed to its handler, which either resumes the
 * effect for the remaining time or cleans it up safely.
 */
public final class TimedStateService implements Listener {

    public interface Handler {
        /** State still valid: continue the effect for {@code remainingMillis}. */
        void resume(Player player, PlayerData.TimedState state, long remainingMillis);

        /** State expired while offline: undo anything that could still be applied to the player. */
        void expire(Player player, PlayerData.TimedState state);
    }

    private final CosmicSMP plugin;
    private final Map<String, Handler> handlers = new HashMap<>();

    public TimedStateService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void register(String key, Handler handler) {
        handlers.put(key, handler);
    }

    public PlayerData.TimedState set(Player player, String key, long durationMillis) {
        PlayerData data = plugin.players().get(player);
        PlayerData.TimedState state = new PlayerData.TimedState(System.currentTimeMillis() + durationMillis);
        data.states.put(key, state);
        plugin.players().saveSoon(data);
        return state;
    }

    public PlayerData.TimedState get(Player player, String key) {
        return plugin.players().get(player).states.get(key);
    }

    public void clear(Player player, String key) {
        PlayerData data = plugin.players().get(player);
        if (data.states.remove(key) != null) {
            plugin.players().saveSoon(data);
        }
    }

    /** Re-applies / cleans states. Called one tick after join so the player is fully in the world. */
    public void restore(Player player) {
        PlayerData data = plugin.players().get(player);
        if (data.states.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (String key : List.copyOf(data.states.keySet())) {
            PlayerData.TimedState state = data.states.get(key);
            Handler handler = handlers.get(key);
            if (handler == null) {
                if (state == null || state.expired(now)) {
                    data.states.remove(key);
                }
                continue;
            }
            try {
                if (state.expired(now)) {
                    data.states.remove(key);
                    handler.expire(player, state);
                } else {
                    handler.resume(player, state, state.expiresAt - now);
                }
            } catch (RuntimeException ex) {
                data.states.remove(key);
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Could not restore state '" + key + "' for " + player.getName(), ex);
            }
        }
        plugin.players().saveSoon(data);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                restore(player);
            }
        }, 2L);
    }
}
