package com.cosmicsmp.core.fx;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.Map;

/**
 * A configurable sound. Keys are plain resource-pack keys, so vanilla sounds ({@code entity.warden.sonic_boom}),
 * ItemsAdder sounds ({@code mynamespace:custom.roar}) and any other pack sound work the same way.
 * <p>
 * Accepted config formats:
 * <pre>
 * sound: "entity.warden.sonic_boom"
 * sound: "entity.warden.sonic_boom 1.0 1.4"        # key volume pitch
 * sound: {sound: "itemsadder:cosmic.roar", volume: 1.0, pitch: 1.0, category: hostile}
 * sound: ""                                        # disabled
 * </pre>
 */
public final class SoundSpec {

    public static final SoundSpec NONE = new SoundSpec(null);

    private final Sound sound;

    private SoundSpec(Sound sound) {
        this.sound = sound;
    }

    public static SoundSpec of(String key, float volume, float pitch) {
        return of(key, volume, pitch, Sound.Source.MASTER);
    }

    public static SoundSpec of(String key, float volume, float pitch, Sound.Source source) {
        try {
            return new SoundSpec(Sound.sound(Key.key(key.toLowerCase(Locale.ROOT)), source, volume, pitch));
        } catch (InvalidKeyException ex) {
            return NONE;
        }
    }

    public static SoundSpec parse(Object raw, SoundSpec fallback) {
        if (raw == null) {
            return fallback;
        }
        if (raw instanceof ConfigurationSection section) {
            return fromValues(section.getString("sound", section.getString("key", "")),
                    section.getDouble("volume", 1.0), section.getDouble("pitch", 1.0),
                    section.getString("category", "master"), fallback);
        }
        if (raw instanceof Map<?, ?> map) {
            Object key = map.containsKey("sound") ? map.get("sound") : map.get("key");
            return fromValues(String.valueOf(key), number(map.get("volume"), 1.0), number(map.get("pitch"), 1.0),
                    String.valueOf(map.containsKey("category") ? map.get("category") : "master"), fallback);
        }
        String text = String.valueOf(raw).trim();
        if (text.isEmpty() || text.equalsIgnoreCase("none")) {
            return NONE;
        }
        String[] parts = text.split("\\s+");
        double volume = parts.length > 1 ? number(parts[1], 1.0) : 1.0;
        double pitch = parts.length > 2 ? number(parts[2], 1.0) : 1.0;
        return fromValues(parts[0], volume, pitch, parts.length > 3 ? parts[3] : "master", fallback);
    }

    private static SoundSpec fromValues(String key, double volume, double pitch, String category, SoundSpec fallback) {
        if (key == null || key.isBlank() || key.equals("null")) {
            return NONE;
        }
        Sound.Source source;
        try {
            source = Sound.Source.valueOf(category.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            source = Sound.Source.MASTER;
        }
        SoundSpec spec = of(key, (float) volume, (float) pitch, source);
        return spec == NONE ? fallback : spec;
    }

    private static double number(Object value, double def) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return value == null ? def : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    public boolean isEmpty() {
        return sound == null;
    }

    /** Plays only for this player (follows them). */
    public void play(Player player) {
        if (sound != null && player != null) {
            player.playSound(sound, Sound.Emitter.self());
        }
    }

    /** Plays in the world at a location (heard by everyone in range, range scales with volume). */
    public void play(Location location) {
        if (sound != null && location != null && location.getWorld() != null) {
            location.getWorld().playSound(sound, location.getX(), location.getY(), location.getZ());
        }
    }

    public SoundSpec withPitch(float pitch) {
        return sound == null ? this : new SoundSpec(Sound.sound(sound.name(), sound.source(), sound.volume(), pitch));
    }
}
