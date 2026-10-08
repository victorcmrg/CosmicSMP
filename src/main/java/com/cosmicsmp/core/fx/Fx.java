package com.cosmicsmp.core.fx;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Geometry + viewer helpers for particle effects.
 * <p>
 * Performance notes: viewer lists are computed once per frame and reused for every point of a shape, trigonometric
 * tables for rings and Fibonacci spheres are cached, and nothing is sent to players outside the view distance.
 */
public final class Fx {

    private static final Map<Integer, double[]> RINGS = new ConcurrentHashMap<>();
    private static final Map<Integer, double[]> SPHERES = new ConcurrentHashMap<>();
    private static int viewDistance = 48;

    private Fx() {
    }

    public static void viewDistance(int distance) {
        viewDistance = Math.max(8, distance);
    }

    public static int viewDistance() {
        return viewDistance;
    }

    public static List<Player> viewers(Location location) {
        return viewers(location, viewDistance);
    }

    public static List<Player> viewers(Location location, double radius) {
        World world = location.getWorld();
        if (world == null) {
            return Collections.emptyList();
        }
        List<Player> players = world.getPlayers();
        if (players.isEmpty()) {
            return Collections.emptyList();
        }
        double r2 = radius * radius;
        double x = location.getX();
        double y = location.getY();
        double z = location.getZ();
        List<Player> out = new ArrayList<>(Math.min(players.size(), 8));
        Location scratch = new Location(world, 0, 0, 0);
        for (Player player : players) {
            player.getLocation(scratch);
            double dx = scratch.getX() - x;
            double dy = scratch.getY() - y;
            double dz = scratch.getZ() - z;
            if (dx * dx + dy * dy + dz * dz <= r2) {
                out.add(player);
            }
        }
        return out;
    }

    /** cos/sin pairs for {@code points} evenly spaced angles. */
    public static double[] ring(int points) {
        return RINGS.computeIfAbsent(points, p -> {
            double[] table = new double[p * 2];
            for (int i = 0; i < p; i++) {
                double angle = Math.PI * 2 * i / p;
                table[i * 2] = Math.cos(angle);
                table[i * 2 + 1] = Math.sin(angle);
            }
            return table;
        });
    }

    /** Unit vectors (x,y,z triplets) evenly distributed on a sphere (Fibonacci lattice). */
    public static double[] sphere(int points) {
        return SPHERES.computeIfAbsent(points, p -> {
            double[] table = new double[p * 3];
            double golden = Math.PI * (3 - Math.sqrt(5));
            for (int i = 0; i < p; i++) {
                double y = 1 - (i / (double) (p - 1)) * 2;
                double radius = Math.sqrt(1 - y * y);
                double theta = golden * i;
                table[i * 3] = Math.cos(theta) * radius;
                table[i * 3 + 1] = y;
                table[i * 3 + 2] = Math.sin(theta) * radius;
            }
            return table;
        });
    }

    public static void line(ParticleSpec spec, Location from, Location to, double step, List<Player> viewers) {
        if (spec == null || viewers.isEmpty()) {
            return;
        }
        double dx = to.getX() - from.getX();
        double dy = to.getY() - from.getY();
        double dz = to.getZ() - from.getZ();
        double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int points = Math.max(1, (int) (length / step));
        for (int i = 0; i <= points; i++) {
            double t = i / (double) points;
            spec.spawn(from.getX() + dx * t, from.getY() + dy * t, from.getZ() + dz * t, viewers);
        }
    }

    public static void circle(ParticleSpec spec, Location center, double radius, int points, List<Player> viewers) {
        if (spec == null || viewers.isEmpty()) {
            return;
        }
        double[] table = ring(points);
        for (int i = 0; i < points; i++) {
            spec.spawn(center.getX() + table[i * 2] * radius, center.getY(), center.getZ() + table[i * 2 + 1] * radius, viewers);
        }
    }

    public static void sphere(ParticleSpec spec, Location center, double radius, int points, double rotation, List<Player> viewers) {
        if (spec == null || viewers.isEmpty()) {
            return;
        }
        double[] table = sphere(points);
        double cos = Math.cos(rotation);
        double sin = Math.sin(rotation);
        for (int i = 0; i < points; i++) {
            double x = table[i * 3];
            double z = table[i * 3 + 2];
            double rx = x * cos - z * sin;
            double rz = x * sin + z * cos;
            spec.spawn(center.getX() + rx * radius, center.getY() + table[i * 3 + 1] * radius, center.getZ() + rz * radius, viewers);
        }
    }

    /** Helix around the segment from -> dir*length, used for beams. */
    public static void helix(ParticleSpec spec, Location from, Vector dir, double length, double radius, double step, double phase, List<Player> viewers) {
        if (spec == null || viewers.isEmpty()) {
            return;
        }
        Vector d = dir.clone().normalize();
        Vector a = Math.abs(d.getY()) > 0.95 ? new Vector(1, 0, 0) : new Vector(0, 1, 0);
        Vector u = d.clone().crossProduct(a).normalize();
        Vector v = d.clone().crossProduct(u).normalize();
        for (double t = 0; t <= length; t += step) {
            double angle = phase + t * 1.6;
            double c = Math.cos(angle) * radius;
            double s = Math.sin(angle) * radius;
            spec.spawn(from.getX() + d.getX() * t + u.getX() * c + v.getX() * s,
                    from.getY() + d.getY() * t + u.getY() * c + v.getY() * s,
                    from.getZ() + d.getZ() * t + u.getZ() * c + v.getZ() * s, viewers);
        }
    }

    /** Horizontal unit vector of a direction; falls back to +Z when looking straight up/down (never NaN). */
    public static Vector horizontal(Vector direction) {
        Vector flat = new Vector(direction.getX(), 0, direction.getZ());
        return flat.lengthSquared() < 1.0E-4 ? new Vector(0, 0, 1) : flat.normalize();
    }

    /** Horizontal unit vector perpendicular to the given direction. */
    public static Vector side(Vector direction) {
        Vector flat = new Vector(direction.getX(), 0, direction.getZ());
        if (flat.lengthSquared() < 1.0E-4) {
            flat = new Vector(1, 0, 0);
        }
        flat.normalize();
        return new Vector(-flat.getZ(), 0, flat.getX());
    }
}
