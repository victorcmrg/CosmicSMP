package com.cosmicsmp.feature.ability.impl.sonic;

import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.Targeting;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.List;

/** Sonic Primary #2: charged, piercing Warden sonic boom with a double sculk helix. */
public final class SonicBeam implements Ability {

    @Override
    public String id() {
        return "sonic_beam";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ctx.plugin().effects().start(new Beam(ctx));
        return CastResult.SUCCESS;
    }

    private static final class Beam extends AbilityEffect {
        private final int charge;
        private final ParticleSpec chargeParticle;
        private final ParticleSpec helixA;
        private final ParticleSpec helixB;
        private final ParticleSpec boom;

        Beam(AbilityContext ctx) {
            super(ctx, ctx.def().integer("charge-ticks", 12) + 4);
            this.charge = ctx.def().integer("charge-ticks", 12);
            this.chargeParticle = ctx.def().particle("charge", ParticleSpec.of(Particle.SCULK_SOUL));
            this.helixA = ctx.def().particle("helix", ParticleSpec.dust("#22d3ee", 1.1f));
            this.helixB = ctx.def().particle("helix-secondary", ParticleSpec.of(Particle.ELECTRIC_SPARK));
            this.boom = ctx.def().particle("boom", ParticleSpec.of(Particle.SONIC_BOOM));
        }

        @Override
        protected void onStart() {
            def.sound("charge", SoundSpec.of("entity.warden.sonic_charge", 1.6f, 1.1f)).play(caster.getLocation());
            if (def.bool("slow-caster", true)) {
                caster.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, charge, 2, false, false, false));
            }
        }

        @Override
        protected void onTick() {
            if (casterGone()) {
                finish();
                return;
            }
            if (age <= charge) {
                Location chest = caster.getLocation().add(0, 1.2, 0);
                double radius = 2.0 * (1.0 - age / (double) charge) + 0.2;
                double[] ring = Fx.ring(8);
                List<Player> viewers = viewers(chest);
                double phase = age * 0.5;
                for (int i = 0; i < 8; i++) {
                    double c = ring[i * 2] * Math.cos(phase) - ring[i * 2 + 1] * Math.sin(phase);
                    double s = ring[i * 2] * Math.sin(phase) + ring[i * 2 + 1] * Math.cos(phase);
                    chargeParticle.spawn(chest.getX() + c * radius, chest.getY() + (i % 2 == 0 ? 0.4 : -0.4) * (radius / 2), chest.getZ() + s * radius, viewers);
                }
                return;
            }
            if (age == charge + 1) {
                fire();
            }
        }

        private void fire() {
            Location eye = caster.getEyeLocation();
            Vector dir = eye.getDirection().normalize();
            double range = def.num("range", 24);
            double length = def.bool("pierce-blocks", true) ? range : Targeting.freeDistance(eye, dir, range);
            Location end = eye.clone().add(dir.clone().multiply(length));
            Location mid = eye.clone().add(dir.clone().multiply(length / 2));
            List<Player> viewers = Fx.viewers(mid, Fx.viewDistance() + length / 2);

            for (double t = 1.0; t <= length; t += 1.3) {
                boom.spawn(eye.getX() + dir.getX() * t, eye.getY() + dir.getY() * t, eye.getZ() + dir.getZ() * t, viewers);
            }
            Fx.helix(helixA, eye, dir, length, 0.55, 0.35, 0, viewers);
            Fx.helix(helixB, eye, dir, length, 0.55, 0.7, Math.PI, viewers);
            def.sound("boom", SoundSpec.of("entity.warden.sonic_boom", 2.5f, 1.0f)).play(caster.getLocation());

            double width = def.num("width", 1.1);
            double damage = def.num("damage", 10);
            String type = def.string("damage-type", "sonic_boom");
            BoundingBox box = BoundingBox.of(eye, end).expand(width);
            for (Entity entity : caster.getWorld().getNearbyEntities(box)) {
                if (!ctx.plugin().combat().canTarget(caster, entity)) {
                    continue;
                }
                LivingEntity living = (LivingEntity) entity;
                Vector center = living.getLocation().add(0, living.getHeight() / 2, 0).toVector();
                Vector rel = center.clone().subtract(eye.toVector());
                double along = Math.max(0, Math.min(length, rel.dot(dir)));
                Vector closest = eye.toVector().add(dir.clone().multiply(along));
                if (closest.distanceSquared(center) <= width * width + living.getWidth()) {
                    ctx.plugin().combat().damage(caster, living, damage, type);
                    living.setVelocity(dir.clone().multiply(def.num("knockback", 1.6)).setY(def.num("knockback-vertical", 0.4)));
                }
            }
        }
    }
}
