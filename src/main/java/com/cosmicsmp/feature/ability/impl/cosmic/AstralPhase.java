package com.cosmicsmp.feature.ability.impl.cosmic;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.task.TimedEffect;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.CombatService;
import com.cosmicsmp.feature.ability.Targeting;
import com.cosmicsmp.feature.ability.TimedStateService;
import com.cosmicsmp.feature.star.AbilityDefinition;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cosmic Primary #3: knocks the target's spirit out of their body. The body stays behind as a Mannequin with their
 * skin; the spirit is invisible, intangible and can't fight until it walks back into its body (or the time runs out
 * and it is pulled back). Hitting the empty body hurts the spirit.
 */
public final class AstralPhase implements Ability, Listener {

    public static final String STATE = "astral_phase";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final CosmicSMP plugin;
    private final Map<UUID, Phase> bodies = new HashMap<>();

    public AstralPhase(CosmicSMP plugin) {
        this.plugin = plugin;
        plugin.timedStates().register(STATE, new TimedStateService.Handler() {
            @Override
            public void resume(Player player, PlayerData.TimedState state, long remainingMillis) {
                cleanup(player);
            }

            @Override
            public void expire(Player player, PlayerData.TimedState state) {
                cleanup(player);
            }

            private void cleanup(Player player) {
                removeInvisibility(player);
                plugin.combat().setIntangible(player.getUniqueId(), false);
                plugin.timedStates().clear(player, STATE);
            }
        });
    }

    @Override
    public String id() {
        return "astral_phase";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity hit = Targeting.entityInSight(plugin, ctx.caster(), ctx.def().num("range", 20), 0.6, true);
        if (!(hit instanceof Player target)) {
            return CastResult.NO_TARGET;
        }
        if (plugin.effects().find(target.getUniqueId(), Phase.class).isPresent()) {
            return CastResult.BLOCKED;
        }
        plugin.effects().start(new Phase(ctx.caster(), target, ctx.def()));
        return CastResult.SUCCESS;
    }

