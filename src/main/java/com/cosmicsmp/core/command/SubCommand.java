package com.cosmicsmp.core.command;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A leaf command, e.g. {@code /cosmic brightness set <player> <amount>}.
 * Description/usage text lives in messages.yml ({@code help.commands.<category>.<name>}) so it can be translated.
 */
public final class SubCommand {

    @FunctionalInterface
    public interface Executor {
        void execute(CommandSender sender, String[] args);
    }

    @FunctionalInterface
    public interface Completer {
        List<String> complete(CommandSender sender, String[] args);
    }

    private final String name;
    private final List<String> aliases;
    private final String permission;
    private final String usage;
    private final boolean playerOnly;
    private final int minArgs;
    private final Executor executor;
    private final Completer completer;

    private SubCommand(Builder b) {
        this.name = b.name;
        this.aliases = List.copyOf(b.aliases);
        this.permission = b.permission;
        this.usage = b.usage;
        this.playerOnly = b.playerOnly;
        this.minArgs = b.minArgs;
        this.executor = b.executor;
        this.completer = b.completer;
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public List<String> aliases() {
        return aliases;
    }

    public String permission() {
        return permission;
    }

    public String usage() {
        return usage;
    }

    public boolean playerOnly() {
        return playerOnly;
    }

    public int minArgs() {
        return minArgs;
    }

    public boolean matches(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return name.equals(lower) || aliases.contains(lower);
    }

    public boolean canUse(CommandSender sender) {
        return (permission == null || permission.isEmpty() || sender.hasPermission(permission))
                && (!playerOnly || sender instanceof Player);
    }

    public void execute(CommandSender sender, String[] args) {
        executor.execute(sender, args);
    }

    public List<String> complete(CommandSender sender, String[] args) {
        return completer == null ? List.of() : completer.complete(sender, args);
    }

    public static final class Builder {
        private final String name;
        private final List<String> aliases = new ArrayList<>();
        private String permission = "";
        private String usage = "";
        private boolean playerOnly;
        private int minArgs;
        private Executor executor = (s, a) -> { };
        private Completer completer;

        private Builder(String name) {
            this.name = name.toLowerCase(Locale.ROOT);
        }

        public Builder aliases(String... values) {
            for (String v : values) {
                aliases.add(v.toLowerCase(Locale.ROOT));
            }
            return this;
        }

        public Builder permission(String value) {
            this.permission = value;
            return this;
        }

        public Builder usage(String value) {
            this.usage = value;
            return this;
        }

        public Builder playerOnly() {
            this.playerOnly = true;
            return this;
        }

        public Builder minArgs(int value) {
            this.minArgs = value;
            return this;
        }

        public Builder executor(Executor value) {
            this.executor = value;
            return this;
        }

        public Builder completer(Completer value) {
            this.completer = value;
            return this;
        }

        public SubCommand build() {
            return new SubCommand(this);
        }
    }
}
