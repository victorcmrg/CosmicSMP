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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * Trail Primary #1: two giant Alien Dragon fists fall from the sky and slam the target area.
 * The fists are item displays: set {@code settings.fist-item.material} to an ItemsAdder item for the 3D model.
 */
public final class DragonFists implements Ability {

    @Override
    public String id() {
        return "dragon_fists";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        double range = ctx.def().num("range", 22);
        LivingEntity target = Targeting.entityInSight(ctx.plugin(), ctx.caster(), range, 0.8, false);
        Location point = target != null ? target.getLocation() : Targeting.pointInSight(ctx.caster(), range);
        ctx.plugin().effects().start(new Slam(ctx, Targeting.ground(point, 8)));
        return CastResult.SUCCESS;
    }

    private static final class Slam extends AbilityEffect {
        private static final int IMPACT = 12;
        private final Location center;
        private final Vector side;
        private final float scale;
        private final ParticleSpec warning;
        private ItemDisplay left;
        private ItemDisplay right;

        Slam(AbilityContext ctx, Location center) {
            super(ctx, 32);
            this.center = center;
            this.side = Fx.side(center.toVector().subtract(ctx.caster().getLocation().toVector()));
            this.scale = (float) ctx.def().num("fist-scale", 2.4);
            this.warning = ctx.def().particle("warning", ParticleSpec.dust("#ef4444", 1.2f));
        }

        private ItemDisplay fist(int sign) {
            ItemStack item = def.item("fist-item", ItemSpec.of("MAGMA_BLOCK", null, List.of())).build(ctx.plugin(), Placeholders.empty(), caster);
            Location at = center.clone().add(side.clone().multiply(1.6 * sign)).add(0, 6.5, 0);
            return DisplayFx.item(ctx.plugin(), at, item, DisplayFx.transform(0, 0, 0, 0.01f), d -> {
                d.setBrightness(new Display.Brightness(12, 15));
                d.setViewRange(2.0f);
            });
        }

        @Override
        protected void onStart() {
            left = fist(-1);
            right = fist(1);
            def.sound("cast", SoundSpec.of("entity.ender_dragon.growl", 0.9f, 1.4f)).play(center);
        }

        @Override
        protected void onTick() {
            if (age == 1) {
                for (int sign : new int[]{-1, 1}) {
                    ItemDisplay fist = sign < 0 ? left : right;
                    DisplayFx.animate(fist, 6, DisplayFx.transform(new Vector3f(),
                            new Quaternionf(new AxisAngle4f(0.35f * sign, 0, 0, 1)), new Vector3f(scale)));
                }
            }
            if (age < IMPACT && age % 2 == 0) {
                double radius = def.num("radius", 3.0) * (age / (double) IMPACT);
                Fx.circle(warning, center.clone().add(0, 0.15, 0), Math.max(0.4, radius), 20, viewers(center));
            }
            if (age == 8) {
                DisplayFx.glide(left, center.clone().add(side.clone().multiply(-0.55)).add(0, 0.9, 0), 4);
                DisplayFx.glide(right, center.clone().add(side.clone().multiply(0.55)).add(0, 0.9, 0), 4);
                SoundSpec.of("entity.phantom.swoop", 1.2f, 0.6f).play(center);
            }
            if (age == IMPACT) {
                impact();
            }
            if (age == 22) {
                DisplayFx.animate(left, 8, DisplayFx.transform(0, -0.6f, 0, 0.01f));
                DisplayFx.animate(right, 8, DisplayFx.transform(0, -0.6f, 0, 0.01f));
            }
        }

        private void impact() {
            List<Player> viewers = viewers(center);
            Block ground = center.clone().subtract(0, 1, 0).getBlock();
            Material material = ground.getType().isSolid() ? ground.getType() : Material.NETHERRACK;
            ParticleSpec.block(Particle.BLOCK, material, 60, 1.4).spawn(center.clone().add(0, 0.3, 0), viewers);
            ParticleSpec.of(Particle.EXPLOSION, 3, 0.8, 0).spawn(center.clone().add(0, 0.6, 0), viewers);
            ParticleSpec.of(Particle.FLAME, 30, 1.2, 0.08).spawn(center.clone().add(0, 0.4, 0), viewers);
            Fx.circle(ParticleSpec.dust("#f97316", 1.6f), center.clone().add(0, 0.2, 0), def.num("radius", 3.0), 28, viewers);
            def.sound("impact", SoundSpec.of("entity.generic.explode", 1.2f, 1.2f)).play(center);
            SoundSpec.of("block.anvil.land", 0.8f, 0.5f).play(center);
            for (LivingEntity living : Targeting.nearby(ctx.plugin(), caster, center.clone().add(0, 0.5, 0), def.num("radius", 3.0))) {
                ctx.plugin().combat().damage(caster, living, def.num("damage", 8));
                ctx.plugin().combat().knockback(living, center, def.num("knockback", 0.9), def.num("knockback-vertical", 0.6));
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            ctx.plugin().tempEntities().remove(left);
            ctx.plugin().tempEntities().remove(right);
        }
    }
}
