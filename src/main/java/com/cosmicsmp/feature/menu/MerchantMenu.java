package com.cosmicsmp.feature.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.item.ItemSpec;
import com.cosmicsmp.core.menu.CosmicMenu;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.star.StarDefinition;
import com.cosmicsmp.feature.star.StarService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Galactic Merchant (menus/merchant.yml): every star laid out like a constellation, with live status, price and stock.
 */
public final class MerchantMenu extends CosmicMenu {

    private final boolean atMerchant;

    public MerchantMenu(CosmicSMP plugin, Player viewer, CosmicMenu parent, boolean atMerchant) {
        super(plugin, viewer, plugin.menus().template("merchant"), parent);
        this.atMerchant = atMerchant;
    }

    private boolean canPurchase() {
        return atMerchant || !plugin.settings().purchasesRequireMerchant || viewer.hasPermission("cosmicsmp.merchant.remote");
    }

    @Override
    protected void draw(Placeholders ph) {
        PlayerData data = plugin.players().get(viewer);
        List<Integer> auto = new ArrayList<>(template.slots("auto-star-slots"));
        Set<Integer> used = new HashSet<>();
        for (StarDefinition star : plugin.stars().all()) {
            if (star.merchantSlot() >= 0) {
                used.add(star.merchantSlot());
            }
        }
        auto.removeAll(used);
        int autoIndex = 0;
        for (StarDefinition star : plugin.stars().all()) {
            int slot = star.merchantSlot();
            if (slot < 0) {
                if (autoIndex >= auto.size()) {
                    plugin.getLogger().warning("No free merchant slot for star '" + star.id() + "' - set merchant-slot in its file.");
                    continue;
                }
                slot = auto.get(autoIndex++);
            }
            set(slot, icon(star, data, ph), e -> click(star));
        }
    }

    private ItemStack icon(StarDefinition star, PlayerData data, Placeholders base) {
        PlayerData.OwnedStar owned = data.star(star.id());
        StarService.Result result = plugin.starService().checkPurchase(viewer, star);
        String state;
        if (owned != null) {
            state = "owned";
        } else if (result == StarService.Result.OUT_OF_STOCK) {
            state = "out-of-stock";
        } else if (result == StarService.Result.NOT_ENOUGH_BRIGHTNESS) {
            state = "poor";
        } else {
            state = data.discovered.contains(star.id()) ? "rebuy" : "available";
        }
        String action = owned != null ? "owned" : (result == StarService.Result.SUCCESS ? (canPurchase() ? "buy" : "visit") : "locked");
        Placeholders ph = base
                .with("star", star.displayName())
                .with("star_color", star.color())
                .with("price", star.price())
                .with("stock", plugin.stock().display(star))
                .with("description", String.join("\n", star.description()))
                .with("status", template.string("star-icon.status." + state, state))
                .with("action", template.string("star-icon.action." + action, ""))
                .with("abilities", MenuText.abilityLines(plugin, template, star, owned, "star-icon.ability-line"));
        ItemSpec spec = star.icon()
                .withName(template.string("star-icon.name", "{star}"))
                .withLore(template.strings("star-icon.lore"));
        if (owned != null && template.bool("star-icon.glow-owned", true)) {
            spec = spec.withGlow(true);
        }
        return spec.build(plugin, ph, viewer);
    }

    private void click(StarDefinition star) {
        PlayerData data = plugin.players().get(viewer);
        if (data.owns(star.id())) {
            click();
            openLater(new StarTreeMenu(plugin, viewer, this, star, canPurchase()));
            return;
        }
        StarService.Result result = plugin.starService().checkPurchase(viewer, star);
        if (result != StarService.Result.SUCCESS) {
            error();
            plugin.messages().send(viewer, plugin.starService().resultKey(result), Placeholders.of("star", star.displayName(), "price", star.price()));
            return;
        }
        if (!canPurchase()) {
            error();
            plugin.messages().send(viewer, "stars.visit-merchant");
            return;
        }
        click();
        Placeholders extra = Placeholders.of("what", star.displayName(), "cost", star.price(),
                "after", data.brightness - star.price());
        openLater(new ConfirmMenu(plugin, viewer, this, icon(star, data, placeholders()), extra, () -> {
            StarService.Result purchase = plugin.starService().purchase(viewer, star);
            if (purchase != StarService.Result.SUCCESS) {
                plugin.messages().send(viewer, plugin.starService().resultKey(purchase), Placeholders.of("star", star.displayName(), "price", star.price()));
            }
        }));
    }
}
