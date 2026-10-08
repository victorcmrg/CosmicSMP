package com.cosmicsmp.feature.star;

import com.cosmicsmp.core.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** A star loaded from {@code stars/<id>.yml}. Adding a new star = dropping a new file (plus its abilities). */
public final class StarDefinition {

    public static final int PRIMARY_SLOTS = 3;

    private final String id;
    private final boolean enabled;
    private final int order;
    private final String displayName;
    private final String color;
    private final int price;
    private final int defaultStock;
    private final List<String> description;
    private final ItemSpec item;
    private final ItemSpec icon;
    private final int merchantSlot;
    private final String model;
    private final AbilityDefinition passive;
    private final AbilityDefinition[] primaries = new AbilityDefinition[PRIMARY_SLOTS];

    public StarDefinition(String fileId, YamlConfiguration c) {
        this.id = c.getString("id", fileId).toLowerCase(Locale.ROOT);
        this.enabled = c.getBoolean("enabled", true);
        this.order = c.getInt("order", 100);
        this.displayName = c.getString("display-name", id);
        this.color = c.getString("color", "#ffffff");
        this.price = Math.max(0, c.getInt("price", 6));
        this.defaultStock = c.getInt("stock", -1);
        this.description = c.getStringList("description");
        this.item = ItemSpec.of(c.getConfigurationSection("item"), ItemSpec.of("NETHER_STAR", displayName, List.of()));
        this.icon = ItemSpec.of(c.getConfigurationSection("icon"), item);
        this.merchantSlot = c.getInt("merchant-slot", -1);
        this.model = c.getString("model", "");
        ConfigurationSection passiveSection = c.getConfigurationSection("passive");
        this.passive = passiveSection == null ? null : new AbilityDefinition(id, 0, passiveSection);
        ConfigurationSection primarySection = c.getConfigurationSection("primaries");
        if (primarySection != null) {
            for (int slot = 1; slot <= PRIMARY_SLOTS; slot++) {
                ConfigurationSection s = primarySection.getConfigurationSection(String.valueOf(slot));
                if (s != null) {
                    primaries[slot - 1] = new AbilityDefinition(id, slot, s);
                }
            }
        }
    }

    public String id() {
        return id;
    }

    public boolean enabled() {
        return enabled;
    }

    public int order() {
        return order;
    }

    public String displayName() {
        return displayName;
    }

    public String color() {
        return color;
    }

    public int price() {
        return price;
    }

    public int defaultStock() {
        return defaultStock;
    }

    public List<String> description() {
        return description;
    }

    public ItemSpec item() {
        return item;
    }

    public ItemSpec icon() {
        return icon;
    }

    public int merchantSlot() {
        return merchantSlot;
    }

    public String model() {
        return model;
    }

    public AbilityDefinition passive() {
        return passive;
    }

    /** @param slot 0 = passive, 1..3 = primaries */
    public AbilityDefinition ability(int slot) {
        if (slot == 0) {
            return passive;
        }
        return slot >= 1 && slot <= PRIMARY_SLOTS ? primaries[slot - 1] : null;
    }

    public List<AbilityDefinition> primaries() {
        List<AbilityDefinition> list = new ArrayList<>(PRIMARY_SLOTS);
        for (AbilityDefinition def : primaries) {
            if (def != null) {
                list.add(def);
            }
        }
        return Collections.unmodifiableList(list);
    }

    public List<AbilityDefinition> all() {
        List<AbilityDefinition> list = new ArrayList<>(PRIMARY_SLOTS + 1);
        if (passive != null) {
            list.add(passive);
        }
        list.addAll(primaries());
        return list;
    }
}
