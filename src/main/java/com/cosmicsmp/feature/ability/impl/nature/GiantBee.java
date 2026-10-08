package com.cosmicsmp.feature.ability.impl.nature;

import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.SummonService;
import com.cosmicsmp.feature.ability.Targeting;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** Nature Primary #2: a giant bee flies to the target, stings (20s nausea) and buzzes away. */
public final class GiantBee implements Ability {

    @Override
    public String id() {
        return "giant_bee";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = Targeting.entityInSight(ctx.plugin(), ctx.caster(), ctx.def().num("range", 24), 0.7, false);
        if (target == null) {
            return CastResult.NO_TARGET;
        }
        Location spawn = ctx.caster().getEyeLocation().add(0, 1.2, 0);
        SummonService.Summon bee = ctx.plugin().summons().spawn(ctx.caster(), spawn, ctx.def().string("mob", "BEE"), EntityType.BEE,
                200, ctx.def().model("bee"), ctx.def().num("scale", 2.5), e -> {
                    e.setInvulnerable(true);
                    e.setGravity(false);
                    if (e instanceof Mob mob) {
                        mob.setAI(false);
                    }
                    if (e instanceof Bee b) {
                        b.setAnger(400);
                        b.setHasStung(false);
                    }
                });
        ctx.plugin().effects().start(new Flight(ctx, bee.entity, target));
        return CastResult.SUCCESS;
    }

    private static final class Flight extends AbilityEffect {
        private final Entity bee;
        private final LivingEntity target;
        private final double speed;
        private int leavingSince = -1;
        private Vector away;

        Flight(AbilityContext ctx, Entity bee, LivingEntity target) {
            super(ctx, 120);
            this.bee = bee;
            this.target = target;
            this.speed = ctx.def().num("speed", 0.75);
        }

        @Override
        protected void onStart() {
            def.sound("cast", SoundSpec.of("entity.bee.loop_aggressive", 1.5f, 0.6f)).play(bee.getLocation());
        }

        @Override
        protected void onTick() {
            if (!bee.isValid()) {
                finish();
                return;
            }
            Location at = bee.getLocation();
            if (leavingSince < 0) {
                if (!target.isValid() || target.isDead() || !target.getWorld().equals(at.getWorld()) || age > 80) {
                    leave(at);
                    return;
                }
                Vector to = target.getEyeLocation().subtract(0, 0.3, 0).toVector().subtract(at.toVector());
                if (to.length() < 1.6) {
                    sting();
                    leave(at);
                    return;
                }
                move(at, to.normalize().multiply(speed));
                if (age % 10 == 0) {
                    SoundSpec.of("entity.bee.loop_aggressive", 1.0f, 0.6f).play(at);
                }
            } else {
                if (age - leavingSince > 25) {
                    finish();
                    return;
                }
                move(at, away);
            }
        }

        private void move(Location at, Vector step) {
            Location next = at.clone().add(step);
            next.setDirection(step);
            bee.teleport(next);
        }

        private void sting() {
            ctx.plugin().combat().damage(caster, target, def.num("sting-damage", 3));
            int nausea = def.integer("nausea-seconds", 20);
            target.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nausea * 20, def.integer("nausea-amplifier", 1), false, true, true));
            int poison = def.integer("poison-seconds", 0);
            if (poison > 0) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poison * 20, 0, false, true, true));
            }
            Location hit = target.getEyeLocation();
            ParticleSpec.of(Particle.CRIT, 15, 0.3, 0.2).spawn(hit, viewers(hit));
            ParticleSpec.of(Particle.FALLING_HONEY, 8, 0.3, 0).spawn(hit, viewers(hit));
            def.sound("sting", SoundSpec.of("entity.bee.sting", 1.4f, 0.7f)).play(hit);
        }

        private void leave(Location at) {
            leavingSince = age;
            Vector dir = at.toVector().subtract(target.getLocation().toVector()).setY(0);
            if (dir.lengthSquared() < 1.0E-3) {
                dir = new Vector(1, 0, 0);
            }
            away = dir.normalize().multiply(0.7).setY(0.6).normalize().multiply(0.9);
        }

        @Override
        protected void onEnd(EndReason reason) {
            if (bee.isValid()) {
                ParticleSpec.of(Particle.CLOUD, 12, 0.5, 0.02).spawn(bee.getLocation(), viewers(bee.getLocation()));
            }
            ctx.plugin().summons().remove(bee);
        }
    }
}
