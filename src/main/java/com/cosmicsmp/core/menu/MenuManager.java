package com.cosmicsmp.core.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.config.YamlFile;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.InventoryHolder;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Loads every {@code menus/*.yml}, opens menus by name and routes inventory events.
 * Feature modules register named factories so YAML actions like {@code open:profile} work for any menu.
 */
public final class MenuManager implements Listener {

    private static final List<String> DEFAULT_MENUS = List.of("merchant", "star_tree", "confirm", "profile", "stock");

    private final CosmicSMP plugin;
    private final Map<String, MenuTemplate> templates = new HashMap<>();
    private final Map<String, BiFunction<Player, CosmicMenu, CosmicMenu>> factories = new HashMap<>();
    private final Map<UUID, Long> lastClick = new HashMap<>();
    private Function<Player, Placeholders> basePlaceholders = p -> Placeholders.of("player", p.getName());

    public MenuManager(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void load() {
        templates.clear();
        for (String name : DEFAULT_MENUS) {
            String path = "menus/" + name + ".yml";
            if (!new File(plugin.getDataFolder(), path).exists() && plugin.getResource(path) != null) {
                plugin.saveResource(path, false);
            }
        }
        File folder = new File(plugin.getDataFolder(), "menus");
        File[] files = folder.listFiles((dir, file) -> file.endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            YamlFile yaml = new YamlFile(plugin, file);
            if (yaml.load()) {
                String name = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                templates.put(name, new MenuTemplate(name, yaml.get()));
            }
        }
    }

    public MenuTemplate template(String name) {
        MenuTemplate template = templates.get(name);
        if (template == null) {
            plugin.getLogger().warning("Menu '" + name + "' is missing (menus/" + name + ".yml) - using an empty layout.");
            template = new MenuTemplate(name, new org.bukkit.configuration.file.YamlConfiguration());
            templates.put(name, template);
        }
        return template;
    }

    public void registerFactory(String name, BiFunction<Player, CosmicMenu, CosmicMenu> factory) {
        factories.put(name.toLowerCase(Locale.ROOT), factory);
    }

    public void basePlaceholders(Function<Player, Placeholders> provider) {
        this.basePlaceholders = provider;
    }

    public Placeholders basePlaceholders(Player player) {
        return basePlaceholders.apply(player);
    }

    public boolean open(String name, Player player, CosmicMenu parent) {
        BiFunction<Player, CosmicMenu, CosmicMenu> factory = factories.get(name.toLowerCase(Locale.ROOT));
        if (factory == null) {
            return false;
        }
        CosmicMenu menu = factory.apply(player, parent);
        if (menu == null) {
            return false;
        }
        menu.open();
        return true;
    }

    void runActions(CosmicMenu menu, List<String> actions, Placeholders ph) {
        Player player = menu.viewer();
        for (String raw : actions) {
            String action = ph.apply(raw);
            int colon = action.indexOf(':');
            String type = (colon < 0 ? action : action.substring(0, colon)).trim().toLowerCase(Locale.ROOT);
            String arg = colon < 0 ? "" : action.substring(colon + 1).trim();
            switch (type) {
                case "close" -> Bukkit.getScheduler().runTask(plugin, () -> player.closeInventory());
                case "back" -> menu.back();
                case "refresh" -> menu.refresh();
                case "open" -> Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!open(arg, player, menu)) {
                        plugin.getLogger().warning("Menu action open:" + arg + " - unknown menu");
                    }
                });
                case "command" -> Bukkit.getScheduler().runTask(plugin, () -> player.performCommand(arg.startsWith("/") ? arg.substring(1) : arg));
                case "console" -> Bukkit.getScheduler().runTask(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), arg.startsWith("/") ? arg.substring(1) : arg));
                case "message" -> player.sendMessage(Text.parse(arg.replace("{prefix}", plugin.messages().prefix())));
                case "sound" -> SoundSpec.parse(arg, SoundSpec.NONE).play(player);
                default -> {
                    if (!menu.customAction(type, arg)) {
                        plugin.getLogger().warning("Unknown menu action '" + raw + "' in menus/" + menu.template.name() + ".yml");
                    }
                }
            }
        }
    }

    public void closeAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.Inventory top = player.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder(false) instanceof CosmicMenu) {
                player.closeInventory();
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof CosmicMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getRawSlot() >= 54) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastClick.put(event.getWhoClicked().getUniqueId(), now);
        if (last != null && now - last < 120) {
            return; // anti spam / anti double-click
        }
        try {
            menu.handleClick(event);
        } catch (RuntimeException ex) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Menu click failed in " + menu.template.name(), ex);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof CosmicMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastClick.remove(event.getPlayer().getUniqueId());
    }
}
