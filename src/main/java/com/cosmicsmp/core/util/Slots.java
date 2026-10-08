package com.cosmicsmp.core.util;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

/** Parses slot definitions: {@code slot: 4}, {@code slots: [0, 1, "9-17", "45..53"]}. */
public final class Slots {

    public static final int SIZE = 54;

    private Slots() {
    }

    public static List<Integer> read(ConfigurationSection section, String singleKey, String listKey) {
        List<Integer> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        if (section.isList(listKey)) {
            for (Object raw : section.getList(listKey, List.of())) {
                parse(String.valueOf(raw), out);
            }
        } else if (section.isSet(listKey)) {
            parse(section.getString(listKey, ""), out);
        }
        if (section.isSet(singleKey)) {
            parse(section.getString(singleKey, ""), out);
        }
        return out;
    }

    public static List<Integer> list(ConfigurationSection section, String key) {
        List<Integer> out = new ArrayList<>();
        if (section == null) {
            return out;
        }
        if (section.isList(key)) {
            for (Object raw : section.getList(key, List.of())) {
                parse(String.valueOf(raw), out);
            }
        } else if (section.isSet(key)) {
            parse(section.getString(key, ""), out);
        }
        return out;
    }

    private static void parse(String raw, List<Integer> out) {
        for (String part : raw.split(",")) {
            String token = part.trim();
            if (token.isEmpty()) {
                continue;
            }
            String[] range = token.contains("..") ? token.split("\\.\\.") : token.split("-");
            try {
                if (range.length == 2) {
                    int from = Integer.parseInt(range[0].trim());
                    int to = Integer.parseInt(range[1].trim());
                    for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
                        add(out, i);
                    }
                } else {
                    add(out, Integer.parseInt(token));
                }
            } catch (NumberFormatException ignored) {
                // invalid entries are skipped
            }
        }
    }

    private static void add(List<Integer> out, int slot) {
        if (slot >= 0 && slot < SIZE && !out.contains(slot)) {
            out.add(slot);
        }
    }
}
