package com.cosmicsmp.feature.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.item.ItemSpec;
import com.cosmicsmp.core.menu.CosmicMenu;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.ability.CooldownService;
import com.cosmicsmp.feature.star.StarDefinition;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

/** Player profile (menus/profile.yml): brightness meter, perks, owned stars and stats. */
public final class ProfileMenu extends CosmicMenu {

    public ProfileMenu(CosmicSMP plugin, Player viewer, CosmicMenu parent) {
        super(plugin, viewer, plugin.menus().template("profile"), parent);
    }

    @Override
    protected void draw(Placeholders ph) {
        PlayerData data = plugin.players().get(viewer);
        Settings s = plugin.settings();

        drawPerk("perks.speed", s.speedEnabled && data.brightness >= s.speedRequired, ph.with("required", s.speedRequired));
        long dashLeft = plugin.perks().dashRemaining(viewer);
        drawPerk("perks.dash", s.dashEnabled && data.brightness >= s.dashRequired, ph.with("required", s.dashRequired)
                .with("cooldown", dashLeft > 0 ? CooldownService.format(dashLeft) : plugin.messages().raw("format.ready", "ready"))
                .with("distance", (int) s.dashDistance));

        List<Integer> slots = template.slots("owned-stars.slots");
        List<StarDefinition> owned = new ArrayList<>();
        for (String id : data.stars.keySet()) {
            StarDefinition star = plugin.stars().get(id);
            if (star != null) {
                owned.add(star);
            }
        }
        for (int i = 0; i < slots.size(); i++) {
            int slot = slots.get(i);
            if (i < owned.size()) {
                StarDefinition star = owned.get(i);
                PlayerData.OwnedStar ownedStar = data.star(star.id());
                Placeholders starPh = ph.with("star", star.displayName()).with("star_color", star.color())
                        .with("abilities", MenuText.abilityLines(plugin, template, star, ownedStar, "owned-stars.ability-line"));
                ItemSpec spec = star.icon().withName(template.string("owned-stars.name", "{star}"))
                        .withLore(template.strings("owned-stars.lore")).withGlow(true);
                set(slot, spec.build(plugin, starPh, viewer), e -> {
                    click();
                    boolean remote = !s.purchasesRequireMerchant || viewer.hasPermission("cosmicsmp.merchant.remote");
                    openLater(new StarTreeMenu(plugin, viewer, this, star, remote));
                });
            } else {
                set(slot, template.item("owned-stars.empty", ItemSpec.of("GRAY_DYE", " ", List.of())).build(plugin, ph, viewer), null);
            }
        }
    }

    private void drawPerk(String path, boolean active, Placeholders ph) {
        int slot = template.integer(path + ".slot", -1);
        if (slot < 0) {
            return;
        }
        ItemSpec spec = template.item(path + (active ? ".active" : ".inactive"), ItemSpec.of("BARRIER", path, List.of()));
        set(slot, spec.build(plugin, ph, viewer), null);
    }
}
