package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.Keys;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.UseCooldownComponent;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The physical star item. It is soulbound (tagged with its owner), shows its unlocked abilities in the lore and
 * carries a per-star cooldown group so the client draws the native cooldown sweep for the selected ability
 * (a client-side animation that costs a single packet).
 */
public final class StarItemService {

    private final CosmicSMP plugin;

    public StarItemService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public ItemStack create(Player owner, StarDefinition star, PlayerData.OwnedStar owned) {
        ItemStack item = star.item().build(plugin, placeholders(owner, star, owned), owner);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(Keys.STAR_ID, PersistentDataType.STRING, star.id());
        meta.getPersistentDataContainer().set(Keys.STAR_OWNER, PersistentDataType.STRING, owner.getUniqueId().toString());
        UseCooldownComponent cooldown = meta.getUseCooldown();
        cooldown.setCooldownSeconds(1.0f);
        cooldown.setCooldownGroup(Keys.starCooldownGroup(star.id()));
        meta.setUseCooldown(cooldown);
        item.setItemMeta(meta);
        item.setAmount(1);
        return item;
    }

    public Placeholders placeholders(Player owner, StarDefinition star, PlayerData.OwnedStar owned) {
        StringBuilder abilities = new StringBuilder();
        String passiveOn = plugin.messages().raw("format.item.passive-unlocked", "<#86efac>✔ {name}");
        String passiveOff = plugin.messages().raw("format.item.passive-locked", "<#52525b>✖ {name}");
        String on = plugin.messages().raw("format.item.primary-unlocked", "<#86efac>✔ {name}");
        String off = plugin.messages().raw("format.item.primary-locked", "<#52525b>✖ {name}");
        String selected = plugin.messages().raw("format.item.primary-selected", "<#fde68a>▶ {name}");
        String selectedName = plugin.messages().raw("format.none", "none");
        for (AbilityDefinition def : star.all()) {
            boolean unlocked = owned != null && owned.unlocked(def.slot());
            String format;
            if (def.isPassive()) {
                format = unlocked ? passiveOn : passiveOff;
            } else if (unlocked && owned.selected == def.slot()) {
                format = selected;
                selectedName = def.name();
            } else {
                format = unlocked ? on : off;
            }
            if (!abilities.isEmpty()) {
                abilities.append('\n');
            }
            abilities.append(format.replace("{name}", def.name()).replace("{slot}", String.valueOf(def.slot())));
        }
        return plugin.menus().basePlaceholders(owner)
                .with("star", star.displayName())
                .with("star_name", star.displayName())
                .with("star_color", star.color())
                .with("owner", owner.getName())
                .with("abilities", abilities.toString())
                .with("selected", selectedName)
                .with("description", String.join("\n", star.description()))
                .with("controls", plugin.messages().raw(plugin.settings().cycleControl == Settings.CycleControl.SNEAK_RIGHT_CLICK
                        ? "format.item.controls-sneak" : "format.item.controls", "<#71717a>F to cycle"));
    }

    // ------------------------------------------------------------------ identification

    public static String starId(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.getPersistentDataContainer().get(Keys.STAR_ID, PersistentDataType.STRING);
    }

    public static boolean isStar(ItemStack item) {
        return starId(item) != null;
    }

    public static UUID owner(ItemStack item) {
        String raw = item.getPersistentDataContainer().get(Keys.STAR_OWNER, PersistentDataType.STRING);
        try {
            return raw == null ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    // ------------------------------------------------------------------ inventory ops

    public boolean hasSpace(Player player) {
        return player.getInventory().firstEmpty() >= 0;
    }

    public boolean give(Player player, StarDefinition star) {
        PlayerData.OwnedStar owned = plugin.players().get(player).star(star.id());
        if (find(player, star.id()) >= 0) {
            refresh(player, star.id());
            return true;
        }
        ItemStack item = create(player, star, owned);
        return player.getInventory().addItem(item).isEmpty();
    }

    /** Raw index in {@link PlayerInventory#getContents()} or -1. */
    public int find(Player player, String starId) {
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            if (starId.equals(starId(contents[i]))) {
                return i;
            }
        }
        return -1;
    }

    /** Rebuilds the star item in place (lore / selected ability changed). */
    public void refresh(Player player, String starId) {
        StarDefinition star = plugin.stars().get(starId);
        PlayerData.OwnedStar owned = plugin.players().get(player).star(starId);
        int slot = find(player, starId);
        if (star == null || owned == null || slot < 0) {
            return;
        }
        player.getInventory().setItem(slot, create(player, star, owned));
        updateCooldownOverlay(player, starId);
    }

    public int removeStar(Player player, String starId) {
        int removed = 0;
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            String id = starId(contents[i]);
            if (id != null && (starId == null || starId.equals(id))) {
                inventory.setItem(i, null);
                removed++;
            }
        }
        if (starId(player.getItemOnCursor()) != null && (starId == null || starId.equals(starId(player.getItemOnCursor())))) {
            player.setItemOnCursor(null);
            removed++;
        }
        return removed;
    }

    /**
     * Makes the inventory match the data: removes foreign / unowned / duplicate star items and (optionally) restores
     * missing ones. Called on join, respawn and after admin edits - closes every duplication route.
     */
    public void sanitize(Player player) {
        PlayerData data = plugin.players().get(player);
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        Set<String> seen = new HashSet<>();
        boolean changed = false;
        for (int i = 0; i < contents.length; i++) {
            String id = starId(contents[i]);
            if (id == null) {
                continue;
            }
            UUID owner = owner(contents[i]);
            if (!data.owns(id) || plugin.stars().get(id) == null || !player.getUniqueId().equals(owner) || !seen.add(id)) {
                inventory.setItem(i, null);
                changed = true;
            }
        }
        if (plugin.settings().autoRestoreItems) {
            for (String id : data.stars.keySet()) {
                StarDefinition star = plugin.stars().get(id);
                if (star != null && !seen.contains(id)) {
                    if (!give(player, star)) {
                        plugin.messages().send(player, "stars.recover-no-space", Placeholders.of("star", star.displayName()));
                    }
                    changed = true;
                }
            }
        }
        if (changed) {
            player.updateInventory();
        }
        for (String id : data.stars.keySet()) {
            updateCooldownOverlay(player, id);
        }
    }

    /** Shows the selected ability's remaining cooldown as the native item cooldown sweep. */
    public void updateCooldownOverlay(Player player, String starId) {
        StarDefinition star = plugin.stars().get(starId);
        PlayerData data = plugin.players().get(player);
        PlayerData.OwnedStar owned = data.star(starId);
        int slot = find(player, starId);
        if (star == null || owned == null || slot < 0) {
            return;
        }
        ItemStack item = player.getInventory().getContents()[slot];
        AbilityDefinition def = owned.selected == 0 ? null : star.ability(owned.selected);
        long left = def == null ? 0 : plugin.cooldowns().remainingMillis(data, def.key());
        player.setCooldown(item, (int) Math.min(Integer.MAX_VALUE, left / 50L));
    }
}
