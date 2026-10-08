package com.cosmicsmp.core.command;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.text.Messages;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The single {@code /cosmic} command. Layout:
 * <pre>
 * /cosmic                         -> help
 * /cosmic help [category]
 * /cosmic &lt;root command&gt; ...      (menu, ...)
 * /cosmic &lt;category&gt;              -> category help
 * /cosmic &lt;category&gt; &lt;command&gt; ...
 * </pre>
 * Help and tab completion only ever show what the sender is allowed to run.
 */
public final class CommandManager implements CommandExecutor, TabCompleter {

    public static final String ROOT = "";

    private final CosmicSMP plugin;
    private final Map<String, CommandCategory> categories = new LinkedHashMap<>();
    private String label = "cosmic";

    public CommandManager(CosmicSMP plugin) {
        this.plugin = plugin;
        categories.put(ROOT, new CommandCategory(ROOT, ""));
    }

    public void bind(PluginCommand command) {
        if (command == null) {
            plugin.getLogger().severe("Command 'cosmic' missing from plugin.yml");
            return;
        }
        label = command.getName();
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    public CommandCategory root() {
        return categories.get(ROOT);
    }

    public CommandCategory register(CommandCategory category) {
        categories.put(category.name(), category);
        return category;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help") || args[0].equals("?")) {
            if (args.length >= 2) {
                CommandCategory category = category(args[1]);
                if (category != null && category.visibleTo(sender)) {
                    sendCategoryHelp(sender, category);
                    return true;
                }
            }
            sendHelp(sender);
            return true;
        }

        SubCommand rootCommand = root().find(args[0]);
        if (rootCommand != null) {
            run(sender, rootCommand, ROOT, Arrays.copyOfRange(args, 1, args.length));
            return true;
        }

        CommandCategory category = category(args[0]);
        if (category == null || category == root()) {
            plugin.messages().send(sender, "command.unknown", Placeholders.of("label", label));
            return true;
        }
        if (!category.visibleTo(sender)) {
            plugin.messages().send(sender, "command.no-permission");
            return true;
        }
        if (args.length == 1) {
            sendCategoryHelp(sender, category);
            return true;
        }
        SubCommand sub = category.find(args[1]);
        if (sub == null) {
            plugin.messages().send(sender, "command.unknown-sub", Placeholders.of("label", label, "category", category.name()));
            return true;
        }
        run(sender, sub, category.name(), Arrays.copyOfRange(args, 2, args.length));
        return true;
    }

    private void run(CommandSender sender, SubCommand command, String category, String[] args) {
        if (command.permission() != null && !command.permission().isEmpty() && !sender.hasPermission(command.permission())) {
            plugin.messages().send(sender, "command.no-permission");
            return;
        }
        if (command.playerOnly() && !(sender instanceof Player)) {
            plugin.messages().send(sender, "command.player-only");
            return;
        }
        if (args.length < command.minArgs()) {
            plugin.messages().send(sender, "command.usage", Placeholders.of("usage", fullUsage(category, command)));
            return;
        }
        try {
            command.execute(sender, args);
        } catch (RuntimeException ex) {
            plugin.messages().send(sender, "command.error");
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Command failed: /" + label + " " + category + " " + command.name(), ex);
        }
    }

    public String fullUsage(String category, SubCommand command) {
        StringBuilder sb = new StringBuilder("/").append(label).append(' ');
        if (!category.isEmpty()) {
            sb.append(category).append(' ');
        }
        sb.append(command.name());
        if (!command.usage().isEmpty()) {
            sb.append(' ').append(command.usage());
        }
        return sb.toString();
    }

