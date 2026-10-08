package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Messages;
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
import java.util.List;
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
        Messages m = plugin.messages();
        String path = "actionbar-hud.";
        List<String> numbers = m.rawList(path + "numbers");
        String separator = m.raw(path + "separator", "   ");
        StringBuilder slots = new StringBuilder();
        for (AbilityDefinition def : star.primaries()) {
            String number = def.slot() - 1 < numbers.size() ? numbers.get(def.slot() - 1) : String.valueOf(def.slot());
            String piece;
            if (!owned.unlocked(def.slot())) {
                piece = m.raw(path + "locked", "<#3f3f46>{number}</#3f3f46>");
            } else {
                boolean selected = owned.selected == def.slot();
                long left = plugin.cooldowns().remainingMillis(data, def.key());
                String state = left > 0
                        ? m.raw(path + (selected ? "selected-cooldown" : "cooldown"), "<#fca5a5>{time}").replace("{time}", CooldownService.format(left))
                        : m.raw(path + (selected ? "selected-ready" : "ready"), "<#86efac>●");
                piece = m.raw(path + (selected ? "selected" : "slot"), "{number} {state}").replace("{state}", state);
            }
            if (piece.isEmpty()) {
                continue;
            }
            if (!slots.isEmpty()) {
                slots.append(separator);
            }
            slots.append(piece.replace("{number}", number).replace("{star_color}", star.color())
                    .replace("{name}", Text.plain(Text.parse(def.name()))));
        }
        String format = owned.primaries.isEmpty()
                ? m.raw(path + "no-primaries", "<#a1a1aa>No primaries yet")
                : m.raw(path + "format", "{slots}");
        Placeholders ph = Placeholders.of("slots", slots.toString(), "star", star.displayName(), "star_color", star.color(),
                "brightness", data.brightness, "brightness_color", plugin.brightness().color(data.brightness));
        return ph.apply(format);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        last.remove(event.getPlayer().getUniqueId());
        heldUntil.remove(event.getPlayer().getUniqueId());
    }
}
