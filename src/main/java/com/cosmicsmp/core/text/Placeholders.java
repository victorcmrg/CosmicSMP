package com.cosmicsmp.core.text;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tiny {key} replacement table applied to raw strings before they are parsed by MiniMessage.
 * Values are inserted verbatim, so they may carry their own colours / tags.
 */
public final class Placeholders {

    private static final Placeholders EMPTY = new Placeholders(Map.of());

    private final Map<String, String> values;

    private Placeholders(Map<String, String> values) {
        this.values = values;
    }

    public static Placeholders empty() {
        return EMPTY;
    }

    public static Placeholders of(Object... pairs) {
        Placeholders ph = new Placeholders(new LinkedHashMap<>());
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            ph.values.put(String.valueOf(pairs[i]), String.valueOf(pairs[i + 1]));
        }
        return ph;
    }

    /** Returns a mutable copy with the extra value set. */
    public Placeholders with(String key, Object value) {
        Placeholders copy = new Placeholders(new LinkedHashMap<>(values));
        copy.values.put(key, String.valueOf(value));
        return copy;
    }

    public Placeholders merge(Placeholders other) {
        if (other == null || other.values.isEmpty()) {
            return this;
        }
        Placeholders copy = new Placeholders(new LinkedHashMap<>(values));
        copy.values.putAll(other.values);
        return copy;
    }

    public String get(String key) {
        return values.get(key);
    }

    public String apply(String input) {
        if (input == null || values.isEmpty() || input.indexOf('{') < 0) {
            return input;
        }
        StringBuilder out = new StringBuilder(input.length() + 16);
        int i = 0;
        int length = input.length();
        while (i < length) {
            char c = input.charAt(i);
            if (c == '{') {
                int end = input.indexOf('}', i + 1);
                if (end > i + 1) {
                    String value = values.get(input.substring(i + 1, end));
                    if (value != null) {
                        out.append(value);
                        i = end + 1;
                        continue;
                    }
                }
            }
            out.append(c);
            i++;
        }
        return out.toString();
    }
}
