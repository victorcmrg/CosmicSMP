package com.cosmicsmp.core.command;

import org.bukkit.command.CommandSender;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A group of sub commands, e.g. {@code /cosmic brightness ...}. A category without a name ("root") exposes its
 * commands directly under {@code /cosmic}. Expansions add categories through {@link CommandManager#register}.
 */
public final class CommandCategory {

    private final String name;
    private final List<String> aliases;
    private final String icon;
    private final Map<String, SubCommand> commands = new LinkedHashMap<>();

    public CommandCategory(String name, String icon, String... aliases) {
        this.name = name.toLowerCase(Locale.ROOT);
        this.icon = icon;
        List<String> list = new ArrayList<>();
        for (String alias : aliases) {
            list.add(alias.toLowerCase(Locale.ROOT));
        }
        this.aliases = List.copyOf(list);
    }

    public CommandCategory add(SubCommand command) {
        commands.put(command.name(), command);
        return this;
    }

    public String name() {
        return name;
    }

    public String icon() {
        return icon;
    }

    public boolean matches(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return name.equals(lower) || aliases.contains(lower);
    }

    public SubCommand find(String input) {
        for (SubCommand command : commands.values()) {
            if (command.matches(input)) {
                return command;
            }
        }
        return null;
    }

    public List<SubCommand> visible(CommandSender sender) {
        List<SubCommand> out = new ArrayList<>();
        for (SubCommand command : commands.values()) {
            if (command.canUse(sender)) {
                out.add(command);
            }
        }
        return out;
    }

    public boolean visibleTo(CommandSender sender) {
        return !visible(sender).isEmpty();
    }
}
