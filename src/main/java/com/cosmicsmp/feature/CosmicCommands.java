package com.cosmicsmp.feature;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.api.event.BrightnessChangeEvent;
import com.cosmicsmp.core.command.CommandCategory;
import com.cosmicsmp.core.command.CommandManager;
import com.cosmicsmp.core.command.SubCommand;
import com.cosmicsmp.core.data.GlobalData;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import com.cosmicsmp.feature.menu.MerchantMenu;
import com.cosmicsmp.feature.menu.ProfileMenu;
import com.cosmicsmp.feature.menu.StarTreeMenu;
import com.cosmicsmp.feature.menu.StockMenu;
import com.cosmicsmp.feature.star.AbilityDefinition;
import com.cosmicsmp.feature.star.StarDefinition;
import com.cosmicsmp.feature.star.StarItemService;
import com.cosmicsmp.feature.star.StockService;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/** Every built-in sub command, grouped in categories. */
public final class CosmicCommands {

    private final CosmicSMP plugin;

    public CosmicCommands(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void register(CommandManager manager) {
        root(manager.root());
        manager.register(stars(new CommandCategory("stars", "✦", "star")));
        manager.register(brightness(new CommandCategory("brightness", "☀", "bright", "b")));
        manager.register(merchant(new CommandCategory("merchant", "☄", "m")));
        manager.register(stock(new CommandCategory("stock", "⚖")));
        manager.register(player(new CommandCategory("player", "☺", "p")));
        manager.register(admin(new CommandCategory("admin", "⚙")));
    }

    // ------------------------------------------------------------------ helpers

    private void msg(CommandSender sender, String key, Object... pairs) {
        plugin.messages().send(sender, key, Placeholders.of(pairs));
    }

    private List<String> online() {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            names.add(player.getName());
        }
        return names;
    }

    private List<String> starIds() {
        return plugin.stars().ids();
    }

    private StarDefinition star(CommandSender sender, String id) {
        StarDefinition star = plugin.stars().get(id);
        if (star == null) {
            msg(sender, "command.unknown-star", "star", id, "stars", String.join(", ", starIds()));
        }
        return star;
    }

    private Player online(CommandSender sender, String name) {
        Player player = Bukkit.getPlayerExact(name);
        if (player == null) {
            msg(sender, "command.player-offline", "player", name);
        }
        return player;
    }

