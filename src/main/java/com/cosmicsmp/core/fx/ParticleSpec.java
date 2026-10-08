package com.cosmicsmp.core.fx;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A configurable particle with its extra data resolved once at load time.
 * <pre>
 * particle: END_ROD
 * particle: {type: DUST, color: "#22d3ee", size: 1.4, count: 3, offset: [0.2, 0.2, 0.2], speed: 0}
 * particle: {type: DUST_COLOR_TRANSITION, color: "#22d3ee", to-color: "#a855f7", size: 1.2}
 * particle: {type: BLOCK, block: SCULK}
 * </pre>
 * Particles are sent only to the given viewers (players within the configured view distance), never broadcast.
 */
public final class ParticleSpec {

    private final Particle type;
    private final int count;
    private final double offsetX;
    private final double offsetY;
    private final double offsetZ;
    private final double speed;
    private final Object data;

    public ParticleSpec(Particle type, int count, double offsetX, double offsetY, double offsetZ, double speed, Object data) {
        this.type = type;
        this.count = count;
        this.offsetX = offsetX;
        this.offsetY = offsetY;
        this.offsetZ = offsetZ;
        this.speed = speed;
        this.data = data;
    }

    public static ParticleSpec of(Particle type) {
        return new ParticleSpec(type, 1, 0, 0, 0, 0, null);
    }

    public static ParticleSpec of(Particle type, int count, double offset, double speed) {
        return new ParticleSpec(type, count, offset, offset, offset, speed, null);
    }

    public static ParticleSpec dust(String hex, float size) {
        return new ParticleSpec(Particle.DUST, 1, 0, 0, 0, 0, new Particle.DustOptions(color(hex, Color.WHITE), size));
    }

    public static ParticleSpec dust(String hex, float size, int count, double offset) {
        return new ParticleSpec(Particle.DUST, count, offset, offset, offset, 0, new Particle.DustOptions(color(hex, Color.WHITE), size));
    }

    public static ParticleSpec transition(String from, String to, float size) {
        return new ParticleSpec(Particle.DUST_COLOR_TRANSITION, 1, 0, 0, 0, 0,
                new Particle.DustTransition(color(from, Color.WHITE), color(to, Color.WHITE), size));
    }

    public static ParticleSpec block(Particle type, Material material, int count, double offset) {
        return new ParticleSpec(type, count, offset, offset, offset, 0, material.createBlockData());
    }

    public static ParticleSpec parse(Object raw, ParticleSpec fallback) {
        if (raw == null) {
            return fallback;
        }
        ConfigurationSection section;
        if (raw instanceof ConfigurationSection s) {
            section = s;
        } else if (raw instanceof Map<?, ?> map) {
            MemoryConfiguration memory = new MemoryConfiguration();
            map.forEach((k, v) -> memory.set(String.valueOf(k), v));
            section = memory;
        } else {
            String name = String.valueOf(raw).trim();
            if (name.isEmpty() || name.equalsIgnoreCase("none")) {
                return null;
            }
            Particle type = particle(name);
            if (type == null) {
                return fallback;
            }
            Object data = defaultData(type, null);
            return data == INVALID ? fallback : new ParticleSpec(type, 1, 0, 0, 0, 0, data);
        }

        Particle type = particle(section.getString("type", ""));
        if (type == null) {
            return fallback;
        }
        Object data = defaultData(type, section);
        if (data == INVALID) {
            return fallback;
        }
        double ox = 0;
        double oy = 0;
        double oz = 0;
        List<?> offset = section.getList("offset");
        if (offset != null && offset.size() == 3) {
            ox = toDouble(offset.get(0));
            oy = toDouble(offset.get(1));
            oz = toDouble(offset.get(2));
        } else if (section.isSet("offset")) {
            ox = oy = oz = section.getDouble("offset");
        }
        return new ParticleSpec(type, Math.max(0, section.getInt("count", 1)), ox, oy, oz, section.getDouble("speed", 0), data);
    }

