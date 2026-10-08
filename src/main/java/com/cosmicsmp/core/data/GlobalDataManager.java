package com.cosmicsmp.core.data;

import com.cosmicsmp.CosmicSMP;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/** Loads / saves {@code data/global.json}. Saves are debounced (one write per second at most) and async. */
public final class GlobalDataManager {

    private final CosmicSMP plugin;
    private final Path file;
    private GlobalData data = new GlobalData();
    private boolean saveScheduled;
    private CompletableFuture<Void> lastWrite = CompletableFuture.completedFuture(null);

    public GlobalDataManager(CosmicSMP plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("data").resolve("global.json");
    }

    public void load() {
        try {
            GlobalData loaded = JsonStore.read(file, GlobalData.class);
            data = loaded == null ? new GlobalData() : loaded;
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "global.json is unreadable; moved aside and starting fresh. " + ex.getMessage());
            JsonStore.quarantine(file);
            data = new GlobalData();
        }
        data.normalize();
    }

    public GlobalData data() {
        return data;
    }

    /** Thread-safe: may be called from the async login thread. */
    public void markDirty() {
        synchronized (this) {
            if (saveScheduled || !plugin.isEnabled()) {
                return;
            }
            saveScheduled = true;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::saveAsync, 20L);
    }

    private void saveAsync() {
        synchronized (this) {
            saveScheduled = false;
        }
        String json = JsonStore.GSON.toJson(data);
        lastWrite = lastWrite.thenRunAsync(() -> {
            try {
                JsonStore.write(file, json);
            } catch (IOException ex) {
                plugin.getLogger().log(Level.SEVERE, "Could not save global.json", ex);
            }
        });
    }

    public void saveSync() {
        lastWrite.join();
        try {
            JsonStore.write(file, JsonStore.GSON.toJson(data));
        } catch (IOException ex) {
            plugin.getLogger().log(Level.SEVERE, "Could not save global.json on shutdown", ex);
        }
    }
}
