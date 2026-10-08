package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.api.event.AbilityUnlockEvent;
import com.cosmicsmp.api.event.BrightnessChangeEvent;
import com.cosmicsmp.api.event.StarEvaporateEvent;
import com.cosmicsmp.api.event.StarPurchaseEvent;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.LinkedHashSet;
import java.util.Set;

/** Buying, unlocking, selecting and losing stars. All state changes are saved immediately. */
public final class StarService {

    public enum Result {
        SUCCESS, ALREADY_OWNED, NOT_OWNED, OUT_OF_STOCK, NOT_ENOUGH_BRIGHTNESS, INVENTORY_FULL,
        ALREADY_UNLOCKED, REQUIRES_PREVIOUS, NOT_AVAILABLE, CANCELLED
    }

    private final CosmicSMP plugin;

    public StarService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    /** What would happen if the player tried to buy this star now (used to colour menu icons). */
    public Result checkPurchase(Player player, StarDefinition star) {
        PlayerData data = plugin.players().get(player);
        if (data.owns(star.id())) {
            return Result.ALREADY_OWNED;
        }
        if (!data.discovered.contains(star.id()) && !plugin.stock().inStock(star)) {
            return Result.OUT_OF_STOCK;
        }
        if (data.brightness < star.price()) {
            return Result.NOT_ENOUGH_BRIGHTNESS;
        }
        return Result.SUCCESS;
    }

