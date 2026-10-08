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
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Nature Primary #1: a twisting vine erupts under the target, roots and hits for 6 hearts. */
public final class VineEruption implements Ability {

    @Override
    public String id() {
        return "vine_eruption";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        double range = ctx.def().num("range", 20);
        LivingEntity target = Targeting.entityInSight(ctx.plugin(), ctx.caster(), range, 0.7, false);
        Location point = target != null ? target.getLocation() : Targeting.pointInSight(ctx.caster(), range);
        ctx.plugin().effects().start(new Eruption(ctx, Targeting.ground(point, 8)));
        return CastResult.SUCCESS;
    }

    private static BlockData block(String name, Material fallback) {
        Material material = Material.matchMaterial(name == null ? "" : name.toUpperCase(Locale.ROOT));
        return (material == null || !material.isBlock() ? fallback : material).createBlockData();
    }

    private static final class Eruption extends AbilityEffect {
        private static final int SEGMENTS = 6;
        private static final int HIT = 7;
        private final Location center;
        private final List<BlockDisplay> parts = new ArrayList<>();
        private final List<Vector3f> targets = new ArrayList<>();
        private final List<Float> yaws = new ArrayList<>();

        Eruption(AbilityContext ctx, Location center) {
            super(ctx, 46);
            this.center = center;
        }

        @Override
        protected void onStart() {
            BlockData stem = block(def.string("vine-block", "MOSS_BLOCK"), Material.MOSS_BLOCK);
            BlockData leaves = block(def.string("leaf-block", "AZALEA_LEAVES"), Material.AZALEA_LEAVES);
            for (int i = 0; i < SEGMENTS; i++) {
                double angle = i * 0.9;
                float x = (float) (Math.cos(angle) * 0.28);
                float z = (float) (Math.sin(angle) * 0.28);
                float yaw = (float) (i * 0.6);
                Vector3f position = new Vector3f(x, i * 0.55f, z);
                targets.add(position);
                yaws.add(yaw);
                BlockData data = i == SEGMENTS - 1 ? leaves : stem;
                parts.add(DisplayFx.block(ctx.plugin(), center, data,
                        DisplayFx.centeredBlock(new Vector3f(x, -0.6f, z), yaw, new Vector3f(0.4f, 0.01f, 0.4f)), null));
            }
            List<Player> viewers = viewers(center);
            Fx.circle(ParticleSpec.dust("#22c55e", 1.3f), center.clone().add(0, 0.15, 0), 1.2, 16, viewers);
            ParticleSpec.block(Particle.BLOCK, Material.ROOTED_DIRT, 30, 0.6).spawn(center.clone().add(0, 0.2, 0), viewers);
            def.sound("cast", SoundSpec.of("block.rooted_dirt.break", 1.4f, 0.7f)).play(center);
        }

        @Override
        protected void onTick() {
            int index = age - 1;
            if (index >= 0 && index < SEGMENTS) {
                DisplayFx.animate(parts.get(index), 5, DisplayFx.centeredBlock(targets.get(index), yaws.get(index),
                        new Vector3f(index == SEGMENTS - 1 ? 0.9f : 0.42f, 0.7f, index == SEGMENTS - 1 ? 0.9f : 0.42f)));
                if (index % 2 == 0) {
                    SoundSpec.of("block.big_dripleaf.tilt_down", 0.8f, 0.6f + index * 0.1f).play(center);
                }
            }
            if (age == HIT) {
                List<Player> viewers = viewers(center);
                ParticleSpec.of(Particle.SPORE_BLOSSOM_AIR, 30, 1.0, 0).spawn(center.clone().add(0, 1.5, 0), viewers);
                ParticleSpec.dust("#16a34a", 1.6f, 25, 0.8).spawn(center.clone().add(0, 1, 0), viewers);
                def.sound("hit", SoundSpec.of("entity.evoker_fangs.attack", 1.2f, 0.8f)).play(center);
                for (LivingEntity living : Targeting.nearby(ctx.plugin(), caster, center.clone().add(0, 0.8, 0), def.num("radius", 1.8))) {
                    ctx.plugin().combat().damage(caster, living, def.num("damage", 12));
                    living.setVelocity(living.getVelocity().setY(0.45));
                    ctx.plugin().combat().stun(living, (int) (def.num("root-seconds", 2) * 20));
                }
            }
            if (age == 34) {
                for (int i = 0; i < parts.size(); i++) {
                    DisplayFx.animate(parts.get(i), 8, DisplayFx.centeredBlock(new Vector3f(targets.get(i).x, -0.8f, targets.get(i).z),
                            yaws.get(i), new Vector3f(0.3f, 0.01f, 0.3f)));
                }
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            parts.forEach(ctx.plugin().tempEntities()::remove);
        }
    }
}