    /** Resolves a known player (online or cached offline) without blocking web lookups. */
    private void known(CommandSender sender, String name, BiConsumer<UUID, String> action) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) {
            action.accept(online.getUniqueId(), online.getName());
            return;
        }
        OfflinePlayer cached = Bukkit.getOfflinePlayerIfCached(name);
        if (cached == null || !plugin.players().hasProfile(cached.getUniqueId())) {
            msg(sender, "command.player-unknown", "player", name);
            return;
        }
        action.accept(cached.getUniqueId(), cached.getName() == null ? name : cached.getName());
    }

    private Integer number(CommandSender sender, String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ex) {
            msg(sender, "command.invalid-number", "input", raw);
            return null;
        }
    }

    private static List<String> args(String[] args, int index, List<String> options) {
        return args.length == index + 1 ? options : List.of();
    }

    // ------------------------------------------------------------------ root

    private void root(CommandCategory root) {
        root.add(SubCommand.builder("menu").aliases("profile").permission("cosmicsmp.command.menu").playerOnly()
                .executor((s, a) -> new ProfileMenu(plugin, (Player) s, null).open()).build());
        root.add(SubCommand.builder("info").aliases("version", "about").permission("cosmicsmp.command.help")
                .executor((s, a) -> msg(s, "command.info", "version", plugin.getPluginMeta().getVersion(),
                        "stars", plugin.stars().all().size(), "modules", String.join(", ", plugin.modules().enabledIds())))
                .build());
    }

    // ------------------------------------------------------------------ stars

    private CommandCategory stars(CommandCategory c) {
        c.add(SubCommand.builder("list").permission("cosmicsmp.command.stars").executor((s, a) -> {
            msg(s, "stars.list-header");
            PlayerData data = s instanceof Player p ? plugin.players().get(p) : null;
            for (StarDefinition star : plugin.stars().all()) {
                String state = data == null ? "" : data.owns(star.id()) ? plugin.messages().raw("format.owned", "owned")
                        : data.discovered.contains(star.id()) ? plugin.messages().raw("format.discovered", "discovered") : "";
                msg(s, "stars.list-line", "star", star.displayName(), "id", star.id(), "price", star.price(),
                        "stock", plugin.stock().display(star), "state", state);
            }
        }).build());

        c.add(SubCommand.builder("info").usage("<star>").minArgs(1).permission("cosmicsmp.command.stars")
                .completer((s, a) -> args(a, 0, starIds()))
                .executor((s, a) -> {
                    StarDefinition star = star(s, a[0]);
                    if (star == null) {
                        return;
                    }
                    msg(s, "stars.info-header", "star", star.displayName(), "price", star.price(), "stock", plugin.stock().display(star));
                    for (String line : star.description()) {
                        s.sendMessage(Text.parse(line));
                    }
                    for (AbilityDefinition def : star.all()) {
                        msg(s, "stars.info-line", "name", def.name(), "cost", def.cost(),
                                "type", def.isPassive() ? plugin.messages().raw("format.passive", "Passive")
                                        : plugin.messages().raw("format.primary", "Primary #{slot}").replace("{slot}", String.valueOf(def.slot())),
                                "cooldown", def.isPassive() ? "-" : def.cooldownSeconds() + "s",
                                "description", String.join(" ", def.description()));
                    }
                }).build());

        c.add(SubCommand.builder("tree").usage("<star>").minArgs(1).playerOnly().permission("cosmicsmp.command.stars")
                .completer((s, a) -> args(a, 0, starIds()))
                .executor((s, a) -> {
                    StarDefinition star = star(s, a[0]);
                    if (star != null) {
                        boolean remote = !plugin.settings().purchasesRequireMerchant || s.hasPermission("cosmicsmp.merchant.remote");
                        new StarTreeMenu(plugin, (Player) s, null, star, remote).open();
                    }
                }).build());

        c.add(SubCommand.builder("select").usage("<1-3>").minArgs(1).playerOnly().permission("cosmicsmp.ability.use")
                .completer((s, a) -> args(a, 0, List.of("1", "2", "3")))
                .executor((s, a) -> {
                    Player player = (Player) s;
                    String starId = StarItemService.starId(player.getInventory().getItemInMainHand());
                    Integer slot = number(s, a[0]);
                    if (slot == null) {
                        return;
                    }
                    if (starId == null) {
                        msg(s, "stars.hold-star");
                        return;
                    }
                    StarDefinition star = plugin.stars().get(starId);
                    if (star == null || !plugin.starService().select(player, star, slot)) {
                        msg(s, "abilities.locked", "ability", "#" + slot, "star", star == null ? starId : star.displayName());
                        return;
                    }
                    msg(s, "abilities.selected", "ability", star.ability(slot).name(), "slot", slot, "star", star.displayName());
                }).build());

        c.add(SubCommand.builder("recover").playerOnly().permission("cosmicsmp.command.stars")
                .executor((s, a) -> {
                    plugin.starItems().sanitize((Player) s);
                    msg(s, "stars.recovered");
                }).build());
        return c;
    }

    // ------------------------------------------------------------------ brightness

    private CommandCategory brightness(CommandCategory c) {
        c.add(SubCommand.builder("check").aliases("get", "see").usage("[player]").permission("cosmicsmp.command.brightness")
                .completer((s, a) -> s.hasPermission("cosmicsmp.command.brightness.others") ? args(a, 0, online()) : List.of())
                .executor((s, a) -> {
                    if (a.length == 0) {
                        if (!(s instanceof Player player)) {
                            msg(s, "command.player-only");
                            return;
                        }
                        plugin.messages().send(s, "brightness.check-self", plugin.brightness().placeholders(player, plugin.brightness().get(player)));
                        return;
                    }
                    if (!s.hasPermission("cosmicsmp.command.brightness.others")) {
                        msg(s, "command.no-permission");
                        return;
                    }
                    known(s, a[0], (uuid, name) -> plugin.players().load(uuid).thenAccept(data ->
                            msg(s, "brightness.check-other", "player", name, "brightness", data.brightness,
                                    "brightness_bar", plugin.brightness().bar(data.brightness),
                                    "brightness_color", plugin.brightness().color(data.brightness),
                                    "brightness_max", plugin.settings().brightnessMax)));
                }).build());

        c.add(SubCommand.builder("top").permission("cosmicsmp.command.brightness.top").executor((s, a) -> {
            msg(s, "brightness.top-header");
            int rank = 1;
            for (GlobalData.LeaderEntry entry : plugin.brightness().top(10)) {
                msg(s, "brightness.top-line", "rank", rank++, "player", entry.name, "brightness", entry.brightness,
                        "kills", entry.kills, "brightness_color", plugin.brightness().color(entry.brightness));
            }
        }).build());

        for (String action : List.of("set", "give", "take")) {
            c.add(SubCommand.builder(action).usage("<player> <amount>").minArgs(2).permission("cosmicsmp.admin.brightness")
                    .completer((s, a) -> a.length == 1 ? online() : args(a, 1, List.of("1", "2", "3", "5", "7")))
                    .executor((s, a) -> {
                        Integer amount = number(s, a[1]);
                        if (amount == null) {
                            return;
                        }
                        known(s, a[0], (uuid, name) -> plugin.players().load(uuid).thenAccept(data -> {
                            int value = switch (action) {
                                case "give" -> data.brightness + amount;
                                case "take" -> data.brightness - amount;
                                default -> amount;
                            };
                            plugin.brightness().setAny(uuid, value, BrightnessChangeEvent.Cause.ADMIN);
                            msg(s, "brightness.admin-set", "player", name, "brightness", plugin.brightness().clamp(value));
                        }));
                    }).build());
        }
        return c;
    }

    // ------------------------------------------------------------------ merchant

    private CommandCategory merchant(CommandCategory c) {
        c.add(SubCommand.builder("open").usage("[player]").permission("cosmicsmp.admin.merchant")
                .completer((s, a) -> args(a, 0, online()))
                .executor((s, a) -> {
                    Player target = a.length > 0 ? online(s, a[0]) : s instanceof Player p ? p : null;
                    if (target == null) {
                        if (a.length == 0) {
                            msg(s, "command.player-only");
                        }
                        return;
                    }
                    new MerchantMenu(plugin, target, null, true).open();
                }).build());

        c.add(SubCommand.builder("spawn").aliases("create").usage("[id]").playerOnly().permission("cosmicsmp.admin.merchant")
                .executor((s, a) -> {
                    Player player = (Player) s;
                    String id = plugin.merchants().create(player.getLocation(), a.length > 0 ? a[0] : null);
                    msg(s, "merchant.spawned", "id", id);
                }).build());

        c.add(SubCommand.builder("remove").aliases("delete").usage("<id|nearest>").minArgs(1).permission("cosmicsmp.admin.merchant")
                .completer((s, a) -> {
                    List<String> ids = new ArrayList<>(plugin.merchants().points().keySet());
                    ids.add("nearest");
                    return args(a, 0, ids);
                })
                .executor((s, a) -> {
                    String id = a[0];
                    if (id.equalsIgnoreCase("nearest")) {
                        if (!(s instanceof Player player)) {
                            msg(s, "command.player-only");
                            return;
                        }
                        id = plugin.merchants().nearest(player.getLocation(), 16);
                        if (id == null) {
                            msg(s, "merchant.none-near");
                            return;
                        }
                    }
                    msg(s, plugin.merchants().remove(id.toLowerCase(Locale.ROOT)) ? "merchant.removed" : "merchant.unknown", "id", id);
                }).build());

        c.add(SubCommand.builder("list").permission("cosmicsmp.admin.merchant").executor((s, a) -> {
            msg(s, "merchant.list-header", "count", plugin.merchants().points().size());
            for (Map.Entry<String, GlobalData.MerchantPoint> entry : plugin.merchants().points().entrySet()) {
                GlobalData.MerchantPoint p = entry.getValue();
                msg(s, "merchant.list-line", "id", entry.getKey(), "world", p.world, "x", (int) p.x, "y", (int) p.y, "z", (int) p.z);
            }
        }).build());

        c.add(SubCommand.builder("tp").usage("<id>").minArgs(1).playerOnly().permission("cosmicsmp.admin.merchant")
                .completer((s, a) -> args(a, 0, new ArrayList<>(plugin.merchants().points().keySet())))
                .executor((s, a) -> {
                    GlobalData.MerchantPoint point = plugin.merchants().points().get(a[0].toLowerCase(Locale.ROOT));
                    Location location = point == null ? null : plugin.merchants().location(point);
                    if (location == null) {
                        msg(s, "merchant.unknown", "id", a[0]);
                        return;
                    }
                    ((Player) s).teleportAsync(location.add(location.getDirection().multiply(2)).setDirection(location.getDirection().multiply(-1)));
                }).build());
        return c;
    }

    // ------------------------------------------------------------------ stock

    private CommandCategory stock(CommandCategory c) {
        c.add(SubCommand.builder("view").aliases("list").permission("cosmicsmp.admin.stock").executor((s, a) -> {
            msg(s, "stock.header");
            for (StarDefinition star : plugin.stars().all()) {
                msg(s, "stock.line", "star", star.displayName(), "stock", plugin.stock().display(star),
                        "default", star.defaultStock() < 0 ? plugin.messages().raw("format.unlimited", "∞") : star.defaultStock());
            }
        }).build());

        c.add(SubCommand.builder("set").usage("<star> <amount|unlimited>").minArgs(2).permission("cosmicsmp.admin.stock")
                .completer((s, a) -> a.length == 1 ? starIds() : args(a, 1, List.of("0", "1", "3", "5", "unlimited")))
                .executor((s, a) -> {
                    StarDefinition star = star(s, a[0]);
                    if (star == null) {
                        return;
                    }
                    Integer amount = a[1].equalsIgnoreCase("unlimited") ? Integer.valueOf(StockService.UNLIMITED) : number(s, a[1]);
                    if (amount == null) {
                        return;
                    }
                    plugin.stock().set(star.id(), amount);
                    msg(s, "stock.updated", "star", star.displayName(), "stock", plugin.stock().display(star));
                }).build());

        c.add(SubCommand.builder("reset").usage("[star|all]").permission("cosmicsmp.admin.stock")
                .completer((s, a) -> {
                    List<String> options = new ArrayList<>(starIds());
                    options.add("all");
                    return args(a, 0, options);
                })
                .executor((s, a) -> {
                    if (a.length == 0 || a[0].equalsIgnoreCase("all")) {
                        plugin.stars().all().forEach(plugin.stock()::reset);
                        msg(s, "stock.reset-all");
                        return;
                    }
                    StarDefinition star = star(s, a[0]);
                    if (star != null) {
                        plugin.stock().reset(star);
                        msg(s, "stock.updated", "star", star.displayName(), "stock", plugin.stock().display(star));
                    }
                }).build());

        c.add(SubCommand.builder("menu").playerOnly().permission("cosmicsmp.admin.stock")
                .executor((s, a) -> new StockMenu(plugin, (Player) s, null).open()).build());
        return c;
    }

    // ------------------------------------------------------------------ player (admin)

    private CommandCategory player(CommandCategory c) {
        c.add(SubCommand.builder("info").usage("<player>").minArgs(1).permission("cosmicsmp.admin.player")
                .completer((s, a) -> args(a, 0, online()))
                .executor((s, a) -> known(s, a[0], (uuid, name) -> plugin.players().load(uuid).thenAccept(data -> {
                    List<String> stars = new ArrayList<>();
                    data.stars.forEach((id, owned) -> stars.add(id + (owned.passive ? "+P" : "") + owned.primaries));
                    GlobalData.BanEntry ban = plugin.deathBans().entry(uuid);
                    msg(s, "player.info", "player", name, "uuid", uuid, "brightness", data.brightness,
                            "stars", stars.isEmpty() ? "-" : String.join(", ", stars),
                            "discovered", data.discovered.isEmpty() ? "-" : String.join(", ", data.discovered),
                            "kills", data.stats.kills, "deaths", data.stats.deaths,
                            "banned", ban == null ? "-" : plugin.deathBans().duration(ban.until));
                }))).build());

        c.add(SubCommand.builder("givestar").usage("<player> <star> [full]").minArgs(2).permission("cosmicsmp.admin.player")
                .completer((s, a) -> a.length == 1 ? online() : a.length == 2 ? starIds() : args(a, 2, List.of("full")))
                .executor((s, a) -> {
                    Player target = online(s, a[0]);
                    StarDefinition star = target == null ? null : star(s, a[1]);
                    if (star == null) {
                        return;
                    }
                    plugin.starService().grant(target, star, a.length > 2 && a[2].equalsIgnoreCase("full"));
                    msg(s, "player.star-given", "player", target.getName(), "star", star.displayName());
                }).build());

        c.add(SubCommand.builder("takestar").usage("<player> <star>").minArgs(2).permission("cosmicsmp.admin.player")
                .completer((s, a) -> a.length == 1 ? online() : args(a, 1, starIds()))
                .executor((s, a) -> {
                    Player target = online(s, a[0]);
                    StarDefinition star = target == null ? null : star(s, a[1]);
                    if (star == null) {
                        return;
                    }
                    msg(s, plugin.starService().revoke(target, star.id()) ? "player.star-taken" : "player.star-not-owned",
                            "player", target.getName(), "star", star.displayName());
                }).build());

        c.add(SubCommand.builder("unlock").usage("<player> <star> <passive|1|2|3>").minArgs(3).permission("cosmicsmp.admin.player")
                .completer((s, a) -> a.length == 1 ? online() : a.length == 2 ? starIds() : args(a, 2, List.of("passive", "1", "2", "3")))
                .executor((s, a) -> {
                    Player target = online(s, a[0]);
                    StarDefinition star = target == null ? null : star(s, a[1]);
                    if (star == null) {
                        return;
                    }
                    int slot = a[2].equalsIgnoreCase("passive") ? 0 : a[2].matches("[1-3]") ? Integer.parseInt(a[2]) : -1;
                    PlayerData.OwnedStar owned = plugin.players().get(target).star(star.id());
                    if (slot < 0 || star.ability(slot) == null || owned == null) {
                        msg(s, "player.star-not-owned", "player", target.getName(), "star", star.displayName());
                        return;
                    }
                    if (slot == 0) {
                        owned.passive = true;
                    } else {
                        owned.primaries.add(slot);
                        if (owned.selected == 0) {
                            owned.selected = slot;
                        }
                    }
                    plugin.players().saveSoon(plugin.players().get(target));
                    plugin.starItems().refresh(target, star.id());
                    plugin.passives().refresh(target);
                    msg(s, "player.unlocked", "player", target.getName(), "ability", star.ability(slot).name());
                }).build());

        c.add(SubCommand.builder("clearcooldowns").usage("<player>").minArgs(1).permission("cosmicsmp.admin.player")
                .completer((s, a) -> args(a, 0, online()))
                .executor((s, a) -> {
                    Player target = online(s, a[0]);
                    if (target == null) {
                        return;
                    }
                    plugin.cooldowns().clear(plugin.players().get(target));
                    for (String id : plugin.players().get(target).stars.keySet()) {
                        plugin.starItems().updateCooldownOverlay(target, id);
                    }
                    msg(s, "player.cooldowns-cleared", "player", target.getName());
                }).build());

        c.add(SubCommand.builder("reset").usage("<player> confirm").minArgs(1).permission("cosmicsmp.admin.player")
                .completer((s, a) -> a.length == 1 ? online() : args(a, 1, List.of("confirm")))
                .executor((s, a) -> {
                    if (a.length < 2 || !a[1].equalsIgnoreCase("confirm")) {
                        msg(s, "player.reset-confirm", "player", a[0]);
                        return;
                    }
                    known(s, a[0], (uuid, name) -> {
                        Player target = Bukkit.getPlayer(uuid);
                        if (target != null) {
                            plugin.starItems().removeStar(target, null);
                        }
                        plugin.players().edit(uuid, data -> {
                            data.stars.clear();
                            data.discovered.clear();
                            data.cooldowns.clear();
                            data.states.clear();
                            data.recentKills.clear();
                            data.brightness = plugin.settings().brightnessStart;
                            data.stats = new PlayerData.Stats();
                            plugin.brightness().updateLeaderboard(data);
                            if (target != null) {
                                plugin.passives().refresh(target);
                                plugin.perks().refresh(target);
                            }
                        });
                        msg(s, "player.reset", "player", name);
                    });
                }).build());

        c.add(SubCommand.builder("unban").usage("<player>").minArgs(1).permission("cosmicsmp.admin.player")
                .completer((s, a) -> {
                    List<String> names = new ArrayList<>();
                    plugin.global().data().bans.values().forEach(b -> names.add(b.name));
                    return args(a, 0, names);
                })
                .executor((s, a) -> {
                    for (Map.Entry<String, GlobalData.BanEntry> entry : plugin.global().data().bans.entrySet()) {
                        if (entry.getValue().name.equalsIgnoreCase(a[0])) {
                            plugin.deathBans().unban(UUID.fromString(entry.getKey()));
                            msg(s, "player.unbanned", "player", entry.getValue().name);
                            return;
                        }
                    }
                    msg(s, "player.not-banned", "player", a[0]);
                }).build());
        return c;
    }

    // ------------------------------------------------------------------ admin

    private CommandCategory admin(CommandCategory c) {
        c.add(SubCommand.builder("reload").permission("cosmicsmp.admin.reload").executor((s, a) -> {
            long start = System.nanoTime();
            plugin.reload();
            msg(s, "admin.reloaded", "time", (System.nanoTime() - start) / 1_000_000);
        }).build());

        c.add(SubCommand.builder("save").permission("cosmicsmp.admin.reload").executor((s, a) -> {
            plugin.players().saveAll();
            plugin.global().markDirty();
            msg(s, "admin.saved");
        }).build());

        c.add(SubCommand.builder("debug").aliases("status").permission("cosmicsmp.admin.debug").executor((s, a) -> {
            Runtime runtime = Runtime.getRuntime();
            msg(s, "admin.debug",
                    "version", plugin.getPluginMeta().getVersion(),
                    "modules", String.join(", ", plugin.modules().enabledIds()),
                    "stars", plugin.stars().all().size(),
                    "players", plugin.players().cachedCount(),
                    "effects", plugin.effects().size(),
                    "summons", plugin.summons().count(),
                    "temp", plugin.tempEntities().size(),
                    "merchants", plugin.merchants().liveCount() + "/" + plugin.merchants().points().size(),
                    "hooks", hooks(),
                    "memory", (runtime.totalMemory() - runtime.freeMemory()) / 1048576 + "MB / " + runtime.maxMemory() / 1048576 + "MB");
        }).build());

        c.add(SubCommand.builder("cleanup").permission("cosmicsmp.admin.reload").executor((s, a) -> {
            int removed = 0;
            for (org.bukkit.World world : Bukkit.getWorlds()) {
                for (org.bukkit.entity.Entity entity : world.getEntities()) {
                    if (com.cosmicsmp.core.task.TempEntities.isTemp(entity) && entity.getPersistentDataContainer().has(
                            com.cosmicsmp.core.Keys.SUMMON_OWNER) && plugin.summons().get(entity) == null) {
                        entity.remove();
                        removed++;
                    }
                }
            }
            msg(s, "admin.cleanup", "count", removed);
        }).build());
        return c;
    }

    private String hooks() {
        List<String> list = new ArrayList<>();
        list.add("ItemsAdder=" + (plugin.hooks().itemsAdder().available() ? "on" : "off"));
        list.add("MythicMobs=" + (plugin.hooks().mythicMobs().available() ? "on" : "off"));
        list.add("BetterModel=" + (plugin.hooks().betterModel().available() ? "on" : "off"));
        return String.join(", ", list);
    }
}
