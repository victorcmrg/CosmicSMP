package com.cosmicsmp.feature.ability.impl.cosmic;

import com.cosmicsmp.core.fx.DisplayFx;
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
import com.cosmicsmp.feature.ability.impl.RideStates;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Cosmic Primary #1: ride a burning comet for 8 seconds. The comet is an item display (client-interpolated motion),
 * so the ride is perfectly smooth; set {@code settings.comet-item} to an ItemsAdder item for a custom model.
 */
public final class CometRide implements Ability {

    @Override
    public String id() {
        return "comet_ride";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Player caster = ctx.caster();
        if (caster.isInsideVehicle() || ctx.plugin().effects().find(caster.getUniqueId(), Comet.class).isPresent()) {
            return CastResult.BLOCKED;
        }
        int duration = ctx.def().integer("duration-seconds", 8) * 20;
        RideStates.begin(ctx.plugin(), caster, duration);
        ctx.plugin().effects().start(new Comet(ctx, duration));
        return CastResult.SUCCESS;
    }

    private static final class Comet extends AbilityEffect {
        private final double speed;
        private final float scale;
        private final ParticleSpec fire;
        private final ParticleSpec stardust;
        private ItemDisplay comet;
        private boolean crashed;

        Comet(AbilityContext ctx, int duration) {
            super(ctx, duration);
            this.speed = ctx.def().num("speed", 1.0);
            this.scale = (float) ctx.def().num("comet-scale", 1.4);
            this.fire = ctx.def().particle("fire", ParticleSpec.of(Particle.FLAME, 4, 0.25, 0.02));
            this.stardust = ctx.def().particle("stardust", ParticleSpec.dust("#c084fc", 1.3f, 2, 0.3));
        }

        @Override
        protected void onStart() {
            comet = DisplayFx.item(ctx.plugin(), caster.getLocation(), def.item("comet-item", ItemSpec.of("MAGMA_BLOCK", null, List.of()))
                    .build(ctx.plugin(), Placeholders.empty(), caster), DisplayFx.transform(0, -0.5f, 0, scale), d -> {
                d.setBrightness(new Display.Brightness(15, 15));
                d.setTeleportDuration(2);
            });
            if (!comet.addPassenger(caster)) {
                cancel(EndReason.CANCELLED);
                return;
            }
            def.sound("cast", SoundSpec.of("entity.blaze.shoot", 1.2f, 0.7f)).play(caster.getLocation());
            SoundSpec.of("item.firecharge.use", 1.0f, 0.6f).play(caster.getLocation());
        }

        @Override
        protected void onTick() {
            if (casterGone() || comet == null || !comet.isValid() || !comet.equals(caster.getVehicle())) {
                cancel(EndReason.CANCELLED);
                return;
            }
            Vector dir = caster.getEyeLocation().getDirection();
            Location next = comet.getLocation().add(dir.clone().multiply(speed));
            if (!next.getBlock().isPassable() || !next.clone().add(0, 1.2, 0).getBlock().isPassable()) {
                crashed = true;
                finish();
                return;
            }
            next.setYaw(caster.getLocation().getYaw());
            next.setPitch(0);
            comet.teleport(next);
            if (age % 10 == 1) {
                DisplayFx.animate(comet, 10, DisplayFx.transform(new Vector3f(0, -0.5f, 0),
                        new Quaternionf(new AxisAngle4f((float) (age * 0.3), 1, 0.4f, 0)), new Vector3f(scale)));
            }
            List<Player> viewers = viewers(next);
            Location tail = next.clone().subtract(dir.clone().multiply(0.8));
            fire.spawn(tail, viewers);
            stardust.spawn(tail, viewers);
            if (age % 3 == 0) {
                ParticleSpec.of(Particle.FIREWORK, 2, 0.15, 0.03).spawn(tail, viewers);
            }
            if (age % 5 == 0) {
                ParticleSpec.of(Particle.LAVA).spawn(tail, viewers);
            }
            if (age % 20 == 0) {
                def.sound("loop", SoundSpec.of("entity.blaze.burn", 0.8f, 0.6f)).play(next);
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            Location where = comet != null && comet.isValid() ? comet.getLocation() : caster.getLocation();
            if (comet != null) {
                comet.eject();
                ctx.plugin().tempEntities().remove(comet);
            }
            if ((crashed || reason == EndReason.COMPLETED) && caster.isOnline()) {
                impact(where);
            }
            RideStates.end(ctx.plugin(), caster, reason);
        }

        private void impact(Location where) {
            List<Player> viewers = viewers(where);
            ParticleSpec.of(Particle.EXPLOSION, 2, 0.6, 0).spawn(where, viewers);
            ParticleSpec.of(Particle.FLAME, 40, 0.8, 0.12).spawn(where, viewers);
            ParticleSpec.of(Particle.END_ROD, 20, 0.6, 0.2).spawn(where, viewers);
            def.sound("impact", SoundSpec.of("entity.firework_rocket.large_blast", 1.2f, 0.8f)).play(where);
            SoundSpec.of("entity.generic.explode", 0.7f, 1.5f).play(where);
            double damage = def.num("impact-damage", 4);
            if (damage > 0) {
                for (LivingEntity living : Targeting.nearby(ctx.plugin(), caster, where, def.num("impact-radius", 3))) {
                    ctx.plugin().combat().damage(caster, living, damage);
                    ctx.plugin().combat().knockback(living, where, 0.7, 0.4);
                }
            }
        }
    }
}
