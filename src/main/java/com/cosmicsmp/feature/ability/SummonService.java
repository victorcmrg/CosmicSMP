package com.cosmicsmp.feature.ability;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Creatures summoned by abilities (Space Warden, Alien Dragon mount, Giant Bee...).
 * <ul>
 *     <li>Spawned through {@link com.cosmicsmp.core.hook.HookManager} so a MythicMobs mob or a BetterModel model can
 *     replace the vanilla placeholder purely from config.</li>
 *     <li>Never persistent, never drop loot/xp, never burn, never target or hurt their owner.</li>
 *     <li>Removed when their lifetime ends, when the owner dies/quits, on reload and on shutdown.</li>
 * </ul>
 */
public final class SummonService implements Listener {

    public static final class Summon {
        public final UUID owner;
        public final Entity entity;
        public final long expiresAt;
        public double damageOverride;

        Summon(UUID owner, Entity entity, long expiresAt) {
            this.owner = owner;
            this.entity = entity;
            this.expiresAt = expiresAt;
        }
    }

    private final CosmicSMP plugin;
    private final Map<UUID, Summon> summons = new HashMap<>();
    private BukkitTask task;

    public SummonService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 10L, 10L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        removeAll();
    }

    /**
     * @param reference vanilla type name or {@code mythic:Id}
     * @param modelId   BetterModel model id ("" for vanilla look)
     */
    public Summon spawn(Player owner, Location location, String reference, EntityType fallback, int lifetimeTicks,
                        String modelId, double scale, Consumer<Entity> configure) {
        Entity entity = plugin.hooks().spawnEntity(reference, location, fallback, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(Keys.SUMMON_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
            e.setSilent(false);
            if (e instanceof Mob mob) {
                mob.setRemoveWhenFarAway(false);
                mob.setCanPickupItems(false);
            }
            if (e instanceof LivingEntity living && scale > 0 && scale != 1.0) {
                AttributeInstance attr = living.getAttribute(Attribute.SCALE);
                if (attr != null) {
                    attr.setBaseValue(scale);
                }
            }
            if (configure != null) {
                configure.accept(e);
            }
        });
        plugin.tempEntities().track(entity);
        plugin.hooks().applyModel(entity, modelId);
        Summon summon = new Summon(owner.getUniqueId(), entity, System.currentTimeMillis() + lifetimeTicks * 50L);
        summons.put(entity.getUniqueId(), summon);
        return summon;
    }

    public UUID ownerOf(Entity entity) {
        Summon summon = summons.get(entity.getUniqueId());
        return summon == null ? null : summon.owner;
    }

    public Summon get(Entity entity) {
        return summons.get(entity.getUniqueId());
    }

    public boolean ownsAny(UUID owner, EntityType type) {
        for (Summon summon : summons.values()) {
            if (summon.owner.equals(owner) && summon.entity.getType() == type && summon.entity.isValid()) {
                return true;
            }
        }
        return false;
    }

    public void remove(Entity entity) {
        summons.remove(entity.getUniqueId());
        plugin.tempEntities().remove(entity);
    }

    public void removeOwnedBy(UUID owner) {
        List<Summon> owned = new ArrayList<>();
        for (Summon summon : summons.values()) {
            if (summon.owner.equals(owner)) {
                owned.add(summon);
            }
        }
        owned.forEach(s -> remove(s.entity));
    }

    public int count() {
        return summons.size();
    }

    private void sweep() {
        long now = System.currentTimeMillis();
        List<Summon> expired = new ArrayList<>();
        for (Summon summon : summons.values()) {
            if (!summon.entity.isValid() || summon.expiresAt <= now) {
                expired.add(summon);
            }
        }
        expired.forEach(s -> remove(s.entity));
    }

    private void removeAll() {
        for (Summon summon : new ArrayList<>(summons.values())) {
            remove(summon.entity);
        }
        summons.clear();
    }

    // ------------------------------------------------------------------ behaviour guards

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        Summon summon = summons.get(event.getEntity().getUniqueId());
        if (summon == null || event.getTarget() == null) {
            return;
        }
        UUID targetOwner = ownerOf(event.getTarget());
        if (event.getTarget().getUniqueId().equals(summon.owner) || summon.owner.equals(targetOwner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSummonDamage(EntityDamageByEntityEvent event) {
        Summon summon = summons.get(event.getDamager().getUniqueId());
        if (summon == null) {
            return;
        }
        if (event.getEntity().getUniqueId().equals(summon.owner) || summon.owner.equals(ownerOf(event.getEntity()))) {
            event.setCancelled(true);
            return;
        }
        if (summon.damageOverride > 0) {
            event.setDamage(summon.damageOverride);
        }
        plugin.combat().record(event.getEntity().getUniqueId(), summon.owner);
    }

    @EventHandler(ignoreCancelled = true)
    public void onOwnerHitsSummon(EntityDamageByEntityEvent event) {
        Summon summon = summons.get(event.getEntity().getUniqueId());
        Player attacker = CombatService.attacker(event.getDamager());
        if (summon != null && attacker != null && attacker.getUniqueId().equals(summon.owner)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (summons.containsKey(event.getEntity().getUniqueId())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            summons.remove(event.getEntity().getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (summons.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (summons.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onOwnerDeath(PlayerDeathEvent event) {
        removeOwnedBy(event.getEntity().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeOwnedBy(event.getPlayer().getUniqueId());
    }
}
