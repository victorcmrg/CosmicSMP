package com.cosmicsmp.feature.ability.impl.elder;

import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Elder Primary #3: a 5x5 bubble for 10 seconds; anything trying to enter bounces off, projectiles reflect. */
public final class ForceField implements Ability {

    @Override
    public String id() {
        return "force_field";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ctx.plugin().effects().find(ctx.caster().getUniqueId(), Field.class).ifPresent(f -> f.cancel(EndReason.CANCELLED));
        ctx.plugin().effects().start(new Field(ctx));
        return CastResult.SUCCESS;
    }

    private static final class Field extends AbilityEffect {
        private final double radius;
        private final boolean follow;
        private final int points;
        private final ParticleSpec shell;
        private final ParticleSpec sparkle;
        private final Map<UUID, Integer> lastBounce = new HashMap<>();
        private Location anchor;

        Field(AbilityContext ctx) {
            super(ctx, ctx.def().integer("duration-seconds", 10) * 20);
            this.radius = ctx.def().num("radius", 2.5);
            this.follow = ctx.def().bool("follow-caster", true);
            this.points = ctx.def().integer("points", 64);
            this.shell = ctx.def().particle("shell", ParticleSpec.dust("#67e8f9", 0.9f));
            this.sparkle = ctx.def().particle("sparkle", ParticleSpec.of(Particle.END_ROD));
        }

        private Location center() {
            return follow ? caster.getLocation().add(0, 1, 0) : anchor;
        }

        @Override
        protected void onStart() {
            anchor = caster.getLocation().add(0, 1, 0);
            def.sound("start", SoundSpec.of("block.beacon.activate", 1.2f, 1.4f)).play(anchor);
        }

        @Override
        protected void onTick() {
            if (casterGone()) {
                cancel(EndReason.CANCELLED);
                return;
            }
            Location center = center();
            if (age % 3 == 0) {
                List<Player> viewers = viewers(center);
                Fx.sphere(shell, center, radius, points, age * 0.06, viewers);
                double[] sphere = Fx.sphere(16);
                int i = (age / 3) % 16;
                sparkle.spawn(center.getX() + sphere[i * 3] * radius, center.getY() + sphere[i * 3 + 1] * radius,
                        center.getZ() + sphere[i * 3 + 2] * radius, viewers);
            }
            if (age % 40 == 0) {
                def.sound("loop", SoundSpec.of("block.beacon.ambient", 0.9f, 1.6f)).play(center);
            }
            if (age % 2 != 0) {
                return;
            }
            double push = def.num("push", 0.9);
            double lift = def.num("lift", 0.35);
            boolean reflect = def.bool("reflect-projectiles", true);
            double reach = radius + 1.5;
            for (Entity entity : center.getWorld().getNearbyEntities(center, reach, reach, reach)) {
                if (entity.equals(caster)) {
                    continue;
                }
                Vector offset = entity.getLocation().add(0, entity.getHeight() / 2, 0).toVector().subtract(center.toVector());
                double distance = offset.length();
                if (distance > radius + 0.4) {
                    continue;
                }
                Vector out = distance < 1.0E-3 ? new Vector(0, 1, 0) : offset.clone().normalize();
                if (entity instanceof Projectile projectile) {
                    if (reflect && !caster.equals(projectile.getShooter())) {
                        Vector velocity = projectile.getVelocity();
                        if (velocity.dot(out) < 0) {
                            projectile.setVelocity(velocity.multiply(-0.8));
                            ParticleSpec.of(Particle.FLASH).spawn(projectile.getLocation(), viewers(center));
                            SoundSpec.of("item.shield.block", 1.0f, 1.5f).play(projectile.getLocation());
                        }
                    }
                    continue;
                }
                if (!(entity instanceof LivingEntity) || !ctx.plugin().combat().canTarget(caster, entity)) {
                    continue;
                }
                entity.setVelocity(out.multiply(push).setY(lift));
                Integer last = lastBounce.get(entity.getUniqueId());
                if (last == null || age - last > 10) {
                    lastBounce.put(entity.getUniqueId(), age);
                    Location contact = center.clone().add(offset.clone().normalize().multiply(radius));
                    ParticleSpec.of(Particle.END_ROD, 8, 0.2, 0.05).spawn(contact, viewers(contact));
                    def.sound("bounce", SoundSpec.of("block.amethyst_block.hit", 1.2f, 1.4f)).play(contact);
                }
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            Location center = caster.isOnline() ? center() : anchor;
            def.sound("end", SoundSpec.of("block.beacon.deactivate", 1.0f, 1.4f)).play(center);
        }
    }
}
