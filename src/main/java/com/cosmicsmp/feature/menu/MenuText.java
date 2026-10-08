package com.cosmicsmp.feature.menu;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.menu.MenuTemplate;
import com.cosmicsmp.feature.ability.CooldownService;
import com.cosmicsmp.feature.star.AbilityDefinition;
import com.cosmicsmp.feature.star.StarDefinition;

/** Shared lore builders for star / ability icons. */
final class MenuText {

    private MenuText() {
    }

    static String abilityLines(CosmicSMP plugin, MenuTemplate template, StarDefinition star, PlayerData.OwnedStar owned, String path) {
        StringBuilder sb = new StringBuilder();
        for (AbilityDefinition def : star.all()) {
            boolean unlocked = owned != null && owned.unlocked(def.slot());
            String format = template.string(path + (unlocked ? ".unlocked" : ".locked"), "{name}");
            if (!sb.isEmpty()) {
                sb.append('\n');
            }
            sb.append(format.replace("{name}", def.name())
                    .replace("{cost}", String.valueOf(def.cost()))
                    .replace("{type}", type(plugin, def)));
        }
        return sb.toString();
    }

    static String type(CosmicSMP plugin, AbilityDefinition def) {
        return def.isPassive() ? plugin.messages().raw("format.passive", "Passive")
                : plugin.messages().raw("format.primary", "Primary #{slot}").replace("{slot}", String.valueOf(def.slot()));
    }

    static String cooldown(AbilityDefinition def) {
        return def.isPassive() ? "-" : CooldownService.format(def.cooldownSeconds() * 1000L);
    }
}
