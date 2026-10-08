package com.cosmicsmp.core.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Server-wide state stored in {@code data/global.json}: stock, merchants, death bans and a leaderboard index. */
public final class GlobalData {

    public int version = 1;
    /** starId -> remaining stock (-1 = unlimited). */
    public Map<String, Integer> stock = new LinkedHashMap<>();
    public Map<String, MerchantPoint> merchants = new LinkedHashMap<>();
    /** uuid -> ban. Concurrent because it is read from the async pre-login thread. */
    public ConcurrentHashMap<String, BanEntry> bans = new ConcurrentHashMap<>();
    /** uuid -> cached leaderboard row (lets /brightness top work for offline players without loading files). */
    public Map<String, LeaderEntry> leaderboard = new LinkedHashMap<>();
    public long lastRestock;

    public void normalize() {
        if (stock == null) {
            stock = new LinkedHashMap<>();
        }
        if (merchants == null) {
            merchants = new LinkedHashMap<>();
        }
        if (bans == null) {
            bans = new ConcurrentHashMap<>();
        }
        if (leaderboard == null) {
            leaderboard = new LinkedHashMap<>();
        }
    }

    public static final class MerchantPoint {
        public String world;
        public double x;
        public double y;
        public double z;
        public float yaw;
        public float pitch;

        public MerchantPoint() {
        }

        public MerchantPoint(String world, double x, double y, double z, float yaw, float pitch) {
            this.world = world;
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    public static final class BanEntry {
        public String name;
        public long since;
        /** Epoch millis, or -1 for permanent. */
        public long until;
        public String reason;

        public BanEntry() {
        }

        public BanEntry(String name, long since, long until, String reason) {
            this.name = name;
            this.since = since;
            this.until = until;
            this.reason = reason;
        }

        public boolean expired(long now) {
            return until > 0 && now >= until;
        }
    }

    public static final class LeaderEntry {
        public String name;
        public int brightness;
        public int kills;

        public LeaderEntry() {
        }

        public LeaderEntry(String name, int brightness, int kills) {
            this.name = name;
            this.brightness = brightness;
            this.kills = kills;
        }
    }
}
