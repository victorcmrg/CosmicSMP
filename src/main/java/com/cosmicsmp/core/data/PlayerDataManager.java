package com.cosmicsmp.core.data;

import com.cosmicsmp.CosmicSMP;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Loads, caches and saves {@link PlayerData}.
 * <ul>
 *     <li>Data is loaded on the async pre-login thread, so it is ready before the player joins and the main thread never
 *     blocks on disk.</li>
 *     <li>Only online players stay cached (RAM stays flat no matter how many players ever joined).</li>
 *     <li>All writes go through one ordered IO thread; JSON is serialised on the main thread (consistent snapshot).</li>
 *     <li>Important changes (purchases, brightness, bans) are written within one tick ({@link #saveSoon}); everything else
 *     on the autosave interval; on shutdown every cached profile is flushed synchronously.</li>
 * </ul>
 */
public final class PlayerDataManager implements Listener {

    private final CosmicSMP plugin;
    private final Path folder;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();
    private final Set<UUID> pendingLogins = ConcurrentHashMap.newKeySet();
    private final Set<UUID> flushQueue = new HashSet<>();
    private ExecutorService io;
    private BukkitTask autosave;
    private boolean flushScheduled;

    public PlayerDataManager(CosmicSMP plugin) {
        this.plugin = plugin;
        this.folder = plugin.getDataFolder().toPath().resolve("data").resolve("players");
    }

    public void start() {
        io = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "CosmicSMP-IO");
            thread.setDaemon(false);
            return thread;
        });
        Bukkit.getPluginManager().registerEvents(this, plugin);
        long period = plugin.settings().autosaveMinutes * 60L * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(plugin, this::autosave, period, period);
        for (Player player : Bukkit.getOnlinePlayers()) {
            get(player);
        }
    }

    private Path file(UUID uuid) {
        return folder.resolve(uuid + ".json");
    }

    public PlayerData get(Player player) {
        PlayerData data = get(player.getUniqueId(), player.getName());
        data.name = player.getName();
        return data;
    }

    private PlayerData get(UUID uuid, String name) {
        return cache.computeIfAbsent(uuid, id -> loadFromDisk(id, name));
    }

    public PlayerData getIfLoaded(UUID uuid) {
        return cache.get(uuid);
    }

    private PlayerData loadFromDisk(UUID uuid, String name) {
        Path file = file(uuid);
        if (JsonStore.exists(file)) {
            try {
                PlayerData data = JsonStore.read(file, PlayerData.class);
                if (data != null) {
                    data.normalize(uuid);
                    if (name != null) {
                        data.name = name;
                    }
                    return data;
                }
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Player file for " + uuid + " is unreadable; it was moved aside "
                        + "and a fresh profile was created. " + ex.getMessage());
                JsonStore.quarantine(file);
            }
        }
        return new PlayerData(uuid, name, plugin.settings().brightnessStart);
    }

    /** Marks data to be written within the next tick (coalesces many changes in one tick into one write). */
    public void saveSoon(PlayerData data) {
        data.dirty = true;
        flushQueue.add(data.uuid);
        if (!flushScheduled && plugin.isEnabled()) {
            flushScheduled = true;
            Bukkit.getScheduler().runTask(plugin, this::flushQueue);
        }
    }

    private void flushQueue() {
        flushScheduled = false;
        List<UUID> ids = new ArrayList<>(flushQueue);
        flushQueue.clear();
        for (UUID id : ids) {
            PlayerData data = cache.get(id);
            if (data != null && data.dirty) {
                saveAsync(data, false);
            }
        }
    }

    private void saveAsync(PlayerData data, boolean evictAfter) {
        data.prune(System.currentTimeMillis(), plugin.settings().antiFarmMillis);
        String json = JsonStore.GSON.toJson(data);
        data.dirty = false;
        UUID uuid = data.uuid;
        Path target = file(uuid);
        io.execute(() -> {
            try {
                JsonStore.write(target, json);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player data " + uuid, ex);
                return;
            }
            if (evictAfter && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (Bukkit.getPlayer(uuid) == null && !pendingLogins.contains(uuid)) {
                        PlayerData cached = cache.get(uuid);
                        if (cached != null && !cached.dirty) {
                            cache.remove(uuid);
                        }
                    }
                });
            }
        });
    }

    private void autosave() {
        for (PlayerData data : cache.values()) {
            if (data.dirty) {
                saveAsync(data, Bukkit.getPlayer(data.uuid) == null);
            }
        }
    }

    public void saveAll() {
        for (PlayerData data : cache.values()) {
            saveAsync(data, false);
        }
    }

    /** Loads a profile for read-only use (cached or from disk), completing on the main thread. */
    public CompletableFuture<PlayerData> load(UUID uuid) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<PlayerData> future = new CompletableFuture<>();
        io.execute(() -> {
            PlayerData data = loadFromDisk(uuid, null);
            Bukkit.getScheduler().runTask(plugin, () -> {
                PlayerData live = cache.get(uuid);
                future.complete(live != null ? live : data);
            });
        });
        return future;
    }

    /** Edits a profile whether the player is online or not, then saves it. Runs the action on the main thread. */
    public void edit(UUID uuid, Consumer<PlayerData> action) {
        PlayerData cached = cache.get(uuid);
        if (cached != null) {
            action.accept(cached);
            saveSoon(cached);
            return;
        }
        load(uuid).thenAccept(data -> {
            action.accept(data);
            if (cache.containsKey(uuid)) {
                saveSoon(data);
            } else {
                saveAsync(data, false);
            }
        });
    }

    public boolean hasProfile(UUID uuid) {
        return cache.containsKey(uuid) || JsonStore.exists(file(uuid));
    }

    public int cachedCount() {
        return cache.size();
    }

    public void shutdown() {
        if (autosave != null) {
            autosave.cancel();
        }
        if (io != null) {
            io.shutdown();
            try {
                if (!io.awaitTermination(15, TimeUnit.SECONDS)) {
                    plugin.getLogger().warning("IO thread did not finish in time; writing remaining data synchronously.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }
        long now = System.currentTimeMillis();
        for (PlayerData data : cache.values()) {
            try {
                data.prune(now, plugin.settings().antiFarmMillis);
                JsonStore.write(file(data.uuid), JsonStore.GSON.toJson(data));
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save player data " + data.uuid + " on shutdown", ex);
            }
        }
        cache.clear();
    }

    // ------------------------------------------------------------------ lifecycle events

    @EventHandler(priority = EventPriority.LOW)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            return;
        }
        UUID uuid = event.getUniqueId();
        pendingLogins.add(uuid);
        get(uuid, event.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPreLoginResult(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            pendingLogins.remove(event.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        pendingLogins.remove(player.getUniqueId());
        PlayerData data = get(player);
        data.lastSeen = System.currentTimeMillis();
        data.dirty = true;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        PlayerData data = cache.get(event.getPlayer().getUniqueId());
        if (data != null) {
            data.lastSeen = System.currentTimeMillis();
            saveAsync(data, true);
        }
    }
}
