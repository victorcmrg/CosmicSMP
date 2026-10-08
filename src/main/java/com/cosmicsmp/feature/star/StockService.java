package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.GlobalData;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

/**
 * Global stock per star. A star's first purchase by a player consumes one unit and permanently "discovers" the star
 * for that player; re-buying after death never consumes stock and is allowed even at 0 stock.
 * Optional timed restock uses an absolute timestamp, so downtime is caught up after restarts/crashes.
 */
public final class StockService {

    public static final int UNLIMITED = -1;

    private final CosmicSMP plugin;
    private BukkitTask task;

    public StockService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::restockTick, 200L, 1200L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
    }

    public int stock(StarDefinition star) {
        GlobalData data = plugin.global().data();
        Integer value = data.stock.get(star.id());
        if (value == null) {
            value = star.defaultStock();
            data.stock.put(star.id(), value);
            plugin.global().markDirty();
        }
        return value;
    }

    public boolean inStock(StarDefinition star) {
        int stock = stock(star);
        return stock == UNLIMITED || stock > 0;
    }

    public void consume(StarDefinition star) {
        int stock = stock(star);
        if (stock > 0) {
            set(star.id(), stock - 1);
        }
    }

    public void set(String starId, int amount) {
        plugin.global().data().stock.put(starId, amount < 0 ? UNLIMITED : amount);
        plugin.global().markDirty();
    }

    public void reset(StarDefinition star) {
        set(star.id(), star.defaultStock());
    }

    public String display(StarDefinition star) {
        int stock = stock(star);
        return stock == UNLIMITED ? plugin.messages().raw("format.unlimited", "∞") : String.valueOf(stock);
    }

    private void restockTick() {
        Settings s = plugin.settings();
        if (!s.restockEnabled) {
            return;
        }
        GlobalData data = plugin.global().data();
        long now = System.currentTimeMillis();
        if (data.lastRestock <= 0) {
            data.lastRestock = now;
            plugin.global().markDirty();
            return;
        }
        long cycles = (now - data.lastRestock) / s.restockIntervalMillis;
        if (cycles <= 0) {
            return;
        }
        data.lastRestock += cycles * s.restockIntervalMillis;
        for (StarDefinition star : plugin.stars().all()) {
            int max = star.defaultStock();
            int current = stock(star);
            if (max < 0 || current < 0 || current >= max) {
                continue;
            }
            data.stock.put(star.id(), (int) Math.min(max, current + cycles * s.restockAmount));
        }
        plugin.global().markDirty();
        plugin.messages().broadcast("stock.restocked", com.cosmicsmp.core.text.Placeholders.empty());
    }
}
