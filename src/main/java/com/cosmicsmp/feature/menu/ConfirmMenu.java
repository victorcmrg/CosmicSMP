package com.cosmicsmp.feature.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.menu.CosmicMenu;
import com.cosmicsmp.core.text.Placeholders;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/** Generic "are you sure?" menu (menus/confirm.yml). Buttons use the actions {@code confirm} and {@code cancel}. */
public final class ConfirmMenu extends CosmicMenu {

    private final ItemStack display;
    private final Placeholders extra;
    private final Runnable onConfirm;
    private boolean done;

    public ConfirmMenu(CosmicSMP plugin, Player viewer, CosmicMenu parent, ItemStack display, Placeholders extra, Runnable onConfirm) {
        super(plugin, viewer, plugin.menus().template("confirm"), parent);
        this.display = display;
        this.extra = extra;
        this.onConfirm = onConfirm;
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders().merge(extra);
    }

    @Override
    protected void draw(Placeholders ph) {
        for (int slot : template.slots("display-slots")) {
            set(slot, display, null);
        }
    }

    @Override
    protected boolean onCustomAction(String action, String argument) {
        if (action.equals("confirm")) {
            if (!done) {
                done = true;
                onConfirm.run();
                back();
            }
            return true;
        }
        if (action.equals("cancel")) {
            back();
            return true;
        }
        return false;
    }
}
