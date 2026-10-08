package com.cosmicsmp.feature.ability.impl.cosmic;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.Keys;
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
import com.destroystokyo.paper.event.player.PlayerStartSpectatingEntityEvent;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Cosmic Primary #3 - Astral Phase: knocks the target's spirit out of its body.
 * <p>
 * <b>Players:</b> the body stays behind as a Mannequin wearing their skin and armour; the player becomes a spirit
 * (spectator mode: intangible, can't fight, interact or be hurt, seen by others as a soul wisp). They return by
 * walking into - or clicking - their body, or are pulled back when the time runs out. A leash keeps the spirit
 * close to the body. Damage dealt to the empty body is stored and applied when the spirit returns (killing blows
 * credit the attacker); if the body "dies" the spirit is yanked back immediately.
 * <p>
 * <b>Mobs:</b> become soulless for a few seconds (no AI, extra damage taken).
 * <p>
 * <b>Crash safety:</b> the original game mode and body position are stored in a timed state before anything changes;
 * quit, reload, shutdown and crashes all restore the player (game mode + position) on the spot or on next join.
 */
public final class AstralPhase implements Ability, Listener {

    public static final String STATE = "astral_phase";
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    private final CosmicSMP plugin;
    /** body entity -> phase */
    private final Map<UUID, Phase> bodies = new HashMap<>();
    /** spirit player -> phase */
    private final Map<UUID, Phase> spirits = new HashMap<>();
    /** soulless mob -> effect */
    private final Map<UUID, Soulless> soulless = new HashMap<>();

    public AstralPhase(CosmicSMP plugin) {
        this.plugin = plugin;
        plugin.timedStates().register(STATE, new TimedStateService.Handler() {
            @Override
            public void resume(Player player, PlayerData.TimedState state, long remainingMillis) {
                restoreFromState(player, state);
            }

            @Override
            public void expire(Player player, PlayerData.TimedState state) {
                restoreFromState(player, state);
            }
        });
    }

    /** After a crash / restart: put the player back in their body with their original game mode. */
    private void restoreFromState(Player player, PlayerData.TimedState state) {
        if (spirits.containsKey(player.getUniqueId())) {
            return;
        }
        GameMode mode = parseMode(state.get("mode"));
        if (player.getGameMode() == GameMode.SPECTATOR && mode != GameMode.SPECTATOR) {
            player.setGameMode(mode);
        }
        Location body = location(state);
        if (body != null) {
            player.teleport(body);
        }
        player.setFallDistance(0);
        plugin.combat().setIntangible(player.getUniqueId(), false);
        plugin.timedStates().clear(player, STATE);
    }

    private static Location location(PlayerData.TimedState state) {
        try {
            World world = Bukkit.getWorld(state.get("world"));
            if (world == null) {
                return null;
            }
            return new Location(world, Double.parseDouble(state.get("x")), Double.parseDouble(state.get("y")),
                    Double.parseDouble(state.get("z")), Float.parseFloat(state.get("yaw")), 0);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static GameMode parseMode(String raw) {
        try {
            return raw == null ? GameMode.SURVIVAL : GameMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return GameMode.SURVIVAL;
        }
    }

    @Override
    public String id() {
        return "astral_phase";
    }

    @Override
    public CastResult cast(AbilityContext ctx) {
        LivingEntity target = Targeting.entityInSight(plugin, ctx.caster(), ctx.def().num("range", 20), 0.7,
                ctx.def().bool("players-only", false));
        if (target == null) {
            return CastResult.NO_TARGET;
        }
        if (spirits.containsKey(target.getUniqueId()) || soulless.containsKey(target.getUniqueId())
                || bodies.containsKey(target.getUniqueId())) {
            return CastResult.BLOCKED;
        }
        if (target instanceof Player player) {
            plugin.effects().start(new Phase(ctx.caster(), player, ctx.def()));
        } else if (target instanceof Mob mob) {
            plugin.effects().start(new Soulless(ctx.caster(), mob, ctx.def()));
        } else {
            return CastResult.NO_TARGET;
        }
        return CastResult.SUCCESS;
    }

    // ------------------------------------------------------------------ events

    /** Hits on the empty body are stored and applied to the spirit when it returns. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBodyDamage(EntityDamageEvent event) {
        Phase phase = bodies.get(event.getEntity().getUniqueId());
        if (phase == null) {
            return;
        }
        event.setCancelled(true);
        // only real attacks count: fire, lava, suffocation... never drain the spirit
        if (!(event instanceof EntityDamageByEntityEvent byEntity)) {
            event.getEntity().setFireTicks(0);
            return;
        }
        Player attacker = CombatService.attacker(byEntity.getDamager());
        if (attacker != null && attacker.equals(phase.target)) {
            return;
        }
        phase.hurtBody(event.getDamage(), attacker);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBodyCombust(org.bukkit.event.entity.EntityCombustEvent event) {
        if (bodies.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Soulless mobs take extra damage. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSoullessDamage(EntityDamageEvent event) {
        Soulless effect = soulless.get(event.getEntity().getUniqueId());
        if (effect != null) {
            event.setDamage(event.getDamage() * effect.def.num("body-damage-multiplier", 1.5));
        }
    }

    /** Spectator click on an entity: clicking your own body returns you; spectating anything else is blocked. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSpectate(PlayerStartSpectatingEntityEvent event) {
        Phase phase = spirits.get(event.getPlayer().getUniqueId());
        if (phase == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getNewSpectatorTarget().equals(phase.body)) {
            phase.returnToBody();
        }
    }

    /** No spectator-menu teleports / portals while phased (the leash handles everything else). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Phase phase = spirits.get(event.getPlayer().getUniqueId());
        if (phase != null && !phase.teleporting && event.getCause() != PlayerTeleportEvent.TeleportCause.PLUGIN) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventory(InventoryOpenEvent event) {
        if (spirits.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    /** Another plugin / staff changed the game mode: end the phase without fighting over it. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameMode(PlayerGameModeChangeEvent event) {
        Phase phase = spirits.get(event.getPlayer().getUniqueId());
        if (phase != null && !phase.changingMode && event.getNewGameMode() != GameMode.SPECTATOR) {
            phase.externalModeChange = true;
            phase.cancel(EndReason.CANCELLED);
        }
    }

    /** A soulless mob saved by a crash gets its AI back as soon as it loads. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (entity instanceof Mob mob && mob.getPersistentDataContainer().has(Keys.SOULLESS, PersistentDataType.BYTE)
                    && !soulless.containsKey(mob.getUniqueId())) {
                mob.getPersistentDataContainer().remove(Keys.SOULLESS);
                mob.setAI(true);
            }
        }
    }

    // ------------------------------------------------------------------ player phase

    private final class Phase extends TimedEffect {
        private final Player caster;
        private final Player target;
        private final AbilityDefinition def;
        private final ParticleSpec wisp;
        private final ParticleSpec tether;
        private final double leash;
        private final double returnRadius;
        private final int maxTicks;
        private Mannequin body;
        private Location origin;
        private GameMode originalMode;
        private double pendingDamage;
        private UUID lastAttacker;
        private boolean returned;
        boolean changingMode;
        boolean teleporting;
        boolean externalModeChange;

        Phase(Player caster, Player target, AbilityDefinition def) {
            super(def.integer("max-duration-seconds", 15) * 20);
            this.caster = caster;
            this.target = target;
            this.def = def;
            this.maxTicks = def.integer("max-duration-seconds", 15) * 20;
            this.wisp = def.particle("spirit", ParticleSpec.of(Particle.WHITE_ASH, 6, 0.3, 0.01));
            this.tether = def.particle("tether", ParticleSpec.dust("#c4b5fd", 0.8f));
            this.leash = def.num("leash-radius", 10);
            this.returnRadius = def.num("return-radius", 1.4);
        }

        @Override
        public UUID owner() {
            return target.getUniqueId();
        }

        @Override
        protected void onStart() {
            origin = target.getLocation();
            originalMode = target.getGameMode();

            // 1. persist everything needed to undo the phase BEFORE touching the player (crash safety)
            PlayerData.TimedState state = plugin.timedStates().set(target, STATE, maxTicks * 50L + 5000L);
            state.put("mode", originalMode.name());
            state.put("world", origin.getWorld().getName());
            state.put("x", origin.getX());
            state.put("y", origin.getY());
            state.put("z", origin.getZ());
            state.put("yaw", origin.getYaw());

            // 2. the empty body
            body = plugin.tempEntities().spawn(origin, Mannequin.class, m -> {
                m.setProfile(ResolvableProfile.resolvableProfile(target.getPlayerProfile()));
                m.setImmovable(true);
                m.setCollidable(false);
                m.setGravity(true);
                m.setCustomNameVisible(false);
                EntityEquipment equipment = m.getEquipment();
                for (EquipmentSlot slot : ARMOR) {
                    ItemStack item = target.getInventory().getItem(slot);
                    equipment.setItem(slot, item == null ? null : item.clone());
                    equipment.setDropChance(slot, 0f);
                }
                equipment.setItemInMainHand(target.getInventory().getItemInMainHand().clone());
                equipment.setDropChance(EquipmentSlot.HAND, 0f);
            });
            updateLabel();
            bodies.put(body.getUniqueId(), this);
            spirits.put(target.getUniqueId(), this);
            plugin.combat().setIntangible(target.getUniqueId(), true);

            // 3. the spirit leaves the body
            if (target.isInsideVehicle()) {
                target.leaveVehicle();
            }
            setMode(GameMode.SPECTATOR);
            Vector away = target.getLocation().toVector().subtract(caster.getLocation().toVector()).setY(0);
            Vector push = Fx.horizontal(away.lengthSquared() < 1.0E-3 ? origin.getDirection().multiply(-1) : away);
            Location out = origin.clone().add(push.multiply(def.num("push-distance", 3.0))).add(0, 1.2, 0);
            out.setDirection(origin.toVector().subtract(out.toVector()));
            teleport(out);

            List<Player> viewers = Fx.viewers(origin);
            Location chest = origin.clone().add(0, 1, 0);
            ParticleSpec.of(Particle.SOUL, 30, 0.4, 0.06).spawn(chest, viewers);
            ParticleSpec.of(Particle.REVERSE_PORTAL, 40, 0.5, 0.1).spawn(chest, viewers);
            ParticleSpec.of(Particle.FLASH).spawn(chest, viewers);
            Fx.line(ParticleSpec.dust("#c4b5fd", 1.2f), chest, out, 0.4, viewers);
            def.sound("cast", SoundSpec.of("entity.vex.charge", 1.2f, 0.6f)).play(origin);
            SoundSpec.of("block.beacon.deactivate", 1.0f, 0.5f).play(origin);
            plugin.messages().send(target, "abilities.astral-phase-victim", Placeholders.of("caster", caster.getName()));
        }

        private void setMode(GameMode mode) {
            changingMode = true;
            try {
                target.setGameMode(mode);
            } finally {
                changingMode = false;
            }
        }

        private void teleport(Location location) {
            teleporting = true;
            try {
                target.teleport(location);
            } finally {
                teleporting = false;
            }
        }

        private void updateLabel() {
            if (body == null || !body.isValid()) {
                return;
            }
            double health = Math.max(0, target.getHealth() - pendingDamage);
            body.setDescription(Text.parse(def.string("body-label", "<#c4b5fd>Empty Vessel <#ff5c7a>❤ {health}")
                    .replace("{health}", String.format(Locale.ROOT, "%.1f", health / 2.0))
                    .replace("{player}", target.getName())));
        }

        void hurtBody(double raw, Player attacker) {
            pendingDamage += raw * def.num("body-damage-multiplier", 1.5);
            if (attacker != null) {
                lastAttacker = attacker.getUniqueId();
                plugin.combat().record(target.getUniqueId(), attacker.getUniqueId());
            }
            body.playHurtAnimation(0);
            Location at = body.getLocation().add(0, 1, 0);
            ParticleSpec.of(Particle.SOUL, 8, 0.3, 0.05).spawn(at, Fx.viewers(at));
            SoundSpec.of("entity.player.hurt", 1.0f, 0.7f).play(at);
            updateLabel();
            if (pendingDamage >= target.getHealth() + target.getAbsorptionAmount()) {
                returnToBody(); // the body is dying: the spirit is dragged back to share its fate
            }
        }

        void returnToBody() {
            if (!isFinished()) {
                returned = true;
                finish();
            }
        }

        @Override
        protected void onTick() {
            if (!target.isOnline() || body == null || !body.isValid()) {
                cancel(target.isOnline() ? EndReason.CANCELLED : EndReason.QUIT);
                return;
            }
            Location spirit = target.getLocation().add(0, 1, 0);
            Location vessel = body.getLocation().add(0, 1, 0);
            boolean sameWorld = spirit.getWorld().equals(vessel.getWorld());
            double distance = sameWorld ? spirit.distance(vessel) : Double.MAX_VALUE;

            // leash: the spirit can't wander off (spectators fly through walls)
            if (distance > leash) {
                Location pulled = sameWorld
                        ? vessel.clone().add(spirit.toVector().subtract(vessel.toVector()).normalize().multiply(leash * 0.9))
                        : vessel.clone();
                pulled.setDirection(target.getLocation().getDirection());
                teleport(pulled.subtract(0, 1, 0));
                SoundSpec.of("block.chain.break", 0.7f, 0.6f).play(target);
            }

            List<Player> viewers = Fx.viewers(spirit);
            if (age % 2 == 0) {
                wisp.spawn(spirit, viewers);
                ParticleSpec.of(Particle.SOUL).spawn(spirit, viewers);
            }
            if (age % 4 == 0 && sameWorld) {
                Fx.line(tether, spirit, vessel, 0.6, viewers);
            }
            if (age % 10 == 0) {
                ParticleSpec.of(Particle.SOUL, 2, 0.3, 0.01).spawn(vessel, viewers);
                plugin.hud().hold(target, 700);
                plugin.messages().actionBar(target, "abilities.astral-phase-remaining", Placeholders.of(
                        "time", Math.max(0, (maxTicks - age) / 20),
                        "distance", String.format(Locale.ROOT, "%.1f", Math.max(0, distance - returnRadius))));
            }
            if (age > 10 && distance <= returnRadius) {
                returnToBody();
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            spirits.remove(target.getUniqueId());
            Location bodyLocation = body != null && body.isValid() ? body.getLocation() : origin;
            if (body != null) {
                bodies.remove(body.getUniqueId());
            }
            plugin.combat().setIntangible(target.getUniqueId(), false);
            plugin.tempEntities().remove(body);

            if (target.isOnline()) {
                if (!externalModeChange && target.getGameMode() == GameMode.SPECTATOR) {
                    setMode(originalMode == GameMode.SPECTATOR ? GameMode.SURVIVAL : originalMode);
                }
                if (bodyLocation != null && !externalModeChange) {
                    Location back = bodyLocation.clone();
                    back.setYaw(target.getLocation().getYaw());
                    back.setPitch(target.getLocation().getPitch());
                    teleport(back);
                }
                target.setFallDistance(0);
                plugin.timedStates().clear(target, STATE);

                Location at = target.getLocation().add(0, 1, 0);
                List<Player> viewers = Fx.viewers(at);
                ParticleSpec.of(Particle.FLASH).spawn(at, viewers);
                ParticleSpec.of(Particle.END_ROD, 20, 0.3, 0.12).spawn(at, viewers);
                def.sound("return", SoundSpec.of("block.respawn_anchor.charge", 1.0f, 1.4f)).play(at);
                plugin.hud().hold(target, 1500);
                plugin.messages().send(target, returned ? "abilities.astral-phase-returned" : "abilities.astral-phase-pulled",
                        Placeholders.empty());

                if (pendingDamage > 0 && reason != EndReason.SHUTDOWN) {
                    double damage = pendingDamage;
                    Player attacker = lastAttacker == null ? null : Bukkit.getPlayer(lastAttacker);
                    // next tick: the game mode switch must be fully applied before damage is accepted
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!target.isOnline() || target.isDead()) {
                            return;
                        }
                        if (attacker != null) {
                            plugin.combat().damage(attacker, target, damage);
                        } else {
                            target.damage(damage);
                        }
                    });
                }
            }
        }
    }

    // ------------------------------------------------------------------ mob variant

    private final class Soulless extends TimedEffect {
        private final Player caster;
        private final Mob mob;
        private final AbilityDefinition def;
        private boolean hadAi;

        Soulless(Player caster, Mob mob, AbilityDefinition def) {
            super(def.integer("mob-duration-seconds", 6) * 20);
            this.caster = caster;
            this.mob = mob;
            this.def = def;
        }

        @Override
        public UUID owner() {
            return mob.getUniqueId();
        }

        @Override
        protected void onStart() {
            hadAi = mob.hasAI();
            mob.getPersistentDataContainer().set(Keys.SOULLESS, PersistentDataType.BYTE, (byte) 1);
            mob.setAI(false);
            soulless.put(mob.getUniqueId(), this);
            Location at = mob.getLocation().add(0, mob.getHeight() / 2, 0);
            List<Player> viewers = Fx.viewers(at);
            ParticleSpec.of(Particle.SOUL, 25, 0.4, 0.06).spawn(at, viewers);
            ParticleSpec.of(Particle.FLASH).spawn(at, viewers);
            def.sound("cast", SoundSpec.of("entity.vex.charge", 1.2f, 0.6f)).play(at);
        }

        @Override
        protected void onTick() {
            if (!mob.isValid()) {
                finish();
                return;
            }
            if (age % 3 == 0) {
                Location at = mob.getLocation().add(0, mob.getHeight() + 0.3 + Math.sin(age * 0.2) * 0.2, 0);
                List<Player> viewers = Fx.viewers(at);
                ParticleSpec.of(Particle.SOUL).spawn(at, viewers);
                ParticleSpec.dust("#c4b5fd", 0.9f).spawn(at, viewers);
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            soulless.remove(mob.getUniqueId());
            if (mob.isValid()) {
                mob.getPersistentDataContainer().remove(Keys.SOULLESS);
                mob.setAI(hadAi);
                Location at = mob.getLocation().add(0, mob.getHeight() / 2, 0);
                ParticleSpec.of(Particle.END_ROD, 12, 0.3, 0.08).spawn(at, Fx.viewers(at));
                def.sound("return", SoundSpec.of("block.respawn_anchor.charge", 1.0f, 1.4f)).play(at);
            }
        }
    }
}
