package com.cosmicsmp.feature.brightness;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.api.event.BrightnessChangeEvent;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.GlobalData;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.fx.DisplayFx;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Brightness: the currency and life meter of the season. */
public final class BrightnessService {

    private final CosmicSMP plugin;

    public BrightnessService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public int get(Player player) {
        return plugin.players().get(player).brightness;
    }

    public int clamp(int value) {
        Settings s = plugin.settings();
        return Math.max(s.brightnessMin, Math.min(s.brightnessMax, value));
    }

    public int change(Player player, int delta, BrightnessChangeEvent.Cause cause) {
        return set(player, get(player) + delta, cause);
    }

    /** Sets brightness for an online player with events, feedback, perks and death-ban checks. */
    public int set(Player player, int value, BrightnessChangeEvent.Cause cause) {
        PlayerData data = plugin.players().get(player);
        int old = data.brightness;
        int next = clamp(value);
        if (next == old) {
            return old;
        }
        BrightnessChangeEvent event = new BrightnessChangeEvent(player, old, next, cause);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return old;
        }
        next = clamp(event.getNewValue());
        data.brightness = next;
        plugin.players().saveSoon(data);
        updateLeaderboard(data);
        plugin.perks().refresh(player);
        plugin.hud().invalidate(player);
        feedback(player, old, next, cause);
        if (next <= plugin.settings().brightnessMin && plugin.settings().banEnabled && cause != BrightnessChangeEvent.Cause.PURCHASE) {
            plugin.deathBans().ban(player);
        }
        return next;
    }

    /** Offline-safe setter used by admin commands (no feedback when the player is offline). */
    public void setAny(UUID uuid, int value, BrightnessChangeEvent.Cause cause) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) {
            set(online, value, cause);
            return;
        }
        plugin.players().edit(uuid, data -> {
            data.brightness = clamp(value);
            updateLeaderboard(data);
        });
    }

    public void updateLeaderboard(PlayerData data) {
        GlobalData global = plugin.global().data();
        global.leaderboard.put(data.uuid.toString(), new GlobalData.LeaderEntry(data.name, data.brightness, data.stats.kills));
        plugin.global().markDirty();
    }

    public List<GlobalData.LeaderEntry> top(int limit) {
        List<GlobalData.LeaderEntry> list = new ArrayList<>();
        for (Map.Entry<String, GlobalData.LeaderEntry> entry : plugin.global().data().leaderboard.entrySet()) {
            if (!plugin.global().data().bans.containsKey(entry.getKey())) {
                list.add(entry.getValue());
            }
        }
        list.sort(Comparator.comparingInt((GlobalData.LeaderEntry e) -> e.brightness).reversed()
                .thenComparing(Comparator.comparingInt((GlobalData.LeaderEntry e) -> e.kills).reversed()));
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    private void feedback(Player player, int old, int next, BrightnessChangeEvent.Cause cause) {
        if (cause == BrightnessChangeEvent.Cause.PURCHASE) {
            return; // purchases have their own feedback
        }
        int delta = next - old;
        Placeholders ph = placeholders(player, next).with("delta", (delta > 0 ? "+" : "") + delta).with("old", old);
        if (plugin.settings().brightnessTitles) {
            plugin.hud().hold(player, 2500);
            plugin.messages().send(player, delta > 0 ? "brightness.gained" : "brightness.lost", ph);
        }
        if (plugin.settings().brightnessHolograms) {
            String format = plugin.messages().raw(delta > 0 ? "format.popup-gain" : "format.popup-loss", "{delta} ✦");
            DisplayFx.popup(plugin, player, player.getEyeLocation().add(com.cosmicsmp.core.fx.Fx.horizontal(player.getLocation().getDirection()).multiply(1.6)),
                    Text.parse(ph.apply(format)), 30);
        }
    }

    public Placeholders placeholders(Player player, int value) {
        Settings s = plugin.settings();
        return Placeholders.of(
                "player", player.getName(),
                "brightness", value,
                "brightness_max", s.brightnessMax,
                "brightness_min", s.brightnessMin,
                "brightness_bar", bar(value),
                "brightness_color", color(value));
    }

    /** Coloured meter from min to max, e.g. ■■■■□□□□□□□. Fully configurable in messages.yml -> format.bar. */
    public String bar(int value) {
        Settings s = plugin.settings();
        String filled = plugin.messages().raw("format.bar.filled", "■");
        String empty = plugin.messages().raw("format.bar.empty", "□");
        String negative = plugin.messages().raw("format.bar.negative-color", "<#ff5c7a>");
        String positive = plugin.messages().raw("format.bar.positive-color", "<#fde68a>");
        String zero = plugin.messages().raw("format.bar.zero-color", "<#cbd5e1>");
        String emptyColor = plugin.messages().raw("format.bar.empty-color", "<#3f3f46>");
        StringBuilder sb = new StringBuilder();
        for (int i = s.brightnessMin; i <= s.brightnessMax; i++) {
            boolean lit = value < 0 ? (i < 0 && i >= value) : (i > 0 && i <= value);
            if (i == 0) {
                sb.append(zero).append("|");
            } else if (lit) {
                sb.append(i < 0 ? negative : positive).append(filled);
            } else {
                sb.append(emptyColor).append(empty);
            }
        }
        return sb.toString();
    }

    public String color(int value) {
        if (value < 0) {
            return plugin.messages().raw("format.bar.negative-color", "<#ff5c7a>");
        }
        if (value == 0) {
            return plugin.messages().raw("format.bar.zero-color", "<#cbd5e1>");
        }
        return plugin.messages().raw("format.bar.positive-color", "<#fde68a>");
    }
}
