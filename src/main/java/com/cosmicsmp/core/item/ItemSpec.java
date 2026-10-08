package com.cosmicsmp.core.item;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Fully configurable item definition used by menus, star items and display models.
 * <pre>
 * material: NETHER_STAR            # or itemsadder:namespace:id / ia:namespace:id / mythic:ItemId
 * amount: 1
 * name: "<gradient:#22d3ee:#a855f7>Sonic Star"
 * lore: ["<gray>line 1", "{placeholder}"]
 * custom-model-data: 1001           # number (floats[0]) - legacy resource packs
 * custom-model-data-strings: ["sonic"]   # 1.21.4+ string selectors
 * item-model: "cosmic:sonic_star"   # 1.21.4+ item_model component
 * tooltip-style: "cosmic:star"      # optional custom tooltip sprite
 * glow: true
 * unbreakable: false
 * hide-flags: true
 * hide-tooltip: false
 * color: "#22d3ee"                  # leather armour / potions
 * skull-texture: "base64..."        # player heads
 * skull-owner: "{player}"           # online player head
 * enchantments: {sharpness: 1}
 * </pre>
 * Placeholders {@code {key}} are resolved at build time; a placeholder whose value contains new lines expands
 * into several lore lines.
 */
public final class ItemSpec {

    private final String material;
    private final int amount;
    private final String name;
    private final List<String> lore;
    private final Float customModelData;
    private final List<String> modelStrings;
    private final String itemModel;
    private final String tooltipStyle;
    private final Boolean glow;
    private final boolean unbreakable;
    private final boolean hideFlags;
    private final boolean hideTooltip;
    private final String color;
    private final String skullTexture;
    private final String skullOwner;
    private final Map<String, Integer> enchantments;

    private ItemSpec(String material, int amount, String name, List<String> lore, Float customModelData,
                     List<String> modelStrings, String itemModel, String tooltipStyle, Boolean glow, boolean unbreakable,
                     boolean hideFlags, boolean hideTooltip, String color, String skullTexture, String skullOwner,
                     Map<String, Integer> enchantments) {
        this.material = material;
        this.amount = amount;
        this.name = name;
        this.lore = lore;
        this.customModelData = customModelData;
        this.modelStrings = modelStrings;
        this.itemModel = itemModel;
        this.tooltipStyle = tooltipStyle;
        this.glow = glow;
        this.unbreakable = unbreakable;
        this.hideFlags = hideFlags;
        this.hideTooltip = hideTooltip;
        this.color = color;
        this.skullTexture = skullTexture;
        this.skullOwner = skullOwner;
        this.enchantments = enchantments;
    }

    public static ItemSpec of(String material, String name, List<String> lore) {
        return new ItemSpec(material, 1, name, lore, null, List.of(), null, null, null, false, true, false,
                null, null, null, Map.of());
    }

    public static ItemSpec of(ConfigurationSection s) {
        return of(s, null);
    }