    public Result purchase(Player player, StarDefinition star) {
        Result check = checkPurchase(player, star);
        if (check != Result.SUCCESS) {
            return check;
        }
        if (!plugin.starItems().hasSpace(player)) {
            return Result.INVENTORY_FULL;
        }
        PlayerData data = plugin.players().get(player);
        boolean first = !data.discovered.contains(star.id());
        StarPurchaseEvent event = new StarPurchaseEvent(player, star.id(), star.price(), first);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.CANCELLED;
        }
        int expected = data.brightness - star.price();
        if (plugin.brightness().set(player, expected, BrightnessChangeEvent.Cause.PURCHASE) != expected) {
            return Result.CANCELLED; // another plugin blocked the payment
        }
        if (first) {
            plugin.stock().consume(star);
            data.discovered.add(star.id());
        }
        data.stars.put(star.id(), new PlayerData.OwnedStar(System.currentTimeMillis()));
        data.stats.starsBought++;
        data.stats.brightnessSpent += star.price();
        plugin.players().saveSoon(data);
        plugin.starItems().give(player, star);
        plugin.passives().refresh(player);
        plugin.hud().invalidate(player);
        Placeholders ph = Placeholders.of("star", star.displayName(), "price", star.price(), "player", player.getName())
                .merge(plugin.brightness().placeholders(player, data.brightness));
        plugin.messages().send(player, "stars.purchased", ph);
        if (first) {
            plugin.messages().broadcast("stars.purchased-broadcast", ph);
        }
        if (plugin.settings().starAnimation) {
            StarAnimations.acquired(plugin, player, star, plugin.starItems().create(player, star, data.star(star.id())));
        }
        return Result.SUCCESS;
    }

    public Result checkUnlock(Player player, StarDefinition star, int slot) {
        PlayerData data = plugin.players().get(player);
        PlayerData.OwnedStar owned = data.star(star.id());
        AbilityDefinition def = star.ability(slot);
        if (def == null) {
            return Result.NOT_AVAILABLE;
        }
        if (owned == null) {
            return Result.NOT_OWNED;
        }
        if (owned.unlocked(slot)) {
            return Result.ALREADY_UNLOCKED;
        }
        if (plugin.settings().treeMode == Settings.TreeMode.SEQUENTIAL && slot > 0) {
            boolean previous = slot == 1 ? (star.passive() == null || owned.passive) : owned.unlocked(slot - 1);
            if (!previous) {
                return Result.REQUIRES_PREVIOUS;
            }
        }
        if (data.brightness < def.cost()) {
            return Result.NOT_ENOUGH_BRIGHTNESS;
        }
        return Result.SUCCESS;
    }

    public Result unlock(Player player, StarDefinition star, int slot) {
        Result check = checkUnlock(player, star, slot);
        if (check != Result.SUCCESS) {
            return check;
        }
        AbilityDefinition def = star.ability(slot);
        AbilityUnlockEvent event = new AbilityUnlockEvent(player, star.id(), slot, def.cost());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return Result.CANCELLED;
        }
        PlayerData data = plugin.players().get(player);
        PlayerData.OwnedStar owned = data.star(star.id());
        int expected = data.brightness - def.cost();
        if (def.cost() > 0 && plugin.brightness().set(player, expected, BrightnessChangeEvent.Cause.PURCHASE) != expected) {
            return Result.CANCELLED;
        }
        if (slot == 0) {
            owned.passive = true;
        } else {
            owned.primaries.add(slot);
            if (owned.selected == 0) {
                owned.selected = slot;
            }
        }
        data.stats.abilitiesUnlocked++;
        data.stats.brightnessSpent += def.cost();
        plugin.players().saveSoon(data);
        plugin.starItems().refresh(player, star.id());
        plugin.passives().refresh(player);
        plugin.hud().invalidate(player);
        Placeholders ph = Placeholders.of("star", star.displayName(), "ability", def.name(), "cost", def.cost())
                .merge(plugin.brightness().placeholders(player, data.brightness));
        plugin.messages().send(player, slot == 0 ? "stars.passive-unlocked" : "stars.primary-unlocked", ph);
        StarAnimations.unlocked(plugin, player, star);
        return Result.SUCCESS;
    }

    public boolean select(Player player, StarDefinition star, int slot) {
        PlayerData.OwnedStar owned = plugin.players().get(player).star(star.id());
        if (owned == null || slot < 1 || !owned.primaries.contains(slot)) {
            return false;
        }
        owned.selected = slot;
        plugin.players().get(player).dirty = true;
        plugin.starItems().refresh(player, star.id());
        plugin.hud().invalidate(player);
        return true;
    }

    /** Selects the next unlocked primary. Returns the new slot or 0. */
    public int cycle(Player player, StarDefinition star) {
        PlayerData.OwnedStar owned = plugin.players().get(player).star(star.id());
        if (owned == null || owned.primaries.isEmpty()) {
            return 0;
        }
        int next = 0;
        for (int i = 1; i <= StarDefinition.PRIMARY_SLOTS; i++) {
            int candidate = ((owned.selected - 1 + i) % StarDefinition.PRIMARY_SLOTS) + 1;
            if (owned.primaries.contains(candidate)) {
                next = candidate;
                break;
            }
        }
        if (next != 0) {
            select(player, star, next);
        }
        return next;
    }

    /** Death: every owned star evaporates (stays "discovered" so it can be rebought without stock). */
    public Set<String> evaporate(Player player, Location where) {
        PlayerData data = plugin.players().get(player);
        if (data.stars.isEmpty()) {
            return Set.of();
        }
        Set<String> lost = new LinkedHashSet<>(data.stars.keySet());
        if (plugin.settings().resetAbilitiesOnDeath) {
            data.stars.clear();
        }
        plugin.players().saveSoon(data);
        plugin.passives().refresh(player);
        plugin.hud().invalidate(player);
        StarAnimations.evaporated(plugin, where);
        Bukkit.getPluginManager().callEvent(new StarEvaporateEvent(player, lost));
        return lost;
    }

    /** Admin grant (no cost, no stock). {@code full} also unlocks every node. */
    public void grant(Player player, StarDefinition star, boolean full) {
        PlayerData data = plugin.players().get(player);
        PlayerData.OwnedStar owned = data.stars.computeIfAbsent(star.id(), id -> new PlayerData.OwnedStar(System.currentTimeMillis()));
        data.discovered.add(star.id());
        if (full) {
            owned.passive = star.passive() != null;
            for (AbilityDefinition def : star.primaries()) {
                owned.primaries.add(def.slot());
            }
            if (owned.selected == 0 && !owned.primaries.isEmpty()) {
                owned.selected = owned.primaries.iterator().next();
            }
        }
        plugin.players().saveSoon(data);
        plugin.starItems().give(player, star);
        plugin.starItems().refresh(player, star.id());
        plugin.passives().refresh(player);
        plugin.hud().invalidate(player);
    }

    public boolean revoke(Player player, String starId) {
        PlayerData data = plugin.players().get(player);
        if (data.stars.remove(starId) == null) {
            return false;
        }
        plugin.starItems().removeStar(player, starId);
        plugin.players().saveSoon(data);
        plugin.passives().refresh(player);
        plugin.hud().invalidate(player);
        return true;
    }

    public String resultKey(Result result) {
        return "stars.result." + result.name().toLowerCase(java.util.Locale.ROOT).replace('_', '-');
    }
}
