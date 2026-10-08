package com.cosmicsmp.feature.ability.impl.sonic;

import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.Targeting;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.List;

/** Sonic Primary #1: sculk tendrils drag the target to you, then a Warden-style smash. */
public final class SculkGrasp implements Ability {

    @Override
    public String id() {
        return "sculk_grasp";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = Targeting.entityInSight(ctx.plugin(), ctx.caster(), ctx.def().num("range", 18),
                ctx.def().num("ray-size", 0.6), ctx.def().bool("players-only", false));
        if (target == null) {
            return CastResult.NO_TARGET;
        }
        ctx.plugin().effects().start(new Grasp(ctx, target));
        return CastResult.SUCCESS;
    }

    private static final class Grasp extends AbilityEffect {
        private final LivingEntity target;
        private final double speed;
        private final ParticleSpec tendril;
        private final ParticleSpec pop;
        private boolean impacted;

        Grasp(AbilityContext ctx, LivingEntity target) {
            super(ctx, ctx.def().integer("max-pull-ticks", 20) + 1);
            this.target = target;
            this.speed = ctx.def().num("pull-speed", 1.15);
            this.tendril = ctx.def().particle("tendril", ParticleSpec.of(Particle.SCULK_SOUL));
            this.pop = ctx.def().particle("pop", ParticleSpec.of(Particle.SCULK_CHARGE_POP, 4, 0.25, 0.02));
        }

        @Override
        protected void onStart() {
            def.sound("cast", SoundSpec.of("entity.warden.tendril_clicks", 1.5f, 0.8f)).play(caster.getLocation());
            SoundSpec.of("block.sculk_catalyst.bloom", 1.2f, 0.7f).play(target.getLocation());
        }

        @Override
        protected void onTick() {
            if (casterGone() || !target.isValid() || target.isDead()) {
                cancel(EndReason.CANCELLED);
                return;
            }
            Location hand = caster.getEyeLocation().subtract(0, 0.4, 0);
            Location body = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            Vector toCaster = hand.toVector().subtract(body.toVector());
            double distance = toCaster.length();
            if (distance < 2.2) {
                impact();
                finish();
                return;
            }
            Vector pull = toCaster.normalize().multiply(speed);
            pull.setY(Math.max(-0.6, Math.min(0.7, pull.getY() + 0.08)));
            target.setVelocity(pull);
            List<Player> viewers = viewers(body);
            if (age % 2 == 0) {
                Fx.line(tendril, hand, body, 0.7, viewers);
            }
            pop.spawn(body, viewers);
        }

        private void impact() {
            if (impacted) {
                return;
            }
            impacted = true;
            Location body = target.getLocation().add(0, target.getHeight() * 0.5, 0);
            List<Player> viewers = viewers(body);
            ParticleSpec.of(Particle.SONIC_BOOM).spawn(body, viewers);
            ParticleSpec.of(Particle.SCULK_SOUL, 18, 0.4, 0.08).spawn(body, viewers);
            def.sound("impact", SoundSpec.of("entity.warden.attack_impact", 1.6f, 0.9f)).play(body);
            ctx.plugin().combat().damage(caster, target, def.num("damage", 8));
            ctx.plugin().combat().knockback(target, caster.getLocation(), def.num("knockback", 0.7), def.num("knockback-vertical", 0.35));
            int darkness = def.integer("darkness-seconds", 3);
            if (darkness > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, darkness * 20, 0, false, false, true));
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            if (reason == EndReason.COMPLETED && !impacted && target.isValid() && !casterGone()
                    && target.getLocation().distanceSquared(caster.getLocation()) < 25) {
                impact();
            }
        }
    }
}
