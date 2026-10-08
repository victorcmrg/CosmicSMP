package com.cosmicsmp.core.data;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

/**
 * Everything the plugin knows about one player, stored as {@code data/players/<uuid>.json}.
 * <p>
 * Every time-based value is an absolute epoch timestamp (cooldowns, timed states, anti-farm), never a
 * "ticks remaining" counter, so restarts, crashes and reloads cannot reset or extend anything.
 * Mutated on the main thread only.
 */
public final class PlayerData {

    public static final int CURRENT_VERSION = 1;

    public int version = CURRENT_VERSION;
    public UUID uuid;
    public String name;
    public int brightness;
    /** Stars currently owned (lost on death). */
    public Map<String, OwnedStar> stars = new LinkedHashMap<>();
    /** Stars bought at least once - permanent unlock that bypasses stock. */
    public Set<String> discovered = new LinkedHashSet<>();
    /** key -> epoch millis when the cooldown ends. */
    public Map<String, Long> cooldowns = new HashMap<>();
    /** Persistent timed states (rides, disguises, phases) that must survive restarts. */
    public Map<String, TimedState> states = new HashMap<>();
    /** victim uuid -> epoch millis of the last brightness-granting kill (anti-farm). */
    public Map<String, Long> recentKills = new HashMap<>();
    public Stats stats = new Stats();
    public long firstJoin;
    public long lastSeen;

    public transient boolean dirty;

    public PlayerData() {
    }

    public PlayerData(UUID uuid, String name, int brightness) {
        this.uuid = uuid;
        this.name = name;
        this.brightness = brightness;
        this.firstJoin = System.currentTimeMillis();
        this.lastSeen = firstJoin;
    }

    /** Repairs nulls from hand-edited / older files. */
    public void normalize(UUID expected) {
        if (uuid == null) {
            uuid = expected;
        }
        if (stars == null) {
            stars = new LinkedHashMap<>();
        }
        stars.values().removeIf(java.util.Objects::isNull);
        stars.values().forEach(OwnedStar::normalize);
        if (discovered == null) {
            discovered = new LinkedHashSet<>();
        }
        if (cooldowns == null) {
            cooldowns = new HashMap<>();
        }
        if (states == null) {
            states = new HashMap<>();
        }
        if (recentKills == null) {
            recentKills = new HashMap<>();
        }
        if (stats == null) {
            stats = new Stats();
        }
        version = CURRENT_VERSION;
    }

    /** Drops expired cooldowns / anti-farm entries so files stay small. */
    public void prune(long now, long antiFarmWindow) {
        cooldowns.values().removeIf(end -> end <= now);
        Iterator<Long> it = recentKills.values().iterator();
        while (it.hasNext()) {
            if (it.next() + antiFarmWindow <= now) {
                it.remove();
            }
        }
    }

    public boolean owns(String starId) {
        return stars.containsKey(starId);
    }

    public OwnedStar star(String starId) {
        return stars.get(starId);
    }

    public static final class OwnedStar {
        public boolean passive;
        /** Unlocked primary slots (1..3). */
        public Set<Integer> primaries = new TreeSet<>();
        /** Selected primary slot, 0 = none. */
        public int selected;
        public long acquiredAt;

        public OwnedStar() {
        }

        public OwnedStar(long acquiredAt) {
            this.acquiredAt = acquiredAt;
        }

        void normalize() {
            if (primaries == null) {
                primaries = new TreeSet<>();
            } else if (!(primaries instanceof TreeSet)) {
                primaries = new TreeSet<>(primaries);
            }
            if (selected != 0 && !primaries.contains(selected)) {
                selected = primaries.isEmpty() ? 0 : primaries.iterator().next();
            }
        }

        public boolean unlocked(int slot) {
            return slot == 0 ? passive : primaries.contains(slot);
        }
    }

    public static final class TimedState {
        public long expiresAt;
        public Map<String, String> data = new HashMap<>();

        public TimedState() {
        }

        public TimedState(long expiresAt) {
            this.expiresAt = expiresAt;
        }

        public boolean expired(long now) {
            return expiresAt <= now;
        }

        public TimedState put(String key, Object value) {
            data.put(key, String.valueOf(value));
            return this;
        }

        public String get(String key) {
            return data == null ? null : data.get(key);
        }
    }

    public static final class Stats {
        public int kills;
        public int deaths;
        public int starsBought;
        public int abilitiesUnlocked;
        public int brightnessSpent;
        public int abilitiesCast;
    }
}