    /** Reads a spec; missing keys fall back to {@code base} (lets menus override only what they need). */
    public static ItemSpec of(ConfigurationSection s, ItemSpec base) {
        if (s == null) {
            return base == null ? of("STONE", null, List.of()) : base;
        }
        ItemSpec b = base == null ? of("STONE", null, List.of()) : base;
        Map<String, Integer> enchants = new LinkedHashMap<>(b.enchantments);
        ConfigurationSection ench = s.getConfigurationSection("enchantments");
        if (ench != null) {
            enchants.clear();
            for (String key : ench.getKeys(false)) {
                enchants.put(key, ench.getInt(key, 1));
            }
        }
        Float cmd = b.customModelData;
        if (s.isSet("custom-model-data")) {
            double value = s.getDouble("custom-model-data");
            cmd = value == 0 ? null : (float) value;
        }
        return new ItemSpec(
                s.getString("material", b.material),
                Math.max(1, s.getInt("amount", b.amount)),
                s.isSet("name") ? s.getString("name") : b.name,
                s.isSet("lore") ? s.getStringList("lore") : b.lore,
                cmd,
                s.isSet("custom-model-data-strings") ? s.getStringList("custom-model-data-strings") : b.modelStrings,
                blankToNull(s.getString("item-model", b.itemModel)),
                blankToNull(s.getString("tooltip-style", b.tooltipStyle)),
                s.isSet("glow") ? Boolean.valueOf(s.getBoolean("glow")) : b.glow,
                s.getBoolean("unbreakable", b.unbreakable),
                s.getBoolean("hide-flags", b.hideFlags),
                s.getBoolean("hide-tooltip", b.hideTooltip),
                blankToNull(s.getString("color", b.color)),
                blankToNull(s.getString("skull-texture", b.skullTexture)),
                blankToNull(s.getString("skull-owner", b.skullOwner)),
                enchants);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public ItemSpec withName(String newName) {
        return new ItemSpec(material, amount, newName, lore, customModelData, modelStrings, itemModel, tooltipStyle, glow,
                unbreakable, hideFlags, hideTooltip, color, skullTexture, skullOwner, enchantments);
    }

    public ItemSpec withLore(List<String> newLore) {
        return new ItemSpec(material, amount, name, newLore, customModelData, modelStrings, itemModel, tooltipStyle, glow,
                unbreakable, hideFlags, hideTooltip, color, skullTexture, skullOwner, enchantments);
    }

    public ItemSpec withGlow(Boolean newGlow) {
        return new ItemSpec(material, amount, name, lore, customModelData, modelStrings, itemModel, tooltipStyle, newGlow,
                unbreakable, hideFlags, hideTooltip, color, skullTexture, skullOwner, enchantments);
    }

    public ItemSpec withAmount(int newAmount) {
        return new ItemSpec(material, Math.max(1, Math.min(99, newAmount)), name, lore, customModelData, modelStrings,
                itemModel, tooltipStyle, glow, unbreakable, hideFlags, hideTooltip, color, skullTexture, skullOwner, enchantments);
    }

    public String material() {
        return material;
    }

    public String name() {
        return name;
    }

    public List<String> lore() {
        return lore;
    }

    public ItemStack build(CosmicSMP plugin, Placeholders ph, Player viewer) {
        Placeholders placeholders = ph == null ? Placeholders.empty() : ph;
        ItemStack item = plugin.hooks().resolveItem(placeholders.apply(material));
        item.setAmount(Math.max(1, Math.min(item.getMaxStackSize(), amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        if (name != null) {
            meta.displayName(Text.item(placeholders.apply(name)));
        }
        if (lore != null && !lore.isEmpty()) {
            meta.lore(lore(lore, placeholders));
        }
        if (customModelData != null || !modelStrings.isEmpty()) {
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            if (customModelData != null) {
                component.setFloats(List.of(customModelData));
            }
            if (!modelStrings.isEmpty()) {
                component.setStrings(modelStrings);
            }
            meta.setCustomModelDataComponent(component);
        }
        if (itemModel != null) {
            NamespacedKey key = NamespacedKey.fromString(itemModel.toLowerCase(Locale.ROOT));
            if (key != null) {
                meta.setItemModel(key);
            }
        }
        if (tooltipStyle != null) {
            NamespacedKey key = NamespacedKey.fromString(tooltipStyle.toLowerCase(Locale.ROOT));
            if (key != null) {
                meta.setTooltipStyle(key);
            }
        }
        if (glow != null) {
            meta.setEnchantmentGlintOverride(glow);
        }
        if (unbreakable) {
            meta.setUnbreakable(true);
        }
        for (Map.Entry<String, Integer> entry : enchantments.entrySet()) {
            NamespacedKey key = NamespacedKey.fromString(entry.getKey().toLowerCase(Locale.ROOT));
            Enchantment enchantment = key == null ? null
                    : RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT).get(key);
            if (enchantment != null) {
                meta.addEnchant(enchantment, entry.getValue(), true);
            }
        }
        if (color != null) {
            Color c = ParticleSpec.color(placeholders.apply(color), Color.WHITE);
            if (meta instanceof LeatherArmorMeta leather) {
                leather.setColor(c);
            } else if (meta instanceof PotionMeta potion) {
                potion.setColor(c);
            }
        }
        if (meta instanceof SkullMeta skull) {
            applySkull(skull, placeholders, viewer);
        }
        if (hideFlags) {
            meta.addItemFlags(ItemFlag.values());
        }
        if (hideTooltip) {
            meta.setHideTooltip(true);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void applySkull(SkullMeta skull, Placeholders ph, Player viewer) {
        if (skullTexture != null) {
            String texture = ph.apply(skullTexture);
            PlayerProfile profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(texture.getBytes(StandardCharsets.UTF_8)), null);
            profile.setProperty(new ProfileProperty("textures", texture));
            skull.setPlayerProfile(profile);
        } else if (skullOwner != null) {
            String owner = ph.apply(skullOwner);
            Player online = Bukkit.getPlayerExact(owner);
            if (online == null && viewer != null && viewer.getName().equalsIgnoreCase(owner)) {
                online = viewer;
            }
            if (online != null) {
                skull.setPlayerProfile(online.getPlayerProfile());
            }
        }
    }

    private static List<Component> lore(List<String> lines, Placeholders ph) {
        List<Component> out = new ArrayList<>(lines.size() + 4);
        for (String raw : lines) {
            String line = ph.apply(raw);
            String trimmed = raw.trim();
            if (line.isEmpty() && trimmed.length() > 2 && trimmed.charAt(0) == '{' && trimmed.endsWith("}")) {
                continue; // a placeholder-only line that resolved to nothing disappears
            }
            if (line.indexOf('\n') >= 0) {
                for (String part : line.split("\n", -1)) {
                    out.add(Text.item(part));
                }
            } else {
                out.add(Text.item(line));
            }
        }
        return Collections.unmodifiableList(out);
    }
}
