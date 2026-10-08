package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.fx.DisplayFx;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.task.TimedEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.UUID;

/** Visual moments of the star economy (purchase, unlock, evaporation). All client-interpolated. */
public final class StarAnimations {

    private StarAnimations() {
    }

    public static void acquired(CosmicSMP plugin, Player player, StarDefinition star, ItemStack item) {
        plugin.effects().start(new Acquire(player, star, item));
    }

    public static void unlocked(CosmicSMP plugin, Player player, StarDefinition star) {
        Location at = player.getLocation().add(0, 1, 0);
        List<Player> viewers = Fx.viewers(at);
        ParticleSpec.dust(star.color(), 1.2f, 2, 0.05).spawn(at, viewers);
        Fx.circle(ParticleSpec.dust(star.color(), 1.0f), at.clone().subtract(0, 0.9, 0), 0.9, 24, viewers);
        ParticleSpec.of(Particle.END_ROD, 12, 0.4, 0.08).spawn(at, viewers);
        SoundSpec.of("block.amethyst_block.resonate", 1f, 1.4f).play(player);
        SoundSpec.of("entity.player.levelup", 0.6f, 1.8f).play(player);
    }

    public static void evaporated(CosmicSMP plugin, Location where) {
        Location at = where.clone().add(0, 1, 0);
        List<Player> viewers = Fx.viewers(at);
        ParticleSpec.of(Particle.END_ROD, 40, 0.2, 0.25).spawn(at, viewers);
        ParticleSpec.of(Particle.REVERSE_PORTAL, 60, 0.4, 0.4).spawn(at, viewers);
        ParticleSpec.of(Particle.FLASH).spawn(at, viewers);
        SoundSpec.of("block.respawn_anchor.deplete", 1f, 1.6f).play(at);
        SoundSpec.of("entity.allay.death", 0.8f, 0.8f).play(at);
    }

    /** A star materialises in front of the player, spins and flies into their chest. */
    private static final class Acquire extends TimedEffect {
        private final Player player;
        private final StarDefinition star;
        private final ItemStack item;
        private ItemDisplay display;
        private ParticleSpec dust;

        Acquire(Player player, StarDefinition star, ItemStack item) {
            super(46);
            this.player = player;
            this.star = star;
            this.item = item;
        }

        @Override
        public UUID owner() {
            return player.getUniqueId();
        }

        private Location front() {
            Vector dir = player.getLocation().getDirection().setY(0);
            if (dir.lengthSquared() < 1.0E-4) {
                dir = new Vector(0, 0, 1);
            }
            return player.getEyeLocation().add(dir.normalize().multiply(1.8)).subtract(0, 0.2, 0);
        }

        @Override
        protected void onStart() {
            dust = ParticleSpec.dust(star.color(), 1.1f);
            display = DisplayFx.item(plugin, front(), item, DisplayFx.transform(0, 0, 0, 0.01f), d -> {
                d.setBillboard(Display.Billboard.FIXED);
                d.setBrightness(new Display.Brightness(15, 15));
                d.setGlowing(true);
            });
            SoundSpec.of("block.beacon.activate", 0.9f, 1.8f).play(player.getLocation());
        }

        @Override
        protected void onTick() {
            if (!player.isOnline() || display == null || !display.isValid()) {
                finish();
                return;
            }
            Location at = display.getLocation();
            List<Player> viewers = Fx.viewers(at);
            switch (age) {
                case 1 -> DisplayFx.animate(display, 12, DisplayFx.transform(new Vector3f(0, 0.25f, 0),
                        new Quaternionf(new AxisAngle4f((float) Math.PI, 0, 1, 0)), new Vector3f(0.9f)));
                case 14 -> DisplayFx.animate(display, 14, DisplayFx.transform(new Vector3f(0, 0.35f, 0),
                        new Quaternionf(new AxisAngle4f((float) (Math.PI * 2), 0, 1, 0)), new Vector3f(1.05f)));
                case 30 -> {
                    Location chest = player.getLocation().add(0, 1.1, 0);
                    Vector delta = chest.toVector().subtract(at.toVector());
                    DisplayFx.animate(display, 10, DisplayFx.transform(delta.toVector3f(),
                            new Quaternionf(new AxisAngle4f((float) (Math.PI * 3), 0, 1, 0)), new Vector3f(0.05f)));
                    SoundSpec.of("entity.illusioner.cast_spell", 0.8f, 1.6f).play(player.getLocation());
                }
                case 40 -> {
                    Location chest = player.getLocation().add(0, 1.1, 0);
                    ParticleSpec.of(Particle.END_ROD, 25, 0.1, 0.2).spawn(chest, viewers);
                    ParticleSpec.of(Particle.FLASH).spawn(chest, viewers);
                    SoundSpec.of("ui.toast.challenge_complete", 0.7f, 1.3f).play(player);
                }
                default -> {
                }
            }
            if (age < 30 && age % 2 == 0) {
                double angle = age * 0.4;
                for (int i = 0; i < 2; i++) {
                    double a = angle + i * Math.PI;
                    dust.spawn(at.getX() + Math.cos(a) * 0.7, at.getY() + 0.3 + (age / 60.0), at.getZ() + Math.sin(a) * 0.7, viewers);
                }
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            plugin.tempEntities().remove(display);
        }
    }
}
