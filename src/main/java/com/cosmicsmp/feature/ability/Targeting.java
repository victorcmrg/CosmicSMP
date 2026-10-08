package com.cosmicsmp.feature.ability;

import com.cosmicsmp.CosmicSMP;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

/** Aim helpers shared by abilities. Uses the server's ray tracer (no per-block Java loops). */
public final class Targeting {

    private Targeting() {
    }

    /** First valid living entity in the caster's crosshair (blocks stop the ray). */
    public static LivingEntity entityInSight(CosmicSMP plugin, Player caster, double range, double raySize, boolean playersOnly) {
        Location eye = caster.getEyeLocation();
        RayTraceResult result = caster.getWorld().rayTrace(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true, raySize,
                e -> plugin.combat().canTarget(caster, e) && (!playersOnly || e instanceof Player));
        if (result != null && result.getHitEntity() instanceof LivingEntity living) {
            return living;
        }
        return null;
    }

    /** Point the caster is looking at (block hit, or max range). */
    public static Location pointInSight(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector dir = eye.getDirection();
        RayTraceResult result = caster.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        if (result != null) {
            Vector hit = result.getHitPosition();
            Vector back = dir.clone().multiply(0.3);
            return new Location(caster.getWorld(), hit.getX() - back.getX(), hit.getY() - back.getY(), hit.getZ() - back.getZ());
        }
        return eye.add(dir.multiply(range));
    }

    /** Free distance along a direction before hitting a block (minus a small margin). */
    public static double freeDistance(Location from, Vector dir, double max) {
        RayTraceResult result = from.getWorld().rayTraceBlocks(from, dir, max, FluidCollisionMode.NEVER, true);
        if (result == null) {
            return max;
        }
        return Math.max(0, result.getHitPosition().distance(from.toVector()) - 0.6);
    }

    /** Drops a location onto the ground below it (searching down {@code depth} blocks). */
    public static Location ground(Location location, int depth) {
        World world = location.getWorld();
        Location loc = location.clone();
        int startY = loc.getBlockY();
        for (int y = startY; y > startY - depth && y > world.getMinHeight(); y--) {
            Block block = world.getBlockAt(loc.getBlockX(), y - 1, loc.getBlockZ());
            if (block.isSolid()) {
                loc.setY(y);
                return loc;
            }
        }
        return loc;
    }

    public static Block groundBlock(Location location) {
        Location ground = ground(location, 6);
        return ground.getWorld().getBlockAt(ground.getBlockX(), ground.getBlockY() - 1, ground.getBlockZ());
    }

    public static List<LivingEntity> nearby(CosmicSMP plugin, Player caster, Location center, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        double r2 = radius * radius;
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (plugin.combat().canTarget(caster, entity) && entity.getLocation().distanceSquared(center) <= r2) {
                out.add((LivingEntity) entity);
            }
        }
        return out;
    }

    public static boolean isPassable(Location location) {
        Block block = location.getBlock();
        return block.isPassable();
    }
}
