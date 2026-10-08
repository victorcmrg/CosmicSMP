package com.cosmicsmp.feature.ability.impl.trail;

import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.SummonService;
import com.cosmicsmp.feature.ability.impl.AbilityEffect;
import com.cosmicsmp.feature.ability.impl.RideStates;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.List;

/**
 * Trail Primary #3: ride the Alien Dragon for 10 seconds, steering with your view.
 * Placeholder mount is a scaled Phantom; {@code settings.models.dragon} / {@code settings.mob} swap in the real model.
 */
public final class DragonRide implements Ability {

    @Override
    public String id() {
        return "dragon_ride";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Player caster = ctx.caster();
        if (caster.isInsideVehicle() || ctx.plugin().effects().find(caster.getUniqueId(), Ride.class).isPresent()) {
            return CastResult.BLOCKED;
        }
        int duration = ctx.def().integer("duration-seconds", 10) * 20;
        Location spawn = caster.getLocation().add(0, 0.3, 0);
        SummonService.Summon mount = ctx.plugin().summons().spawn(caster, spawn, ctx.def().string("mob", "PHANTOM"), EntityType.PHANTOM,
                duration + 40, ctx.def().model("dragon"), ctx.def().num("scale", 3.0), e -> {
                    e.setInvulnerable(true);
                    e.setGravity(false);
                    if (e instanceof Mob mob) {
                        mob.setAI(false);
                    }
                    if (e instanceof LivingEntity living) {
                        living.setCollidable(false);
                    }
                });
        if (!mount.entity.addPassenger(caster)) {
            ctx.plugin().summons().remove(mount.entity);
            return CastResult.BLOCKED;
        }
        RideStates.begin(ctx.plugin(), caster, duration);
        ctx.plugin().effects().start(new Ride(ctx, mount.entity, duration));
        return CastResult.SUCCESS;
    }

    private static final class Ride extends AbilityEffect {
        private final Entity mount;
        private final double speed;
        private final ParticleSpec wings;
        private final ParticleSpec embers;

        Ride(AbilityContext ctx, Entity mount, int duration) {
            super(ctx, duration);
            this.mount = mount;
            this.speed = ctx.def().num("speed", 0.85);
            this.wings = ctx.def().particle("trail", new ParticleSpec(Particle.DRAGON_BREATH, 2, 0.4, 0.1, 0.4, 0.01, 1.0f));
            this.embers = ctx.def().particle("embers", ParticleSpec.of(Particle.FLAME, 1, 0.3, 0.01));
        }

        @Override
        protected void onStart() {
            def.sound("cast", SoundSpec.of("entity.ender_dragon.growl", 1.2f, 1.1f)).play(caster.getLocation());
        }

        @Override
        protected void onTick() {
            if (casterGone() || !mount.isValid() || !mount.equals(caster.getVehicle())) {
                cancel(EndReason.CANCELLED);
                return;
            }
            Vector dir = caster.getEyeLocation().getDirection();
            Location current = mount.getLocation();
            Location next = current.clone().add(dir.clone().multiply(speed));
            if (!clear(next)) {
                Vector flat = dir.clone().setY(0);
                next = flat.lengthSquared() < 1.0E-4 ? current.clone() : current.clone().add(flat.normalize().multiply(speed));
                if (!clear(next)) {
                    next = current.clone();
                }
            }
            next.setYaw(caster.getLocation().getYaw());
            next.setPitch(0);
            mount.teleport(next);

            List<Player> viewers = viewers(next);
            Location behind = next.clone().subtract(dir.clone().multiply(1.5));
            if (age % 2 == 0) {
                wings.spawn(behind, viewers);
            }
            embers.spawn(behind, viewers);
            if (age % 12 == 0) {
                def.sound("flap", SoundSpec.of("entity.ender_dragon.flap", 0.9f, 1.2f)).play(next);
            }
            if (age % 20 == 0) {
                ctx.plugin().hud().hold(caster, 1100);
                ctx.plugin().messages().actionBar(caster, "abilities.ride-remaining",
                        Placeholders.of("time", Math.max(0, (maxTicksLeft()) / 20)));
            }
        }

        private int maxTicksLeft() {
            return def.integer("duration-seconds", 10) * 20 - age;
        }

        private static boolean clear(Location location) {
            return location.getBlock().isPassable() && location.clone().add(0, 1, 0).getBlock().isPassable();
        }

        @Override
        protected void onEnd(EndReason reason) {
            if (mount.isValid()) {
                mount.eject();
            }
            ctx.plugin().summons().remove(mount);
            RideStates.end(ctx.plugin(), caster, reason);
            if (caster.isOnline()) {
                def.sound("end", SoundSpec.of("entity.phantom.death", 0.8f, 0.6f)).play(caster.getLocation());
            }
        }
    }
}
