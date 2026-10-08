package com.cosmicsmp.feature.ability.impl.elder;

import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Elder Primary #2: an expanding tidal ring that launches everyone it touches into the air (6 hearts). */
public final class TidalWave implements Ability {

    @Override
    public String id() {
        return "tidal_wave";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ctx.plugin().effects().start(new Wave(ctx));
        return CastResult.SUCCESS;
    }

    private static final class Wave extends AbilityEffect {
        private final double radius;
        private final double speed;
        private final Set<UUID> hit = new HashSet<>();
        private final ParticleSpec water;
        private final ParticleSpec crest;
        private final ParticleSpec foam;
        private Location center;

        Wave(AbilityContext ctx) {
            super(ctx, (int) Math.ceil(ctx.def().num("radius", 9) / ctx.def().num("speed", 0.6)) + 2);
            this.radius = ctx.def().num("radius", 9);
            this.speed = ctx.def().num("speed", 0.6);
            this.water = ctx.def().particle("water", ParticleSpec.dust("#0ea5e9", 1.6f));
            this.crest = ctx.def().particle("crest", ParticleSpec.dust("#a5f3fc", 1.1f));
            this.foam = ctx.def().particle("foam", ParticleSpec.of(Particle.SPLASH, 2, 0.15, 0.1));
        }

        @Override
        protected void onStart() {
            center = caster.getLocation();
            def.sound("cast", SoundSpec.of("item.trident.riptide_3", 1.2f, 0.8f)).play(center);
            SoundSpec.of("entity.player.splash.high_speed", 1.0f, 0.9f).play(center);
        }

        @Override
        protected void onTick() {
            double r = Math.min(radius, age * speed);
            int points = (int) Math.max(12, Math.min(72, r * 8));
            double[] ring = Fx.ring(points);
            List<Player> viewers = Fx.viewers(center, Fx.viewDistance() + radius);
            for (int i = 0; i < points; i++) {
                double x = center.getX() + ring[i * 2] * r;
                double z = center.getZ() + ring[i * 2 + 1] * r;
                water.spawn(x, center.getY() + 0.2, z, viewers);
                if (i % 2 == 0) {
                    crest.spawn(x, center.getY() + 0.9, z, viewers);
                }
                if (i % 3 == 0) {
                    foam.spawn(x, center.getY() + 0.4, z, viewers);
                }
            }
            double height = def.num("height", 3);
            for (Entity entity : center.getWorld().getNearbyEntities(center, r + 1.2, height, r + 1.2)) {
                if (hit.contains(entity.getUniqueId()) || !ctx.plugin().combat().canTarget(caster, entity)) {
                    continue;
                }
                Vector flat = entity.getLocation().toVector().subtract(center.toVector()).setY(0);
                double d = flat.length();
                if (Math.abs(d - r) > 1.1) {
                    continue;
                }
                hit.add(entity.getUniqueId());
                LivingEntity living = (LivingEntity) entity;
                ctx.plugin().combat().damage(caster, living, def.num("damage", 12));
                Vector push = d < 1.0E-3 ? new Vector() : flat.normalize().multiply(def.num("outward", 0.5));
                living.setVelocity(push.setY(def.num("launch", 1.25)));
                ParticleSpec.of(Particle.SPLASH, 25, 0.4, 0.2).spawn(living.getLocation().add(0, 0.5, 0), viewers);
                SoundSpec.of("entity.generic.splash", 1.0f, 1.2f).play(living.getLocation());
            }
        }
    }
}