    private CommandCategory category(String input) {
        for (CommandCategory category : categories.values()) {
            if (category != root() && category.matches(input)) {
                return category;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ help

    public void sendHelp(CommandSender sender) {
        Messages m = plugin.messages();
        for (String line : m.rawList("help.header")) {
            sender.sendMessage(Text.parse(line.replace("{prefix}", m.prefix()).replace("{label}", label)));
        }
        for (SubCommand command : root().visible(sender)) {
            sender.sendMessage(commandLine(ROOT, command));
        }
        String format = m.raw("help.category-line", "<gray>/{label} {category}");
        String hover = m.raw("help.category-hover", "<gray>Click to open");
        for (CommandCategory category : categories.values()) {
            if (category == root() || !category.visibleTo(sender)) {
                continue;
            }
            Placeholders ph = Placeholders.of("label", label, "category", category.name(), "icon", category.icon(),
                    "description", m.raw("help.categories." + category.name(), ""),
                    "count", category.visible(sender).size());
            sender.sendMessage(Text.parse(ph.apply(format))
                    .hoverEvent(HoverEvent.showText(Text.parse(ph.apply(hover))))
                    .clickEvent(ClickEvent.runCommand("/" + label + " help " + category.name())));
        }
        for (String line : m.rawList("help.footer")) {
            sender.sendMessage(Text.parse(line.replace("{prefix}", m.prefix()).replace("{label}", label)));
        }
    }

    public void sendCategoryHelp(CommandSender sender, CommandCategory category) {
        Messages m = plugin.messages();
        Placeholders ph = Placeholders.of("label", label, "category", category.name(), "icon", category.icon(),
                "description", m.raw("help.categories." + category.name(), ""));
        for (String line : m.rawList("help.category-header")) {
            sender.sendMessage(Text.parse(ph.apply(line.replace("{prefix}", m.prefix()))));
        }
        for (SubCommand command : category.visible(sender)) {
            sender.sendMessage(commandLine(category.name(), command));
        }
        for (String line : m.rawList("help.category-footer")) {
            sender.sendMessage(Text.parse(ph.apply(line.replace("{prefix}", m.prefix()))));
        }
    }

    private Component commandLine(String category, SubCommand command) {
        Messages m = plugin.messages();
        String key = "help.commands." + (category.isEmpty() ? "root" : category) + "." + command.name();
        String usage = fullUsage(category, command);
        Placeholders ph = Placeholders.of("usage", usage, "label", label,
                "description", m.raw(key, ""),
                "permission", command.permission() == null || command.permission().isEmpty() ? "-" : command.permission());
        String suggest = "/" + label + " " + (category.isEmpty() ? "" : category + " ") + command.name() + " ";
        return Text.parse(ph.apply(m.raw("help.command-line", "<gray>{usage}")))
                .hoverEvent(HoverEvent.showText(Text.parse(ph.apply(m.raw("help.command-hover", "{description}")))))
                .clickEvent(ClickEvent.suggestCommand(suggest));
    }

    // ------------------------------------------------------------------ tab completion

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            options.add("help");
            for (SubCommand sub : root().visible(sender)) {
                options.add(sub.name());
            }
            for (CommandCategory category : categories.values()) {
                if (category != root() && category.visibleTo(sender)) {
                    options.add(category.name());
                }
            }
            return filter(options, args[0]);
        }
        if (args[0].equalsIgnoreCase("help") && args.length == 2) {
            List<String> options = new ArrayList<>();
            for (CommandCategory category : categories.values()) {
                if (category != root() && category.visibleTo(sender)) {
                    options.add(category.name());
                }
            }
            return filter(options, args[1]);
        }
        SubCommand rootCommand = root().find(args[0]);
        if (rootCommand != null) {
            return rootCommand.canUse(sender) ? filter(rootCommand.complete(sender, Arrays.copyOfRange(args, 1, args.length)), args[args.length - 1]) : List.of();
        }
        CommandCategory category = category(args[0]);
        if (category == null || !category.visibleTo(sender)) {
            return List.of();
        }
        if (args.length == 2) {
            List<String> options = new ArrayList<>();
            for (SubCommand sub : category.visible(sender)) {
                options.add(sub.name());
            }
            return filter(options, args[1]);
        }
        SubCommand sub = category.find(args[1]);
        if (sub == null || !sub.canUse(sender)) {
            return List.of();
        }
        return filter(sub.complete(sender, Arrays.copyOfRange(args, 2, args.length)), args[args.length - 1]);
    }

    public static List<String> filter(List<String> options, String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }

    public String label() {
        return label;
    }
}
