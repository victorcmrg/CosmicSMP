package com.cosmicsmp.core.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Base class of every menu. A menu is a 54-slot chest (GENERIC_9X6) whose static layout comes from YAML and whose
 * dynamic content is drawn by subclasses. Clicks are always cancelled (no item can ever be taken out) and routed
 * to per-slot handlers.
 */
public abstract class CosmicMenu implements InventoryHolder {

    protected final CosmicSMP plugin;
    protected final Player viewer;
    protected final MenuTemplate template;
    protected final CosmicMenu parent;
    private final Map<Integer, Consumer<InventoryClickEvent>> handlers = new HashMap<>();
    private Inventory inventory;

    protected CosmicMenu(CosmicSMP plugin, Player viewer, MenuTemplate template, CosmicMenu parent) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.template = template;
        this.parent = parent;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public Player viewer() {
        return viewer;
    }

    /** Placeholders available to every item and the title of this menu. */
    protected Placeholders placeholders() {
        return plugin.menus().basePlaceholders(viewer);
    }

    protected abstract void draw(Placeholders ph);

    public final void open() {
        Placeholders ph = placeholders();
        inventory = Bukkit.createInventory(this, 54, Text.parse(ph.apply(template.title())));
        render(ph);
        viewer.openInventory(inventory);
        template.openSound().play(viewer);
    }

    /** Redraws the contents in place (no close/reopen, so no cursor reset or title flicker). */
    public final void refresh() {
        if (inventory != null) {
            render(placeholders());
        }
    }

    private void render(Placeholders ph) {
        handlers.clear();
        ItemStack[] contents = new ItemStack[54];
        for (MenuButton button : template.buttons()) {
            if (!button.permission().isEmpty() && !viewer.hasPermission(button.permission())) {
                continue;
            }
            ItemStack item = button.item().build(plugin, ph, viewer);
            for (int slot : button.slots()) {
                contents[slot] = item;
                if (!button.actions().isEmpty()) {
                    handlers.put(slot, e -> {
                        button.clickSound().play(viewer);
                        plugin.menus().runActions(this, button.actions(), ph);
                    });
                }
            }
        }
        inventory.setContents(contents);
        draw(ph);
    }

    protected final void set(int slot, ItemStack item, Consumer<InventoryClickEvent> handler) {
        if (slot < 0 || slot >= 54) {
            return;
        }
        inventory.setItem(slot, item);
        if (handler == null) {
            handlers.remove(slot);
        } else {
            handlers.put(slot, handler);
        }
    }

    final void handleClick(InventoryClickEvent event) {
        Consumer<InventoryClickEvent> handler = handlers.get(event.getRawSlot());
        if (handler != null) {
            handler.accept(event);
        }
    }

    /** Menu-specific actions (e.g. confirm / cancel). Return true when handled. */
    protected boolean onCustomAction(String action, String argument) {
        return false;
    }

    final boolean customAction(String action, String argument) {
        return onCustomAction(action, argument);
    }

    public void back() {
        if (parent != null) {
            Bukkit.getScheduler().runTask(plugin, () -> parent.open());
        } else {
            Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory());
        }
    }

    protected void openLater(CosmicMenu menu) {
        Bukkit.getScheduler().runTask(plugin, () -> menu.open());
    }

    protected void closeLater() {
        Bukkit.getScheduler().runTask(plugin, () -> viewer.closeInventory());
    }

    protected void error() {
        template.errorSound().play(viewer);
    }

    protected void click() {
        template.clickSound().play(viewer);
    }
}
