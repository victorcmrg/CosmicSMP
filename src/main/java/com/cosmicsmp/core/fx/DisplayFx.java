package com.cosmicsmp.core.fx;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.task.TimedEffect;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.UUID;
import java.util.function.Consumer;

/**
 * Client-side animation toolkit built on display entities.
 * <p>
 * The server sends a start state and an end state; the client interpolates every frame in between. This gives
 * smooth 60+ fps animations (scaling, spinning, sliding, floating text) for one or two metadata packets instead of
 * one packet per tick, which is the cheapest possible way to animate on both CPU and network.
 */
public final class DisplayFx {

    private DisplayFx() {
    }

    public static Transformation transform(float tx, float ty, float tz, float scale) {
        return new Transformation(new Vector3f(tx, ty, tz), new Quaternionf(), new Vector3f(scale, scale, scale), new Quaternionf());
    }

    public static Transformation transform(Vector3f translation, float yawRadians, Vector3f scale) {
        return new Transformation(translation, new Quaternionf(new AxisAngle4f(yawRadians, 0, 1, 0)), scale, new Quaternionf());
    }

    public static Transformation transform(Vector3f translation, Quaternionf rotation, Vector3f scale) {
        return new Transformation(translation, rotation, scale, new Quaternionf());
    }

    /**
     * Transformation for a block display rotated around its own vertical centre line (block displays otherwise pivot
     * on their corner). {@code position} is where the bottom-centre of the scaled block ends up, relative to the entity.
     */
    public static Transformation centeredBlock(Vector3f position, float yawRadians, Vector3f scale) {
        Quaternionf rotation = new Quaternionf().rotateY(yawRadians);
        Vector3f pivot = new Vector3f(scale.x * 0.5f, 0, scale.z * 0.5f);
        rotation.transform(pivot);
        return new Transformation(new Vector3f(position).sub(pivot), rotation, scale, new Quaternionf());
    }

    public static ItemDisplay item(CosmicSMP plugin, Location location, ItemStack item, Transformation start, Consumer<ItemDisplay> extra) {
        return plugin.tempEntities().spawn(flat(location), ItemDisplay.class, d -> {
            d.setItemStack(item);
            base(d, start);
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    public static BlockDisplay block(CosmicSMP plugin, Location location, BlockData block, Transformation start, Consumer<BlockDisplay> extra) {
        return plugin.tempEntities().spawn(flat(location), BlockDisplay.class, d -> {
            d.setBlock(block);
            base(d, start);
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    public static TextDisplay text(CosmicSMP plugin, Location location, Component text, Consumer<TextDisplay> extra) {
        return plugin.tempEntities().spawn(flat(location), TextDisplay.class, d -> {
            d.text(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
            d.setShadowed(true);
            d.setSeeThrough(false);
            base(d, transform(0, 0, 0, 1));
            if (extra != null) {
                extra.accept(d);
            }
        });
    }

    /** Display rotation comes from the transformation only - never from the spawn location's yaw/pitch. */
    private static Location flat(Location location) {
        Location copy = location.clone();
        copy.setYaw(0);
        copy.setPitch(0);
        return copy;
    }

    private static void base(Display display, Transformation start) {
        display.setTransformation(start);
        display.setViewRange(1.0f);
        display.setShadowRadius(0);
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(0);
        display.setTeleportDuration(0);
    }

    /** Makes a display visible only to one player (pure client-side feedback). */
    public static void onlyFor(CosmicSMP plugin, Display display, Player viewer) {
        display.setVisibleByDefault(false);
        viewer.showEntity(plugin, display);
    }

    /**
     * Interpolates to a new transformation. Must be called at least one tick after spawning, otherwise the client
     * receives both states at once and snaps.
     */
    public static void animate(Display display, int durationTicks, Transformation to) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setInterpolationDelay(0);
        display.setInterpolationDuration(durationTicks);
        display.setTransformation(to);
    }

    public static void animateLater(CosmicSMP plugin, Display display, int delayTicks, int durationTicks, Transformation to) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> animate(display, durationTicks, to), Math.max(1, delayTicks));
    }

    /** Smoothly moves a display (client interpolates the teleport). */
    public static void glide(Display display, Location to, int durationTicks) {
        if (display == null || !display.isValid()) {
            return;
        }
        display.setTeleportDuration(Math.max(0, Math.min(59, durationTicks)));
        display.teleport(flat(to));
    }

    /**
     * Floating text that rises and fades (scales down). {@code viewer == null} shows it to everyone.
     */
    public static void popup(CosmicSMP plugin, Player viewer, Location location, Component text, int lifeTicks) {
        plugin.effects().start(new Popup(plugin, viewer, location, text, lifeTicks));
    }

    private static final class Popup extends TimedEffect {

        private final Player viewer;
        private final Location location;
        private final Component text;
        private final int life;
        private TextDisplay display;

        Popup(CosmicSMP plugin, Player viewer, Location location, Component text, int life) {
            super(Math.max(12, life));
            this.life = Math.max(12, life);
            this.viewer = viewer;
            this.location = location;
            this.text = text;
        }

        @Override
        public UUID owner() {
            return null;
        }

        @Override
        protected void onStart() {
            display = text(plugin, location, text, d -> {
                d.setTransformation(transform(0, 0, 0, 0.2f));
                d.setBrightness(new Display.Brightness(15, 15));
                if (viewer != null) {
                    onlyFor(plugin, d, viewer);
                }
            });
        }

        @Override
        protected void onTick() {
            if (age == 1) {
                animate(display, 6, transform(0, 0.35f, 0, 1.25f));
            } else if (age == 8) {
                animate(display, life - 8, transform(0, 1.2f, 0, 0.6f));
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            plugin.tempEntities().remove(display);
        }
    }
}
