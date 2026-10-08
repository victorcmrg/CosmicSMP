package com.cosmicsmp.feature.ability.impl;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.feature.ability.TimedStateService;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Crash-safe flights (Dragon Ride, Comet Ride). A persisted "ride" state guarantees a player who crashed or logged
 * out mid-air gets Slow Falling + fall protection on their next join instead of falling to their death.
 */
public final class RideStates {

    public static final String KEY = "ride";
    private static final int LANDING_TICKS = 100;

    private RideStates() {
    }

    public static void register(CosmicSMP plugin) {
        plugin.timedStates().register(KEY, new TimedStateService.Handler() {
            @Override
            public void resume(Player player, PlayerData.TimedState state, long remainingMillis) {
                land(plugin, player);
                plugin.timedStates().clear(player, KEY);
            }

            @Override
            public void expire(Player player, PlayerData.TimedState state) {
                land(plugin, player);
            }
        });
    }

    public static void begin(CosmicSMP plugin, Player player, int durationTicks) {
        plugin.timedStates().set(player, KEY, durationTicks * 50L + 5000L);
    }

    public static void end(CosmicSMP plugin, Player player, EndReason reason) {
        if (reason == EndReason.QUIT || !player.isOnline()) {
            return; // keep the state: landing protection is applied on the next join
        }
        land(plugin, player);
        plugin.timedStates().clear(player, KEY);
    }

    private static void land(CosmicSMP plugin, Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, LANDING_TICKS, 0, false, false, true));
        plugin.perks().grantNoFall(player, LANDING_TICKS + 40);
    }
}
