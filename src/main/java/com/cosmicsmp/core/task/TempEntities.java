package com.cosmicsmp.core.task;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.Keys;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Registry of every temporary entity the plugin creates (display models, summons, mounts, holograms).
 * <p>
 * Crash safety: temporary entities are spawned with {@code persistent=false}, so the server never writes them to
 * disk - a crash cannot leave orphan mobs/displays behind. As a second line of defence they carry a PDC tag and any
 * tagged entity that ever gets loaded from disk is removed.
 */
public final class TempEntities implements Listener {

    private final CosmicSMP plugin;
    private final Map<UUID, Entity> tracked = new HashMap<>();
    private BukkitTask sweeper;

    public TempEntities(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        sweeper = Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 100L, 100L);
    }

    public <T extends Entity> T spawn(Location location, Class<T> type, Consumer<? super T> configure) {
        T entity = location.getWorld().spawn(location, type, e -> {
            mark(e);
            if (configure != null) {
                configure.accept(e);
            }
        });
        tracked.put(entity.getUniqueId(), entity);
        return entity;
    }

    /** Registers an entity created elsewhere (e.g. by a hook) as temporary. */
    public <T extends Entity> T track(T entity) {
        mark(entity);
        tracked.put(entity.getUniqueId(), entity);
        return entity;
    }

    private static void mark(Entity entity) {
        entity.setPersistent(false);
        entity.getPersistentDataContainer().set(Keys.TEMP_ENTITY, PersistentDataType.BYTE, (byte) 1);
    }

    public static boolean isTemp(Entity entity) {
        return entity.getPersistentDataContainer().has(Keys.TEMP_ENTITY, PersistentDataType.BYTE);
    }

    public void remove(Entity entity) {
        if (entity == null) {
            return;
        }
        tracked.remove(entity.getUniqueId());
        plugin.hooks().removeModel(entity);
        if (entity.isValid()) {
            entity.eject();
            entity.remove();
        }
    }

    public int size() {
        return tracked.size();
    }

    private void sweep() {
        tracked.values().removeIf(entity -> !entity.isValid());
    }

    public void removeAll() {
        if (sweeper != null) {
            sweeper.cancel();
        }
        Iterator<Entity> it = tracked.values().iterator();
        while (it.hasNext()) {
            Entity entity = it.next();
            it.remove();
            plugin.hooks().removeModel(entity);
            if (entity.isValid()) {
                entity.eject();
                entity.remove();
            }
        }
    }

    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            if (isTemp(entity) && !tracked.containsKey(entity.getUniqueId())) {
                entity.remove();
            }
        }
    }
}
