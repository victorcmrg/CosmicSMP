package com.cosmicsmp.core.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Text pipeline used everywhere (chat, titles, action bars, items, menus, holograms).
 * <p>
 * Accepted formats, freely mixed in a single string:
 * <ul>
 *     <li>MiniMessage: {@code <gradient:#a855f7:#22d3ee>text</gradient>}, {@code <#ff00aa>}, {@code <bold>} ...</li>
 *     <li>Hex shortcuts: {@code &#ff00aa}, {@code {#ff00aa}} and the Spigot {@code &x&f&f&0&0&a&a} form.</li>
 *     <li>Legacy codes: {@code &a}, {@code &l}, {@code &r} (also with {@code §}).</li>
 * </ul>
 */
public final class Text {

    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern SPIGOT_HEX = Pattern.compile("[&§]x((?:[&§][A-Fa-f0-9]){6})");
    private static final Pattern HEX = Pattern.compile("[&§]#([A-Fa-f0-9]{6})|\\{#([A-Fa-f0-9]{6})}");
    private static final Pattern LEGACY = Pattern.compile("[&§]([0-9a-fk-orA-FK-OR])");
    private static final Map<Character, String> LEGACY_TAGS = Map.ofEntries(
            Map.entry('0', "<black>"), Map.entry('1', "<dark_blue>"), Map.entry('2', "<dark_green>"),
            Map.entry('3', "<dark_aqua>"), Map.entry('4', "<dark_red>"), Map.entry('5', "<dark_purple>"),
            Map.entry('6', "<gold>"), Map.entry('7', "<gray>"), Map.entry('8', "<dark_gray>"),
            Map.entry('9', "<blue>"), Map.entry('a', "<green>"), Map.entry('b', "<aqua>"),
            Map.entry('c', "<red>"), Map.entry('d', "<light_purple>"), Map.entry('e', "<yellow>"),
            Map.entry('f', "<white>"), Map.entry('k', "<obfuscated>"), Map.entry('l', "<bold>"),
            Map.entry('m', "<strikethrough>"), Map.entry('n', "<underlined>"), Map.entry('o', "<italic>"),
            Map.entry('r', "<reset>"));

    private static final int CACHE_LIMIT = 2048;
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private Text() {
    }

    /** Converts hex / legacy shortcuts into MiniMessage tags. */
    public static String toMiniMessage(String input) {
        if (input.indexOf('&') < 0 && input.indexOf('§') < 0 && !input.contains("{#")) {
            return input;
        }
        String out = replace(SPIGOT_HEX, input, m -> "<#" + m.group(1).replaceAll("[&§]", "") + ">");
        out = replace(HEX, out, m -> "<#" + (m.group(1) != null ? m.group(1) : m.group(2)) + ">");
        return replace(LEGACY, out, m -> LEGACY_TAGS.get(Character.toLowerCase(m.group(1).charAt(0))));
    }

    private static String replace(Pattern pattern, String input, java.util.function.Function<Matcher, String> fn) {
        Matcher matcher = pattern.matcher(input);
        if (!matcher.find()) {
            return input;
        }
        StringBuilder sb = new StringBuilder(input.length() + 16);
        do {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(fn.apply(matcher)));
        } while (matcher.find());
        matcher.appendTail(sb);
        return sb.toString();
    }

    public static Component parse(String input) {
        if (input == null || input.isEmpty()) {
            return Component.empty();
        }
        Component cached = CACHE.get(input);
        if (cached != null) {
            return cached;
        }
        Component parsed;
        try {
            parsed = MINI.deserialize(toMiniMessage(input));
        } catch (RuntimeException ex) {
            parsed = Component.text(input);
        }
        if (CACHE.size() >= CACHE_LIMIT) {
            CACHE.clear();
        }
        CACHE.put(input, parsed);
        return parsed;
    }

    public static Component parse(String input, Placeholders placeholders) {
        return parse(placeholders == null ? input : placeholders.apply(input));
    }

    /** Parses text for item names / lore: italic is disabled unless the text sets it. */
    public static Component item(String input) {
        return parse(input).decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }

    public static List<Component> itemLines(List<String> lines) {
        List<Component> out = new ArrayList<>(lines.size());
        for (String line : lines) {
            out.add(item(line));
        }
        return out;
    }

    public static String plain(Component component) {
        return PlainTextComponentSerializer.plainText().serialize(component);
    }

    /** Escapes MiniMessage tags in untrusted input (player names, chat). */
    public static String escape(String input) {
        return MINI.escapeTags(input);
    }

    public static void clearCache() {
        CACHE.clear();
    }
}
