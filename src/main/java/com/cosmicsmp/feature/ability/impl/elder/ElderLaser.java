package com.cosmicsmp.feature.ability.impl.elder;

import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** Elder Primary #1: a short guardian charge, then a laser that hits the first enemy in line (3 hearts). */
public final class ElderLaser implements Ability {

    @Override
    public String id() {
        return "elder_laser";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ctx.plugin().effects().start(new Laser(ctx));
        return CastResult.SUCCESS;
    }

    private static final class Laser extends AbilityEffect {
        private final int charge;
        private final ParticleSpec beam;
        private final ParticleSpec spark;
        private Location from;
        private Location to;

        Laser(AbilityContext ctx) {
            super(ctx, ctx.def().integer("charge-ticks", 8) + 5);
            this.charge = ctx.def().integer("charge-ticks", 8);
            this.beam = ctx.def().particle("beam", ParticleSpec.transition("#5eead4", "#fde047", 1.3f));
            this.spark = ctx.def().particle("spark", ParticleSpec.of(Particle.ELECTRIC_SPARK));
        }

        @Override
        protected void onStart() {
            def.sound("charge", SoundSpec.of("entity.guardian.attack", 1.2f, 1.6f)).play(caster.getLocation());
        }

        @Override
        protected void onTick() {
            if (casterGone()) {
                finish();
                return;
            }
            if (age <= charge) {
                Location hand = caster.getEyeLocation().add(caster.getEyeLocation().getDirection().multiply(0.8)).subtract(0, 0.3, 0);
                double radius = 0.9 * (1 - age / (double) charge) + 0.1;
                double angle = age * 0.8;
                List<Player> viewers = viewers(hand);
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * (Math.PI * 2 / 3);
                    beam.spawn(hand.getX() + Math.cos(a) * radius, hand.getY() + Math.sin(a) * radius * 0.6, hand.getZ() + Math.sin(a) * radius, viewers);
                }
                return;
            }
            if (age == charge + 1) {
                fire();
            }
            if (from != null && age <= charge + 3) {
                Fx.line(beam, from, to, 0.35, Fx.viewers(from, Fx.viewDistance() + from.distance(to) / 2));
            }
        }

        private void fire() {
            Location eye = caster.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            double range = def.num("range", 26);
            RayTraceResult result = caster.getWorld().rayTrace(eye, dir, range, FluidCollisionMode.NEVER, true,
                    def.num("ray-size", 0.45), e -> ctx.plugin().combat().canTarget(caster, e));
            from = eye.clone().add(dir.clone().multiply(0.6)).subtract(0, 0.2, 0);
            to = result == null ? eye.clone().add(dir.clone().multiply(range)) : result.getHitPosition().toLocation(eye.getWorld());
            List<Player> viewers = Fx.viewers(from, Fx.viewDistance() + range / 2);
            Fx.line(beam, from, to, 0.25, viewers);
            Fx.line(spark, from, to, 1.0, viewers);
            def.sound("fire", SoundSpec.of("block.beacon.power_select", 1.2f, 1.9f)).play(caster.getLocation());
            if (result != null && result.getHitEntity() instanceof LivingEntity target) {
                ctx.plugin().combat().damage(caster, target, def.num("damage", 6));
                target.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, def.integer("glow-seconds", 2) * 20, 0, false, false, false));
                ParticleSpec.of(Particle.FLASH).spawn(to, viewers);
                ParticleSpec.of(Particle.END_ROD, 14, 0.2, 0.15).spawn(to, viewers);
                def.sound("hit", SoundSpec.of("entity.elder_guardian.hurt", 1.0f, 1.6f)).play(to);
            } else {
                ParticleSpec.of(Particle.SMOKE, 6, 0.1, 0.02).spawn(to, viewers);
            }
        }
    }
}
