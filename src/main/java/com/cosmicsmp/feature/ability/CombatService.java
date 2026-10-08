package com.cosmicsmp.feature.ability;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.Keys;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.task.TimedEffect;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Shared combat rules for every ability: who can be hit, how damage is applied (fires normal damage events, so
 * WorldGuard / PvP toggles / other plugins still apply), kill credit for summons, stuns and knockback.
 */
public final class CombatService implements Listener {

    private record Hit(UUID attacker, long at) {
    }

    private final CosmicSMP plugin;
    private final Map<UUID, Hit> lastHits = new HashMap<>();
    private final Set<UUID> intangible = new HashSet<>();
    private final Map<String, DamageType> damageTypes = new HashMap<>();
    /** entity -> epoch millis when its longest running stun ends (overlapping stuns don't cut each other short). */
    private final Map<UUID, Long> stunnedUntil = new HashMap<>();

    public CombatService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    /** Valid ability target for this caster? */
    public boolean canTarget(Player caster, Entity entity) {
        if (!(entity instanceof LivingEntity living) || !living.isValid() || living.isDead() || entity.equals(caster)) {
            return false;
        }
        if (entity instanceof ArmorStand || intangible.contains(entity.getUniqueId())
                || entity.getPersistentDataContainer().has(Keys.MERCHANT, PersistentDataType.STRING)) {
            return false;
        }
        UUID summonOwner = plugin.summons().ownerOf(entity);
        if (summonOwner != null && summonOwner.equals(caster.getUniqueId())) {
            return false;
        }
        if (entity instanceof Player target) {
            GameMode mode = target.getGameMode();
            return plugin.settings().targetPlayers && mode != GameMode.CREATIVE && mode != GameMode.SPECTATOR;
        }
        return plugin.settings().targetMobs && !entity.isInvulnerable();
    }

    public boolean damage(Player caster, LivingEntity target, double amount) {
        return damage(caster, target, amount, null);
    }

    /**
     * Applies ability damage attributed to the caster. Returns true if the target actually took damage
     * (false when another plugin cancelled it, e.g. a protected region).
     */
    public boolean damage(Player caster, LivingEntity target, double amount, String damageTypeOverride) {
        if (amount <= 0 || !target.isValid() || target.isDead()) {
            return false;
        }
        DamageType type = damageType(damageTypeOverride != null ? damageTypeOverride : plugin.settings().damageType);
        DamageSource source = DamageSource.builder(type).withCausingEntity(caster).withDirectEntity(caster).build();
        record(target.getUniqueId(), caster.getUniqueId());
        if (plugin.settings().ignoreInvulnerability) {
            target.setNoDamageTicks(0);
        }
        double before = target.getHealth() + target.getAbsorptionAmount();
        target.damage(amount, source);
        return target.isDead() || target.getHealth() + target.getAbsorptionAmount() < before;
    }

    private DamageType damageType(String name) {
        return damageTypes.computeIfAbsent(name.toLowerCase(Locale.ROOT), key -> {
            DamageType type = null;
            try {
                type = RegistryAccess.registryAccess().getRegistry(RegistryKey.DAMAGE_TYPE).get(Key.key(key));
            } catch (RuntimeException ignored) {
                // invalid key
            }
            if (type == null) {
                plugin.getLogger().warning("Unknown damage type '" + key + "', using minecraft:magic");
                type = DamageType.MAGIC;
            }
            return type;
        });
    }

    public void record(UUID victim, UUID attacker) {
        lastHits.put(victim, new Hit(attacker, System.currentTimeMillis()));
    }