    private static void removeInvisibility(Player player) {
        PotionEffect effect = player.getPotionEffect(PotionEffectType.INVISIBILITY);
        if (effect != null && effect.isAmbient() && !effect.hasParticles()) {
            player.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
    }

    // ------------------------------------------------------------------ guards

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBodyHit(EntityDamageEvent event) {
        Phase phase = bodies.get(event.getEntity().getUniqueId());
        if (phase == null) {
            return;
        }
        event.setCancelled(true);
        double amount = event.getDamage() * phase.def.num("body-damage-multiplier", 1.5);
        Player attacker = event instanceof EntityDamageByEntityEvent byEntity ? CombatService.attacker(byEntity.getDamager()) : null;
        if (attacker != null && !attacker.equals(phase.target)) {
            plugin.combat().damage(attacker, phase.target, amount);
            Location at = event.getEntity().getLocation().add(0, 1, 0);
            ParticleSpec.of(Particle.SOUL, 8, 0.3, 0.05).spawn(at, Fx.viewers(at));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (plugin.combat().isIntangible(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (plugin.combat().isIntangible(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (plugin.combat().isIntangible(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ effect

    private final class Phase extends TimedEffect {
        private final Player caster;
        private final Player target;
        private final AbilityDefinition def;
        private final ParticleSpec spirit;
        private final ParticleSpec tether;
        private Mannequin body;
        private boolean returned;

        Phase(Player caster, Player target, AbilityDefinition def) {
            super(def.integer("max-duration-seconds", 15) * 20);
            this.caster = caster;
            this.target = target;
            this.def = def;
            this.spirit = def.particle("spirit", ParticleSpec.of(Particle.WHITE_ASH, 6, 0.3, 0.01));
            this.tether = def.particle("tether", ParticleSpec.dust("#c4b5fd", 0.8f));
        }

        @Override
        public UUID owner() {
            return target.getUniqueId();
        }

        @Override
        protected void onStart() {
            int maxTicks = def.integer("max-duration-seconds", 15) * 20;
            plugin.timedStates().set(target, STATE, maxTicks * 50L + 2000L);
            Location origin = target.getLocation();
            body = plugin.tempEntities().spawn(origin, Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile(target.getPlayerProfile()));
                m.setImmovable(true);
                m.setDescription(Text.parse(def.string("body-label", "<#c4b5fd>Empty Vessel")));
                m.setCustomNameVisible(false);
                EntityEquipment equipment = m.getEquipment();
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack item = target.getInventory().getItem(slot);
                    equipment.setItem(slot, item == null ? null : item.clone());
                    equipment.setDropChance(slot, 0f);
                }
            });
            bodies.put(body.getUniqueId(), this);
            plugin.combat().setIntangible(target.getUniqueId(), true);
            target.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, maxTicks + 20, 0, true, false, true));
            hideArmor();
            Vector away = target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0);
            if (away.lengthSquared() < 1.0E-3) {
                away = target.getLocation().getDirection().multiply(-1).setY(0);
            }
            target.setVelocity(away.normalize().multiply(def.num("push", 1.4)).setY(0.55));
            List<Player> viewers = Fx.viewers(origin);
            ParticleSpec.of(Particle.SOUL, 30, 0.4, 0.06).spawn(origin.clone().add(0, 1, 0), viewers);
            ParticleSpec.of(Particle.REVERSE_PORTAL, 40, 0.5, 0.1).spawn(origin.clone().add(0, 1, 0), viewers);
            def.sound("cast", SoundSpec.of("entity.vex.charge", 1.2f, 0.6f)).play(origin);
            SoundSpec.of("block.beacon.deactivate", 1.0f, 0.5f).play(origin);
            plugin.messages().send(target, "abilities.astral-phase-victim", Placeholders.of("caster", caster.getName()));
        }

        private void hideArmor() {
            Map<EquipmentSlot, ItemStack> empty = new EnumMap<>(EquipmentSlot.class);
            for (EquipmentSlot slot : ARMOR) {
                empty.put(slot, null);
            }
            empty.put(EquipmentSlot.HAND, null);
            empty.put(EquipmentSlot.OFF_HAND, null);
            for (Player viewer : target.getTrackedBy()) {
                viewer.sendEquipmentChange(target, empty);
            }
        }

        @Override
        protected void onTick() {
            if (!target.isOnline() || target.isDead() || body == null || !body.isValid()) {
                cancel(target.isOnline() ? EndReason.CANCELLED : EndReason.QUIT);
                return;
            }
            Location spirit = target.getLocation().add(0, 1, 0);
            Location vessel = body.getLocation().add(0, 1, 0);
            List<Player> viewers = Fx.viewers(spirit);
            if (age % 2 == 0) {
                this.spirit.spawn(spirit, viewers);
                ParticleSpec.of(Particle.SOUL).spawn(spirit, viewers);
            }
            if (age % 4 == 0 && spirit.getWorld().equals(vessel.getWorld()) && spirit.distanceSquared(vessel) < 900) {
                Fx.line(tether, spirit, vessel, 0.7, viewers);
            }
            if (age % 10 == 0) {
                hideArmor();
                ParticleSpec.of(Particle.SOUL, 2, 0.3, 0.01).spawn(vessel, viewers);
            }
            double back = def.num("return-radius", 1.3);
            if (age > 20 && spirit.getWorld().equals(vessel.getWorld()) && spirit.distanceSquared(vessel) <= back * back) {
                returned = true;
                finish();
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            if (body != null) {
                bodies.remove(body.getUniqueId());
            }
            plugin.combat().setIntangible(target.getUniqueId(), false);
            if (target.isOnline()) {
                removeInvisibility(target);
                if (!returned && body != null && body.isValid() && reason != EndReason.DEATH) {
                    target.teleport(body.getLocation());
                }
                Map<EquipmentSlot, ItemStack> real = new EnumMap<>(EquipmentSlot.class);
                for (EquipmentSlot slot : EquipmentSlot.values()) {
                    if (slot != EquipmentSlot.BODY && slot != EquipmentSlot.SADDLE) {
                        real.put(slot, target.getInventory().getItem(slot));
                    }
                }
                for (Player viewer : target.getTrackedBy()) {
                    viewer.sendEquipmentChange(target, real);
                }
                Location at = target.getLocation().add(0, 1, 0);
                List<Player> viewers = Fx.viewers(at);
                ParticleSpec.of(Particle.FLASH).spawn(at, viewers);
                ParticleSpec.of(Particle.END_ROD, 20, 0.3, 0.12).spawn(at, viewers);
                def.sound("return", SoundSpec.of("block.respawn_anchor.charge", 1.0f, 1.4f)).play(at);
                if (reason != EndReason.QUIT) {
                    plugin.timedStates().clear(target, STATE);
                }
            }
            plugin.tempEntities().remove(body);
        }
    }
}
