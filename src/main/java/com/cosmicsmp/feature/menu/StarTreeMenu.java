package com.cosmicsmp.feature.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.item.ItemSpec;
import com.cosmicsmp.core.menu.CosmicMenu;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.star.AbilityDefinition;
import com.cosmicsmp.feature.star.StarDefinition;
import com.cosmicsmp.feature.star.StarService;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Skill tree of one star (menus/star_tree.yml): the star on top, the passive in the middle and three primary
 * branches, connected by panes that light up as nodes are unlocked. Click a locked node to buy it, an unlocked
 * primary to select it.
 */
public final class StarTreeMenu extends CosmicMenu {

    private final StarDefinition star;
    private final boolean canPurchase;

    public StarTreeMenu(CosmicSMP plugin, Player viewer, CosmicMenu parent, StarDefinition star, boolean canPurchase) {
        super(plugin, viewer, plugin.menus().template("star_tree"), parent);
        this.star = star;
        this.canPurchase = canPurchase;
    }

    @Override
    protected Placeholders placeholders() {
        return super.placeholders().with("star", star.displayName()).with("star_color", star.color());
    }

    @Override
    protected void draw(Placeholders ph) {
        PlayerData data = plugin.players().get(viewer);
        PlayerData.OwnedStar owned = data.star(star.id());

        Placeholders starPh = ph.with("price", star.price()).with("description", String.join("\n", star.description()))
                .with("status", template.string("star.status." + (owned != null ? "owned" : "not-owned"), ""))
                .with("abilities", MenuText.abilityLines(plugin, template, star, owned, "star.ability-line"));
        ItemSpec starSpec = star.icon().withName(template.string("star.name", "{star}")).withLore(template.strings("star.lore"));
        if (owned != null) {
            starSpec = starSpec.withGlow(true);
        }
        int starSlot = template.integer("star-slot", 4);
        set(starSlot, starSpec.build(plugin, starPh, viewer), null);

        if (star.passive() != null) {
            drawNode(star.passive(), template.integer("passive-slot", 22), owned, data, ph);
            drawConnectors("connectors.passive", owned != null && owned.passive, ph);
        }
        List<Integer> primarySlots = template.slots("primary-slots");
        for (AbilityDefinition def : star.primaries()) {
            int index = def.slot() - 1;
            if (index < primarySlots.size()) {
                drawNode(def, primarySlots.get(index), owned, data, ph);
                drawConnectors("connectors.primary-" + def.slot(), owned != null && owned.unlocked(def.slot()), ph);
            }
        }
    }

    private void drawConnectors(String path, boolean unlocked, Placeholders ph) {
        ItemStack item = template.item(unlocked ? "connector.unlocked" : "connector.locked", ItemSpec.of("GRAY_STAINED_GLASS_PANE", " ", List.of()))
                .build(plugin, ph, viewer);
        for (int slot : template.slots(path)) {
            set(slot, item, null);
        }
    }

    private void drawNode(AbilityDefinition def, int slot, PlayerData.OwnedStar owned, PlayerData data, Placeholders base) {
        String status;
        String action;
        boolean unlocked = owned != null && owned.unlocked(def.slot());
        if (unlocked) {
            boolean selected = !def.isPassive() && owned.selected == def.slot();
            status = selected ? "selected" : "unlocked";
            action = def.isPassive() ? "none" : (selected ? "selected" : "select");
        } else {
            StarService.Result check = plugin.starService().checkUnlock(viewer, star, def.slot());
            status = switch (check) {
                case NOT_OWNED -> "locked-star";
                case REQUIRES_PREVIOUS -> "requires";
                case NOT_ENOUGH_BRIGHTNESS -> "poor";
                case NOT_AVAILABLE -> "unavailable";
                default -> "available";
            };
            action = check == StarService.Result.SUCCESS ? (canPurchase ? "buy" : "visit") : "none";
        }
        Placeholders ph = base.with("name", def.name())
                .with("description", String.join("\n", def.description()))
                .with("cost", def.cost())
                .with("cooldown", MenuText.cooldown(def))
                .with("type", MenuText.type(plugin, def))
                .with("status", template.string("node.status." + status, status))
                .with("action", template.string("node.action." + action, ""));
        ItemSpec spec = def.icon().withName(template.string("node.name", "{name}")).withLore(template.strings("node.lore"));
        if (unlocked) {
            spec = spec.withGlow(true);
        }
        set(slot, spec.build(plugin, ph, viewer), e -> clickNode(def));
    }

    private void clickNode(AbilityDefinition def) {
        PlayerData data = plugin.players().get(viewer);
        PlayerData.OwnedStar owned = data.star(star.id());
        if (owned != null && owned.unlocked(def.slot())) {
            if (!def.isPassive() && owned.selected != def.slot()) {
                plugin.starService().select(viewer, star, def.slot());
                plugin.messages().send(viewer, "abilities.selected", Placeholders.of("ability", def.name(), "slot", def.slot(), "star", star.displayName()));
                click();
                refresh();
            }
            return;
        }
        StarService.Result check = plugin.starService().checkUnlock(viewer, star, def.slot());
        if (check != StarService.Result.SUCCESS) {
            error();
            plugin.messages().send(viewer, plugin.starService().resultKey(check), Placeholders.of("star", star.displayName(),
                    "ability", def.name(), "price", def.cost()));
            return;
        }
        if (!canPurchase) {
            error();
            plugin.messages().send(viewer, "stars.visit-merchant");
            return;
        }
        click();
        Placeholders extra = Placeholders.of("what", def.name(), "cost", def.cost(), "after", data.brightness - def.cost());
        ItemStack display = def.icon().withName(template.string("node.name", "{name}"))
                .withLore(List.of("{description}")).build(plugin, placeholders().with("name", def.name())
                        .with("description", String.join("\n", def.description())), viewer);
        openLater(new ConfirmMenu(plugin, viewer, this, display, extra, () -> {
            StarService.Result result = plugin.starService().unlock(viewer, star, def.slot());
            if (result != StarService.Result.SUCCESS) {
                plugin.messages().send(viewer, plugin.starService().resultKey(result), Placeholders.of("star", star.displayName(),
                        "ability", def.name(), "price", def.cost()));
            }
        }));
    }
}
