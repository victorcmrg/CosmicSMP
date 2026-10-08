package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import com.cosmicsmp.feature.ability.CooldownService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Action-bar HUD shown while holding a star: selected ability, cooldowns and brightness.
 * Network-friendly: a packet is only sent when the text changes, plus a keep-alive every 2 seconds
 * (the client fades action bars after ~3s). Players not holding a star cost nothing.
 */
public final class HudService implements Listener {

    private record Last(String text, long sentAt) {
    }

    private final CosmicSMP plugin;
    private final Map<UUID, Last> last = new HashMap<>();
    private final Map<UUID, Long> heldUntil = new HashMap<>();
    private BukkitTask task;
    private boolean registered;

    public HudService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (!registered) {
            registered = true;
            Bukkit.getPluginManager().registerEvents(this, plugin);
        }
        if (plugin.settings().hudEnabled) {
            int period = plugin.settings().hudUpdateTicks;
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, period, period);
        }
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        last.clear();
    }

    public void invalidate(Player player) {
        last.remove(player.getUniqueId());
    }

    /** Lets another action-bar message (cast feedback, brightness...) stay visible before the HUD returns. */
    public void hold(Player player, long millis) {
        heldUntil.merge(player.getUniqueId(), System.currentTimeMillis() + millis, Math::max);
        last.remove(player.getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : Bukkit.getOnlinePlayers()) {
            ItemStack hand = player.getInventory().getItemInMainHand();
            String starId = StarItemService.starId(hand);
            if (starId == null) {
                if (last.remove(player.getUniqueId()) != null) {
                    player.sendActionBar(net.kyori.adventure.text.Component.empty());
                }
                continue;
            }
            Long held = heldUntil.get(player.getUniqueId());
            if (held != null) {
                if (held > now) {
                    continue;
                }
                heldUntil.remove(player.getUniqueId());
            }
            String text = build(player, starId);
            if (text == null) {
                continue;
            }
            Last previous = last.get(player.getUniqueId());
            if (previous != null && previous.text.equals(text) && now - previous.sentAt < 2000) {
                continue;
            }
            last.put(player.getUniqueId(), new Last(text, now));
            player.sendActionBar(Text.parse(text));
        }
    }

    private String build(Player player, String starId) {
        StarDefinition star = plugin.stars().get(starId);
        PlayerData data = plugin.players().get(player);
        PlayerData.OwnedStar owned = data.star(starId);
        if (star == null || owned == null) {
            return null;
        }
        String ready = plugin.messages().raw("hud.ready", "<#86efac>✔");
        String slotSelected = plugin.messages().raw("hud.slot-selected", "<#fde68a><bold>{name}</bold> {state}");
        String slotOther = plugin.messages().raw("hud.slot", "<#a1a1aa>{name} {state}");
        String slotLocked = plugin.messages().raw("hud.slot-locked", "");
        String separator = plugin.messages().raw("hud.separator", " <dark_gray>┃</dark_gray> ");
        StringBuilder slots = new StringBuilder();
        for (AbilityDefinition def : star.primaries()) {
            String format;
            String state;
            if (!owned.unlocked(def.slot())) {
                format = slotLocked;
                state = "";
            } else {
                long left = plugin.cooldowns().remainingMillis(data, def.key());
                state = left > 0 ? plugin.messages().raw("hud.cooldown", "<#fca5a5>{time}").replace("{time}", CooldownService.format(left)) : ready;
                format = owned.selected == def.slot() ? slotSelected : slotOther;
            }
            if (format.isEmpty()) {
                continue;
            }
            if (!slots.isEmpty()) {
                slots.append(separator);
            }
            slots.append(format.replace("{name}", Text.plain(Text.parse(def.name()))).replace("{state}", state)
                    .replace("{slot}", String.valueOf(def.slot())));
        }
        if (slots.isEmpty()) {
            slots.append(plugin.messages().raw("hud.no-primaries", "<#71717a>No primaries unlocked"));
        }
        Placeholders ph = Placeholders.of("star", star.displayName(), "slots", slots.toString(),
                "brightness", data.brightness, "brightness_color", plugin.brightness().color(data.brightness));
        return ph.apply(plugin.messages().raw("hud.format", "{star} <dark_gray>»</dark_gray> {slots}"));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
        heldUntil.remove(event.getPlayer().getUniqueId());
    }
}