    private static final Object INVALID = new Object();

    private static Object defaultData(Particle type, ConfigurationSection s) {
        Class<?> dataType = type.getDataType();
        if (dataType == Void.class) {
            return null;
        }
        String colorText = s == null ? "#ffffff" : s.getString("color", "#ffffff");
        float size = s == null ? 1.0f : (float) s.getDouble("size", 1.0);
        if (dataType == Particle.DustOptions.class) {
            return new Particle.DustOptions(color(colorText, Color.WHITE), size);
        }
        if (dataType == Particle.DustTransition.class) {
            String to = s == null ? colorText : s.getString("to-color", colorText);
            return new Particle.DustTransition(color(colorText, Color.WHITE), color(to, Color.WHITE), size);
        }
        if (dataType == Color.class) {
            Color c = color(colorText, Color.WHITE);
            return Color.fromARGB(255, c.getRed(), c.getGreen(), c.getBlue());
        }
        if (dataType == Float.class) {
            return (float) (s == null ? 1.0 : s.getDouble("power", s.getDouble("value", 1.0)));
        }
        if (dataType == Integer.class) {
            return s == null ? 0 : s.getInt("delay", s.getInt("value", 0));
        }
        if (dataType == BlockData.class) {
            Material material = Material.matchMaterial(s == null ? "STONE" : s.getString("block", "STONE"));
            return (material == null || !material.isBlock() ? Material.STONE : material).createBlockData();
        }
        if (dataType == ItemStack.class) {
            Material material = Material.matchMaterial(s == null ? "STONE" : s.getString("item", "STONE"));
            return new ItemStack(material == null || !material.isItem() ? Material.STONE : material);
        }
        if (dataType == Particle.Spell.class) {
            return new Particle.Spell(color(colorText, Color.WHITE), s == null ? 1.0f : (float) s.getDouble("power", 1.0));
        }
        return INVALID;
    }

    private static Particle particle(String name) {
        try {
            return Particle.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public static Color color(String text, Color fallback) {
        if (text == null) {
            return fallback;
        }
        String value = text.trim();
        try {
            if (value.startsWith("#")) {
                return Color.fromRGB(Integer.parseInt(value.substring(1), 16));
            }
            String[] rgb = value.split("\\s*,\\s*");
            if (rgb.length == 3) {
                return Color.fromRGB(Integer.parseInt(rgb[0]), Integer.parseInt(rgb[1]), Integer.parseInt(rgb[2]));
            }
        } catch (IllegalArgumentException ex) {
            return fallback;
        }
        return fallback;
    }

    private static double toDouble(Object value) {
        if (value instanceof Number n) {
            return n.doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public ParticleSpec withCount(int newCount) {
        return new ParticleSpec(type, newCount, offsetX, offsetY, offsetZ, speed, data);
    }

    public ParticleSpec withOffset(double offset, double newSpeed) {
        return new ParticleSpec(type, count, offset, offset, offset, newSpeed, data);
    }

    public Particle type() {
        return type;
    }

    public void spawn(Location location, List<Player> viewers) {
        spawn(location.getX(), location.getY(), location.getZ(), viewers);
    }

    public void spawn(double x, double y, double z, List<Player> viewers) {
        for (int i = 0, size = viewers.size(); i < size; i++) {
            viewers.get(i).spawnParticle(type, x, y, z, count, offsetX, offsetY, offsetZ, speed, data);
        }
    }

    /** Spawns with a directional velocity (count 0 trick: offset = direction, speed = magnitude). */
    public void spawnDirectional(double x, double y, double z, double dx, double dy, double dz, double velocity, List<Player> viewers) {
        for (int i = 0, size = viewers.size(); i < size; i++) {
            viewers.get(i).spawnParticle(type, x, y, z, 0, dx, dy, dz, velocity, data);
        }
    }
}
