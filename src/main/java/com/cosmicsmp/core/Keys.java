package com.cosmicsmp.core;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;

/**
 * Every {@link NamespacedKey} used by the plugin (PDC tags, attribute modifiers, cooldown groups).
 * Centralised so expansions never collide with core keys.
 */
public final class Keys {

    public static NamespacedKey STAR_ID;
    public static NamespacedKey STAR_OWNER;
    public static NamespacedKey MERCHANT;
    public static NamespacedKey SUMMON_OWNER;
    public static NamespacedKey TEMP_ENTITY;
    public static NamespacedKey FLOWER_VITALITY;
    public static NamespacedKey STUN;

    private static Plugin plugin;

    private Keys() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
        STAR_ID = key("star_id");
        STAR_OWNER = key("star_owner");
        MERCHANT = key("merchant");
        SUMMON_OWNER = key("summon_owner");
        TEMP_ENTITY = key("temp_entity");
        FLOWER_VITALITY = key("flower_vitality");
        STUN = key("stun");
    }

    public static NamespacedKey key(String value) {
        return new NamespacedKey(plugin, value);
    }

    /** Client cooldown group shared by every copy of a star item (drives the native cooldown overlay). */
    public static NamespacedKey starCooldownGroup(String starId) {
        return key("star/" + starId);
    }
}
