package com.cosmicsmp.feature.ability.impl.sonic;

import com.cosmicsmp.CosmicSMP;
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
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;

import java.util.List;

/**
 * Sonic Primary #3: summons a Space Warden that hunts the caster's enemies (vanilla Warden placeholder - set
 * {@code settings.mob: mythic:SpaceWarden} or {@code settings.models.warden: space_warden} for the custom model).
 */
public final class SpaceWarden implements Ability, Listener {

    private final CosmicSMP plugin;

    public SpaceWarden(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "space_warden";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        Player caster = ctx.caster();
        Location spawn = caster.getLocation().add(com.cosmicsmp.core.fx.Fx.horizontal(caster.getLocation().getDirection()).multiply(2.5));
        spawn = Targeting.ground(spawn, 6);
        if (!Targeting.isPassable(spawn) || !Targeting.isPassable(spawn.clone().add(0, 1, 0))) {
            spawn = caster.getLocation();
        }
        spawn.setYaw(caster.getLocation().getYaw());
        int lifetime = ctx.def().integer("lifetime-seconds", 25) * 20;
        double health = ctx.def().num("health", 120);
        boolean glow = ctx.def().bool("glowing", true);
        SummonService.Summon summon = plugin.summons().spawn(caster, spawn, ctx.def().string("mob", "WARDEN"), EntityType.WARDEN,
                lifetime, ctx.def().model("warden"), ctx.def().num("scale", 1.0), e -> {
                    if (e instanceof LivingEntity living) {
                        AttributeInstance max = living.getAttribute(Attribute.MAX_HEALTH);
                        if (max != null) {
                            max.setBaseValue(health);
                            living.setHealth(health);
                        }
                        living.setGlowing(glow);
                    }
                });
        summon.damageOverride = ctx.def().num("damage", 10);
        plugin.effects().start(new Brain(ctx, summon, lifetime));
        return CastResult.SUCCESS;
    }

    /** Owners of a Space Warden never get the warden's darkness pulse. */
    @EventHandler(ignoreCancelled = true)
    public void onDarkness(EntityPotionEffectEvent event) {
        if (event.getCause() == EntityPotionEffectEvent.Cause.WARDEN && event.getEntity() instanceof Player player
                && plugin.summons().ownsAny(player.getUniqueId(), EntityType.WARDEN)) {
            event.setCancelled(true);
        }
    }

    private static final class Brain extends AbilityEffect {
        private final SummonService.Summon summon;
        private final ParticleSpec aura;
        private final ParticleSpec stars;

        Brain(AbilityContext ctx, SummonService.Summon summon, int lifetime) {
            super(ctx, lifetime);
            this.summon = summon;
            this.aura = ctx.def().particle("aura", ParticleSpec.dust("#22d3ee", 1.4f, 3, 0.6));
            this.stars = ctx.def().particle("stars", ParticleSpec.of(Particle.END_ROD, 2, 0.6, 0.01));
        }

        @Override
        protected void onStart() {
            Location at = summon.entity.getLocation();
            List<Player> viewers = viewers(at);
            ParticleSpec.block(Particle.BLOCK, Material.SCULK, 50, 0.8).spawn(at, viewers);
            ParticleSpec.of(Particle.SCULK_SOUL, 25, 0.8, 0.05).spawn(at.clone().add(0, 1, 0), viewers);
            def.sound("summon", SoundSpec.of("entity.warden.emerge", 1.4f, 1.2f)).play(at);
        }

        @Override
        protected void onTick() {
            Entity entity = summon.entity;
            if (!entity.isValid()) {
                finish();
                return;
            }
            if (age % 5 == 0) {
                Location at = entity.getLocation().add(0, 1.4, 0);
                List<Player> viewers = viewers(at);
                aura.spawn(at, viewers);
                stars.spawn(at, viewers);
            }
            if (age % 10 != 0 || !(entity instanceof Mob mob) || casterGone()) {
                return;
            }
            LivingEntity target = nearestEnemy(entity.getLocation(), def.num("target-radius", 16));
            if (target != null) {
                if (mob instanceof Warden warden) {
                    warden.setAnger(target, 150);
                }
                mob.setTarget(target);
            } else {
                mob.setTarget(null);
                if (entity.getLocation().distanceSquared(caster.getLocation()) > 49) {
                    mob.getPathfinder().moveTo(caster, 1.3);
                }
            }
        }

        private LivingEntity nearestEnemy(Location from, double radius) {
            LivingEntity best = null;
            double bestDistance = Double.MAX_VALUE;
            for (LivingEntity candidate : Targeting.nearby(ctx.plugin(), caster, from, radius)) {
                if (candidate.equals(summon.entity) || ctx.plugin().summons().ownerOf(candidate) != null
                        && caster.getUniqueId().equals(ctx.plugin().summons().ownerOf(candidate))) {
                    continue;
                }
                double d = candidate.getLocation().distanceSquared(from);
                if (d < bestDistance) {
                    bestDistance = d;
                    best = candidate;
                }
            }
            return best;
        }

        @Override
        protected void onEnd(EndReason reason) {
            Entity entity = summon.entity;
            if (entity.isValid()) {
                Location at = entity.getLocation();
                List<Player> viewers = viewers(at);
                ParticleSpec.of(Particle.SCULK_SOUL, 30, 0.7, 0.05).spawn(at.clone().add(0, 1, 0), viewers);
                ParticleSpec.of(Particle.REVERSE_PORTAL, 40, 0.6, 0.1).spawn(at.clone().add(0, 1, 0), viewers);
                def.sound("despawn", SoundSpec.of("entity.warden.dig", 1.2f, 1.3f)).play(at);
            }
            ctx.plugin().summons().remove(entity);
        }
    }
}
