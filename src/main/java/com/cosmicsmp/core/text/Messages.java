package com.cosmicsmp.core.text;

import com.cosmicsmp.core.config.YamlFile;
import com.cosmicsmp.core.fx.SoundSpec;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads {@code messages.yml}. Every message can be a simple string, a list of lines, or a rich section:
 * <pre>
 * star-purchased:
 *   chat: ["{prefix}You bought {star}!"]
 *   actionbar: "<gold>+1 star"
 *   title: {title: "<gradient:#fff:#a855f7>STAR ACQUIRED", subtitle: "{star}", fade-in: 10, stay: 50, fade-out: 15}
 *   sound: "ui.toast.challenge_complete 1 1.2"
 * </pre>
 * {@code {prefix}} is available in every message. Hex colours, gradients and legacy codes are supported (see {@link Text}).
 */
public final class Messages {

    private static final Set<String> ENTRY_KEYS = Set.of("chat", "actionbar", "title", "sound");

    private final JavaPlugin plugin;
    private final YamlFile file;
    private final Map<String, Entry> entries = new HashMap<>();
    private final Set<String> warnedMissing = new HashSet<>();
    private String prefix = "";

    public Messages(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new YamlFile(plugin, "messages.yml", true);
    }

    public void load() {
        file.load();
        YamlConfiguration config = file.get();
        prefix = config.getString("prefix", "");
        entries.clear();
        warnedMissing.clear();
        ConfigurationSection root = config.getConfigurationSection("messages");
        if (root != null) {
            collect(root, "");
        }
    }

    private void collect(ConfigurationSection section, String path) {
        for (String key : section.getKeys(false)) {
            String full = path.isEmpty() ? key : path + "." + key;
            if (section.isConfigurationSection(key)) {
                ConfigurationSection child = section.getConfigurationSection(key);
                if (child != null && !Collections.disjoint(child.getKeys(false), ENTRY_KEYS)) {
                    entries.put(full, Entry.of(child));
                } else if (child != null) {
                    collect(child, full);
                }
            } else if (section.isList(key)) {
                entries.put(full, new Entry(section.getStringList(key), null, null, null, 10, 50, 15, SoundSpec.NONE));
            } else {
                entries.put(full, new Entry(List.of(section.getString(key, "")), null, null, null, 10, 50, 15, SoundSpec.NONE));
            }
        }
    }

    public String prefix() {
        return prefix;
    }

    /** Raw string from anywhere in messages.yml (formats, help lines, etc.). */
    public String raw(String path, String def) {
        return file.get().getString(path, def);
    }

    public List<String> rawList(String path) {
        return file.get().getStringList(path);
    }

    public ConfigurationSection section(String path) {
        return file.get().getConfigurationSection(path);
    }

    /** Parses a single-line message (first chat line) into a component. */
    public Component component(String key, Placeholders ph) {
        Entry entry = entry(key);
        if (entry == null || entry.chat.isEmpty()) {
            return Component.empty();
        }
        return Text.parse(apply(entry.chat.get(0), ph));
    }

    public String format(String key, Placeholders ph) {
        Entry entry = entry(key);
        return entry == null || entry.chat.isEmpty() ? "" : apply(entry.chat.get(0), ph);
    }

    public void send(CommandSender sender, String key) {
        send(sender, key, Placeholders.empty());
    }

    public void send(CommandSender sender, String key, Placeholders ph) {
        Entry entry = entry(key);
        if (entry == null || sender == null) {
            return;
        }
        for (String line : entry.chat) {
            if (!line.isEmpty()) {
                sender.sendMessage(Text.parse(apply(line, ph)));
            }
        }
        if (sender instanceof Player player) {
            entry.sendExtras(player, this, ph);
        }
    }

    public void broadcast(String key, Placeholders ph) {
        Entry entry = entry(key);
        if (entry == null) {
            return;
        }
        for (String line : entry.chat) {
            if (!line.isEmpty()) {
                net.kyori.adventure.text.Component component = Text.parse(apply(line, ph));
                Bukkit.getConsoleSender().sendMessage(component);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendMessage(component);
                }
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            entry.sendExtras(player, this, ph);
        }
    }

    public void actionBar(Player player, String key, Placeholders ph) {
        Entry entry = entry(key);
        if (entry != null && !entry.chat.isEmpty()) {
            player.sendActionBar(Text.parse(apply(entry.chat.get(0), ph)));
        }
    }

    String apply(String line, Placeholders ph) {
        String withPrefix = line.replace("{prefix}", prefix);
        return ph == null ? withPrefix : ph.apply(withPrefix);
    }

    private Entry entry(String key) {
        Entry entry = entries.get(key);
        if (entry == null && warnedMissing.add(key)) {
            plugin.getLogger().warning("Missing message '" + key + "' in messages.yml");
        }
        return entry;
    }

    private record Entry(List<String> chat, String actionbar, String title, String subtitle,
                         int fadeIn, int stay, int fadeOut, SoundSpec sound) {

        static Entry of(ConfigurationSection s) {
            List<String> chat = s.isList("chat") ? s.getStringList("chat")
                    : s.isString("chat") ? List.of(s.getString("chat", "")) : new ArrayList<>();
            String title = null;
            String subtitle = null;
            int fi = 10;
            int st = 50;
            int fo = 15;
            if (s.isConfigurationSection("title")) {
                ConfigurationSection t = s.getConfigurationSection("title");
                title = t.getString("title", "");
                subtitle = t.getString("subtitle", "");
                fi = t.getInt("fade-in", fi);
                st = t.getInt("stay", st);
                fo = t.getInt("fade-out", fo);
            } else if (s.isString("title")) {
                title = s.getString("title");
                subtitle = s.getString("subtitle", "");
            }
            return new Entry(chat, s.getString("actionbar"), title, subtitle, fi, st, fo, SoundSpec.parse(s.get("sound"), SoundSpec.NONE));
        }

        void sendExtras(Audience audience, Messages messages, Placeholders ph) {
            if (actionbar != null && !actionbar.isEmpty()) {
                audience.sendActionBar(Text.parse(messages.apply(actionbar, ph)));
            }
            if (title != null) {
                audience.showTitle(Title.title(
                        Text.parse(messages.apply(title, ph)),
                        Text.parse(messages.apply(subtitle == null ? "" : subtitle, ph)),
                        Title.Times.times(Duration.ofMillis(fadeIn * 50L), Duration.ofMillis(stay * 50L), Duration.ofMillis(fadeOut * 50L))));
            }
            if (audience instanceof Player player) {
                sound.play(player);
            }
        }
    }
}
