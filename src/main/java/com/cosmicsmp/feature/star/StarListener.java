package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.menu.CosmicMenu;
import com.cosmicsmp.core.text.Placeholders;
import com.destroystokyo.paper.event.inventory.PrepareResultEvent;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Star item controls and protections.
 * <ul>
 *     <li>Right click: cast the selected primary.</li>
 *     <li>F (swap hands) or Sneak + Right click (configurable): cycle the selected primary.</li>
 *     <li>Soulbound: cannot be dropped, stored, framed, crafted, placed or moved into any foreign inventory.</li>
 *     <li>Death: stars evaporate (removed from drops and data).</li>
 * </ul>
 */
public final class StarListener implements Listener {

    private final CosmicSMP plugin;
    private final Map<UUID, Long> lastUse = new HashMap<>();

    public StarListener(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------ controls

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        String starId = StarItemService.starId(item);
        if (starId == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(StarItemService.owner(item))) {
            plugin.starItems().sanitize(player);
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastUse.get(player.getUniqueId());
        if (last != null && now - last < plugin.settings().castThrottleMillis) {
            return;
        }
        lastUse.put(player.getUniqueId(), now);
        if (!player.hasPermission("cosmicsmp.ability.use")) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        if (player.isSneaking() && plugin.settings().cycleControl == Settings.CycleControl.SNEAK_RIGHT_CLICK) {
            cycle(player, starId);
            return;
        }
        plugin.casts().castSelected(player, starId);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        ItemStack main = event.getPlayer().getInventory().getItemInMainHand();
        String starId = StarItemService.starId(main);
        String offStar = StarItemService.starId(event.getPlayer().getInventory().getItemInOffHand());
        if (starId == null && offStar == null) {
            return;
        }
        event.setCancelled(true); // star items never go to the off hand
        if (starId != null && !event.getPlayer().isSneaking() && plugin.settings().cycleControl == Settings.CycleControl.SWAP_HAND) {
            cycle(event.getPlayer(), starId);
        }
    }

    private void cycle(Player player, String starId) {
        StarDefinition star = plugin.stars().get(starId);
        if (star == null) {
            return;
        }
        int slot = plugin.starService().cycle(player, star);
        if (slot == 0) {
            plugin.messages().actionBar(player, "abilities.none-unlocked", Placeholders.of("star", star.displayName()));
            return;
        }
        AbilityDefinition def = star.ability(slot);
        plugin.hud().hold(player, 1200);
        plugin.messages().send(player, "abilities.selected", Placeholders.of("ability", def.name(), "slot", slot, "star", star.displayName()));
    }

    // ------------------------------------------------------------------ soulbound protections

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (StarItemService.isStar(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            plugin.messages().actionBar(event.getPlayer(), "stars.soulbound", Placeholders.empty());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        InventoryView view = event.getView();
        if (view.getTopInventory().getHolder(false) instanceof CosmicMenu) {
            return; // menus cancel everything already
        }
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        boolean starInvolved = StarItemService.isStar(current) || StarItemService.isStar(cursor);
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            starInvolved |= StarItemService.isStar(event.getWhoClicked().getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            starInvolved |= StarItemService.isStar(event.getWhoClicked().getInventory().getItemInOffHand());
        }
        if (!starInvolved) {
            return;
        }
        // bundles could carry the star out of the inventory
        if (isBundle(current) || isBundle(cursor)) {
            event.setCancelled(true);
            return;
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND || event.getSlotType() == InventoryType.SlotType.ARMOR
                || (event.getClickedInventory() == event.getWhoClicked().getInventory() && event.getSlot() == 40)) {
            event.setCancelled(true);
            return;
        }
        if (isForeign(view)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!StarItemService.isStar(event.getOldCursor())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        boolean foreign = isForeign(event.getView());
        for (int raw : event.getRawSlots()) {
            if (foreign && raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private static boolean isForeign(InventoryView view) {
        InventoryType type = view.getTopInventory().getType();
        return type != InventoryType.CRAFTING && type != InventoryType.PLAYER;
    }

    private static boolean isBundle(ItemStack item) {
        return item != null && !item.isEmpty() && (item.getType() == Material.BUNDLE || item.getType().name().endsWith("_BUNDLE"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (StarItemService.isStar(event.getItemInHand())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (StarItemService.isStar(hand)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (StarItemService.isStar(event.getPlayerItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraft(PrepareItemCraftEvent event) {
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (StarItemService.isStar(item)) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareResult(PrepareResultEvent event) {
        for (ItemStack item : event.getInventory().getContents()) {
            if (StarItemService.isStar(item)) {
                event.setResult(null);
                return;
            }
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        event.getDrops().removeIf(StarItemService::isStar);
        event.getItemsToKeep().removeIf(StarItemService::isStar);
        if (!plugin.settings().evaporateOnDeath) {
            return;
        }
        plugin.starItems().removeStar(player, null);
        Set<String> lost = plugin.starService().evaporate(player, player.getLocation());
        if (!lost.isEmpty()) {
            plugin.messages().send(player, "stars.evaporated", Placeholders.of("count", lost.size()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.starItems().sanitize(player);
            }
        }, 2L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                plugin.starItems().sanitize(player);
            }
        }, 3L);
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastUse.remove(event.getPlayer().getUniqueId());
    }
}
