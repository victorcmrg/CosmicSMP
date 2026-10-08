package com.cosmicsmp.feature.ability.impl.nature;

import com.cosmicsmp.core.fx.DisplayFx;
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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * Nature Primary #3 - Earth Clap: two walls of the ground's own block rise on each side of the target and clap
 * together (7 hearts + stun). Walls are block displays: the world is never modified, so nothing can be griefed,
 * duplicated or left behind after a crash.
 */
public final class EarthClap implements Ability {

    @Override
    public String id() {
        return "earth_clap";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        double range = ctx.def().num("range", 18);
        LivingEntity target = Targeting.entityInSight(ctx.plugin(), ctx.caster(), range, 0.7, false);
        Location point = target != null ? target.getLocation() : Targeting.pointInSight(ctx.caster(), range);
        ctx.plugin().effects().start(new Clap(ctx, Targeting.ground(point, 8)));
        return CastResult.SUCCESS;
    }

    private static final class Clap extends AbilityEffect {
        private static final int RISE = 2;
        private static final int SLAM = 14;
        private static final int IMPACT = 17;
        private static final int SINK = 40;
        private final Location center;
        private final Vector side;
        private final float yaw;
        private final float width;
        private final float height;
        private final double distance;
        private BlockDisplay left;
        private BlockDisplay right;
        private Material material;

        Clap(AbilityContext ctx, Location center) {
            super(ctx, 54);
            this.center = center;
            Vector dir = center.toVector().subtract(ctx.caster().getLocation().toVector()).setY(0);
            if (dir.lengthSquared() < 1.0E-3) {
                dir = ctx.caster().getLocation().getDirection().setY(0);
            }
            dir.normalize();
            this.side = Fx.side(dir);
            this.yaw = (float) Math.atan2(dir.getX(), dir.getZ());
            this.width = (float) ctx.def().num("wall-width", 3);
            this.height = (float) ctx.def().num("wall-height", 3);
            this.distance = ctx.def().num("distance", 2.4);
        }

        private BlockDisplay wall(int sign, BlockData data) {
            Location at = center.clone().add(side.clone().multiply(distance * sign));
            return DisplayFx.block(ctx.plugin(), at, data,
                    DisplayFx.centeredBlock(new Vector3f(0, -height - 0.3f, 0), yaw, new Vector3f(0.8f, height, width)), d -> d.setViewRange(2.0f));
        }

        @Override
        protected void onStart() {
            Block ground = center.clone().subtract(0, 1, 0).getBlock();
            if (ground.getType().isSolid() && ground.getType().isOccluding()) {
                material = ground.getType();
            } else {
                Material fallback = Material.matchMaterial(def.string("fallback-block", "MOSS_BLOCK").toUpperCase(Locale.ROOT));
                material = fallback == null || !fallback.isBlock() ? Material.MOSS_BLOCK : fallback;
            }
            BlockData data = material.createBlockData();
            left = wall(-1, data);
            right = wall(1, data);
            Fx.circle(ParticleSpec.dust("#a16207", 1.3f), center.clone().add(0, 0.15, 0), 1.5, 18, viewers(center));
        }

        @Override
        protected void onTick() {
            if (age == RISE) {
                Vector3f scale = new Vector3f(0.8f, height, width);
                DisplayFx.animate(left, 6, DisplayFx.centeredBlock(new Vector3f(0, 0, 0), yaw, scale));
                DisplayFx.animate(right, 6, DisplayFx.centeredBlock(new Vector3f(0, 0, 0), yaw, scale));
                List<Player> viewers = viewers(center);
                for (int sign : new int[]{-1, 1}) {
                    Location base = center.clone().add(side.clone().multiply(distance * sign)).add(0, 0.2, 0);
                    ParticleSpec.block(Particle.BLOCK, material, 30, 0.6).spawn(base, viewers);
                }
                def.sound("rise", SoundSpec.of("entity.ravager.step", 1.4f, 0.6f)).play(center);
                SoundSpec.of("block.rooted_dirt.break", 1.0f, 0.6f).play(center);
            }
            if (age == SLAM) {
                DisplayFx.glide(left, center.clone().add(side.clone().multiply(-0.45)), 3);
                DisplayFx.glide(right, center.clone().add(side.clone().multiply(0.45)), 3);
            }
            if (age == IMPACT) {
                impact();
            }
            if (age == SINK) {
                Vector3f scale = new Vector3f(0.8f, height, width);
                DisplayFx.animate(left, 10, DisplayFx.centeredBlock(new Vector3f(0, -height - 0.3f, 0), yaw, scale));
                DisplayFx.animate(right, 10, DisplayFx.centeredBlock(new Vector3f(0, -height - 0.3f, 0), yaw, scale));
            }
        }

        private void impact() {
            List<Player> viewers = viewers(center);
            ParticleSpec.block(Particle.BLOCK, material, 70, 1.0).spawn(center.clone().add(0, 1, 0), viewers);
            ParticleSpec.of(Particle.EXPLOSION).spawn(center.clone().add(0, 1, 0), viewers);
            def.sound("impact", SoundSpec.of("block.anvil.land", 1.0f, 0.5f)).play(center);
            SoundSpec.of("entity.iron_golem.damage", 1.2f, 0.6f).play(center);
            double radius = def.num("radius", 1.8);
            for (LivingEntity living : Targeting.nearby(ctx.plugin(), caster, center.clone().add(0, 1, 0), radius + 0.6)) {
                ctx.plugin().combat().damage(caster, living, def.num("damage", 14));
                living.setVelocity(new Vector(0, 0.05, 0));
                ctx.plugin().combat().stun(living, (int) (def.num("stun-seconds", 2.5) * 20));
                living.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 25, 0, false, false, false));
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            ctx.plugin().tempEntities().remove(left);
            ctx.plugin().tempEntities().remove(right);
        }
    }
}