    /** The player credited for this death: vanilla killer first, then the last ability/summon hit in the window. */
    public Player resolveKiller(Player victim) {
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            return killer;
        }
        Hit hit = lastHits.get(victim.getUniqueId());
        if (hit == null || System.currentTimeMillis() - hit.at() > plugin.settings().killCreditMillis) {
            return null;
        }
        Player attacker = plugin.getServer().getPlayer(hit.attacker());
        return attacker == null || attacker.equals(victim) ? null : attacker;
    }

    public void clearHits(UUID victim) {
        lastHits.remove(victim);
    }

    public void setIntangible(UUID uuid, boolean value) {
        if (value) {
            intangible.add(uuid);
        } else {
            intangible.remove(uuid);
        }
    }

    public boolean isIntangible(UUID uuid) {
        return intangible.contains(uuid);
    }

    public void knockback(LivingEntity target, Location from, double horizontal, double vertical) {
        Vector push = target.getLocation().toVector().subtract(from.toVector()).setY(0);
        if (push.lengthSquared() < 1.0E-4) {
            push = new Vector(Math.random() - 0.5, 0, Math.random() - 0.5);
        }
        push.normalize().multiply(horizontal).setY(vertical);
        target.setVelocity(push);
    }

    /** Stuns a living entity: heavy slowness, no jumping, a ring of stars over the head. */
    public void stun(LivingEntity target, int ticks) {
        if (ticks <= 0 || !target.isValid()) {
            return;
        }
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 6, false, false, true));
        AttributeInstance jump = target.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(Keys.STUN);
            // transient: never written to disk, so a crash can't leave a player unable to jump
            jump.addTransientModifier(new AttributeModifier(Keys.STUN, -1.0, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        }
        stunnedUntil.merge(target.getUniqueId(), System.currentTimeMillis() + ticks * 50L, Math::max);
        plugin.effects().start(new StunEffect(this, target, ticks));
    }

    private void stunEnded(LivingEntity target) {
        Long until = stunnedUntil.get(target.getUniqueId());
        if (until == null || until <= System.currentTimeMillis() + 50 || !target.isValid()) {
            stunnedUntil.remove(target.getUniqueId());
            clearStun(target);
        }
    }

    private static void clearStun(LivingEntity target) {
        AttributeInstance jump = target.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null) {
            jump.removeModifier(Keys.STUN);
        }
    }

    private static final class StunEffect extends TimedEffect {
        private static final ParticleSpec STARS = ParticleSpec.of(Particle.CRIT);
        private final CombatService combat;
        private final LivingEntity target;

        StunEffect(CombatService combat, LivingEntity target, int ticks) {
            super(ticks);
            this.combat = combat;
            this.target = target;
        }

        @Override
        public UUID owner() {
            return target.getUniqueId();
        }

        @Override
        protected void onTick() {
            if (!target.isValid()) {
                finish();
                return;
            }
            if (age % 4 == 0) {
                Location head = target.getEyeLocation().add(0, 0.55, 0);
                double angle = age * 0.35;
                java.util.List<Player> viewers = Fx.viewers(head);
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * (Math.PI * 2 / 3);
                    STARS.spawn(head.getX() + Math.cos(a) * 0.45, head.getY(), head.getZ() + Math.sin(a) * 0.45, viewers);
                }
            }
        }

        @Override
        protected void onEnd(EndReason reason) {
            combat.stunEnded(target);
        }
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker != null && event.getEntity() instanceof Player victim && !attacker.equals(victim)) {
            record(victim.getUniqueId(), attacker.getUniqueId());
        }
    }

    /** Intangible players (astral spirits) can't deal damage. */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIntangibleAttack(EntityDamageByEntityEvent event) {
        Player attacker = attacker(event.getDamager());
        if (attacker != null && intangible.contains(attacker.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    public static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile) {
            ProjectileSource shooter = projectile.getShooter();
            if (shooter instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearStun(event.getPlayer()); // safety net (transient modifiers are not saved anyway)
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastHits.remove(event.getPlayer().getUniqueId());
        intangible.remove(event.getPlayer().getUniqueId());
        stunnedUntil.remove(event.getPlayer().getUniqueId());
    }
}
