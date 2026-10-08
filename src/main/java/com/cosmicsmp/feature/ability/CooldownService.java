package com.cosmicsmp.feature.ability;

import com.cosmicsmp.core.data.PlayerData;

/**
 * Cooldowns stored in the player's file as absolute end timestamps: a restart, crash or relog never resets them.
 */
public final class CooldownService {

    public long remainingMillis(PlayerData data, String key) {
        Long end = data.cooldowns.get(key);
        if (end == null) {
            return 0;
        }
        long left = end - System.currentTimeMillis();
        if (left <= 0) {
            data.cooldowns.remove(key);
            return 0;
        }
        return left;
    }

    public boolean ready(PlayerData data, String key) {
        return remainingMillis(data, key) <= 0;
    }

    public void set(PlayerData data, String key, long millis) {
        if (millis <= 0) {
            data.cooldowns.remove(key);
        } else {
            data.cooldowns.put(key, System.currentTimeMillis() + millis);
        }
        data.dirty = true;
    }

    public void clear(PlayerData data) {
        data.cooldowns.clear();
        data.dirty = true;
    }

    public static String format(long millis) {
        if (millis <= 0) {
            return "0s";
        }
        if (millis < 10_000) {
            return String.format(java.util.Locale.ROOT, "%.1fs", millis / 1000.0);
        }
        long seconds = (millis + 999) / 1000;
        if (seconds < 60) {
            return seconds + "s";
        }
        return (seconds / 60) + "m " + (seconds % 60) + "s";
    }
}
