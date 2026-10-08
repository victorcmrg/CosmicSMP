package com.cosmicsmp.feature.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.menu.CosmicMenu;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.star.StarDefinition;
import com.cosmicsmp.feature.star.StockService;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

import java.util.List;

/** Admin stock editor (menus/stock.yml): left +1, right -1, shift-left unlimited, shift-right reset to default. */
public final class StockMenu extends CosmicMenu {

    public StockMenu(CosmicSMP plugin, Player viewer, CosmicMenu parent) {
        super(plugin, viewer, plugin.menus().template("stock"), parent);
    }

    @Override
    protected void draw(Placeholders ph) {
        List<Integer> slots = template.slots("star-slots");
        int i = 0;
        for (StarDefinition star : plugin.stars().all()) {
            if (i >= slots.size()) {
                break;
            }
            Placeholders starPh = ph.with("star", star.displayName()).with("stock", plugin.stock().display(star))
                    .with("default", star.defaultStock() < 0 ? plugin.messages().raw("format.unlimited", "∞") : String.valueOf(star.defaultStock()));
            set(slots.get(i++), star.icon().withName(template.string("star-icon.name", "{star}"))
                    .withLore(template.strings("star-icon.lore")).build(plugin, starPh, viewer), e -> {
                if (!viewer.hasPermission("cosmicsmp.admin.stock")) {
                    error();
                    return;
                }
                int current = plugin.stock().stock(star);
                ClickType click = e.getClick();
                if (click == ClickType.SHIFT_LEFT) {
                    plugin.stock().set(star.id(), StockService.UNLIMITED);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    plugin.stock().reset(star);
                } else if (click.isLeftClick()) {
                    plugin.stock().set(star.id(), current < 0 ? 1 : current + 1);
                } else if (click.isRightClick()) {
                    plugin.stock().set(star.id(), current <= 0 ? 0 : current - 1);
                }
                click();
                refresh();
            });
        }
    }
}
