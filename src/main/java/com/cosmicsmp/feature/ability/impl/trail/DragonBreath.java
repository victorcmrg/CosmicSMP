package com.cosmicsmp.feature.ability.impl.trail;

import com.cosmicsmp.core.fx.DisplayFx;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.item.ItemSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.Targeting;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.List;

/** Trail Primary #2: a fireball of dragon's breath; explodes on impact and leaves a burning breath cloud. */
public final class DragonBreath implements Ability {

    @Override
    public String id() {
        return "dragon_breath";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        ctx.plugin().effects().start(new Fireball(ctx));
        return CastResult.SUCCESS;
    }

    private static final class Fireball extends AbilityEffect {
        private final double speed;
        private final double maxRange;
        private final int lingerTicks;
        private final ParticleSpec trail;
        private final ParticleSpec trail2;
        private Location position;
        private Vector velocity;
        private ItemDisplay ball;
        private double travelled;
        private int explodedAt = -1;

        Fireball(AbilityContext ctx) {
            super(ctx, 200);
            this.speed = ctx.def().num("speed", 1.3);
            this.maxRange = ctx.def().num("max-range", 40);
            this.lingerTicks = ctx.def().integer("linger-ticks", 40);
            this.trail = ctx.def().particle("trail", new ParticleSpec(Particle.DRAGON_BREATH, 3, 0.12, 0.12, 0.12, 0.01, 1.0f));
            this.trail2 = ctx.def().particle("trail-secondary", ParticleSpec.of(Particle.REVERSE_PORTAL, 2, 0.1, 0.02));
        }

        @Override
        protected void onStart() {
            Location eye = caster.getEyeLocation();
            velocity = eye.getDirection().normalize().multiply(speed);
            position = eye.clone().add(eye.getDirection().multiply(1.2));
            ball = DisplayFx.item(ctx.plugin(), position, def.item("projectile-item", ItemSpec.of("DRAGON_BREATH", null, List.of()))
                    .build(ctx.plugin(), Placeholders.empty(), caster), DisplayFx.transform(0, 0, 0, (float) def.num("projectile-scale", 1.4)), d -> {
                d.setBillboard(Display.Billboard.CENTER);
                d.setBrightness(new Display.Brightness(15, 15));
            });
            def.sound("cast", SoundSpec.of("entity.ender_dragon.shoot", 1.2f, 1.0f)).play(caster.getLocation());
        }

        @Override
        protected void onTick() {
            if (explodedAt >= 0) {
                linger();
                return;
            }
            RayTraceResult hit = position.getWorld().rayTrace(position, velocity, speed, FluidCollisionMode.NEVER, true, 0.6,
                    e -> ctx.plugin().combat().canTarget(caster, e));
            if (hit != null) {
                position = hit.getHitPosition().toLocation(position.getWorld());
                explode();
                return;
            }
            position.add(velocity);
            travelled += speed;
            DisplayFx.glide(ball, position, 2);
            List<Player> viewers = viewers(position);
            trail.spawn(position, viewers);
            trail2.spawn(position, viewers);
            if (age % 3 == 0) {
                ParticleSpec.of(Particle.FLAME).spawn(position, viewers);
            }
            if (travelled >= maxRange) {
                explode();
            }
        }

        private void explode() {
            explodedAt = age;
            ctx.plugin().tempEntities().remove(ball);
            ball = null;
            List<Player> viewers = viewers(position);
            new ParticleSpec(Particle.DRAGON_BREATH, 90, 1.2, 0.8, 1.2, 0.04, 1.0f).spawn(position, viewers);
            ParticleSpec.of(Particle.EXPLOSION, 4, 0.9, 0).spawn(position, viewers);
            ParticleSpec.of(Particle.REVERSE_PORTAL, 50, 1.0, 0.2).spawn(position, viewers);
            def.sound("impact", SoundSpec.of("entity.dragon_fireball.explode", 1.4f, 1.0f)).play(position);
            SoundSpec.of("entity.generic.explode", 0.8f, 1.3f).play(position);
            double radius = def.num("radius", 3.5);
            for (LivingEntity living : Targeting.nearby(ctx.plugin(), caster, position, radius)) {
                ctx.plugin().combat().damage(caster, living, def.num("damage", 12));
                ctx.plugin().combat().knockback(living, position, def.num("knockback", 0.8), 0.5);
            }
            if (lingerTicks <= 0) {
                finish();
            }
        }

        private void linger() {
            int since = age - explodedAt;
            if (since > lingerTicks || casterGone()) {
                finish();
                return;
            }
            if (since % 3 == 0) {
                Location ground = Targeting.ground(position, 4).add(0, 0.2, 0);
                Fx.circle(new ParticleSpec(Particle.DRAGON_BREATH, 1, 0.1, 0.05, 0.1, 0.005, 1.0f), ground, def.num("radius", 3.5) * 0.8, 18, viewers(ground));
            }
            double lingerDamage = def.num("linger-damage", 0);
            if (lingerDamage > 0 && since % 10 == 0) {
                for (LivingEntity living : Targeting.nearby(ctx.plugin(), caster, position, def.num("radius", 3.5))) {
                    ctx.plugin().combat().damage(caster, living, lingerDamage);
                }
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            ctx.plugin().tempEntities().remove(ball);
        }
    }
}
