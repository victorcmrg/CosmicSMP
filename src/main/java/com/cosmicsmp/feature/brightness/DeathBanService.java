package com.cosmicsmp.feature.brightness;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.GlobalData;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.util.UUID;

/**
 * Death bans at minimum brightness. Bans are stored in {@code global.json} with an absolute expiry and enforced at
 * login, so they survive restarts and never depend on the vanilla ban list. When a ban ends (expiry or unban) the
 * player's brightness is reset to {@code death-ban.reset-brightness-on-return}.
 */
public final class DeathBanService implements Listener {

    private final CosmicSMP plugin;

    public DeathBanService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public boolean isBanned(UUID uuid) {
        GlobalData.BanEntry entry = plugin.global().data().bans.get(uuid.toString());
        return entry != null && !entry.expired(System.currentTimeMillis());
    }

    public GlobalData.BanEntry entry(UUID uuid) {
        return plugin.global().data().bans.get(uuid.toString());
    }

    public void ban(Player player) {
        if (plugin.settings().banBypassOps && player.isOp() || player.hasPermission("cosmicsmp.bypass.deathban")) {
            return;
        }
        long now = System.currentTimeMillis();
        long minutes = plugin.settings().banDurationMinutes;
        long until = minutes <= 0 ? -1 : now + minutes * 60_000L;
        plugin.global().data().bans.put(player.getUniqueId().toString(),
                new GlobalData.BanEntry(player.getName(), now, until, "brightness"));
        plugin.global().markDirty();
        PlayerData data = plugin.players().get(player);
        plugin.players().saveSoon(data);
        Placeholders ph = Placeholders.of("player", player.getName(), "duration", duration(until));
        if (plugin.settings().banBroadcast) {
            plugin.messages().broadcast("death-ban.broadcast", ph);
        }
        // kick on the next tick: never kick inside the death event itself
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.kick(kickMessage(ph));
            }
        }, 2L);
    }

    /** Lifts a ban and resets brightness (works for offline players). */
    public boolean unban(UUID uuid) {
        GlobalData.BanEntry removed = plugin.global().data().bans.remove(uuid.toString());
        if (removed == null) {
            return false;
        }
        plugin.global().markDirty();
        plugin.brightness().setAny(uuid, plugin.settings().banResetBrightness,
                com.cosmicsmp.api.event.BrightnessChangeEvent.Cause.BAN_RESET);
        return true;
    }

    private net.kyori.adventure.text.Component kickMessage(Placeholders ph) {
        String raw = String.join("\n", plugin.messages().rawList("death-ban.screen"));
        return Text.parse(ph.apply(raw.isEmpty() ? "<red>You ran out of brightness." : raw));
    }

    public String duration(long until) {
        if (until < 0) {
            return plugin.messages().raw("format.permanent", "permanent");
        }
        long left = Math.max(0, until - System.currentTimeMillis()) / 1000;
        long days = left / 86400;
        long hours = (left % 86400) / 3600;
        long minutes = (left % 3600) / 60;
        if (days > 0) {
            return days + "d " + hours + "h";
        }
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return Math.max(1, minutes) + "m";
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED || !plugin.settings().banEnabled) {
            return;
        }
        String key = event.getUniqueId().toString();
        GlobalData.BanEntry entry = plugin.global().data().bans.get(key);
        if (entry == null) {
            return;
        }
        if (entry.expired(System.currentTimeMillis())) {
            plugin.global().data().bans.remove(key);
            plugin.global().markDirty();
            PlayerData data = plugin.players().getIfLoaded(event.getUniqueId());
            if (data != null) {
                data.brightness = plugin.settings().banResetBrightness;
                data.dirty = true;
            }
            return;
        }
        Placeholders ph = Placeholders.of("player", event.getName(), "duration", duration(entry.until));
        event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, kickMessage(ph));
    }
}
