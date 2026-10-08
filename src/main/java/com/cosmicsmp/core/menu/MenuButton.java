package com.cosmicsmp.core.menu;

import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.item.ItemSpec;
import com.cosmicsmp.core.util.Slots;
import org.bukkit.configuration.ConfigurationSection;

import java.util.List;

/**
 * A static menu item defined in YAML.
 * <pre>
 * close:
 *   slot: 49
 *   material: BARRIER
 *   name: "<#ff5c7a>Close"
 *   lore: []
 *   actions: ["close"]                 # close | back | refresh | open:&lt;menu&gt; | command:&lt;cmd&gt;
 *                                      # console:&lt;cmd&gt; | message:&lt;text&gt; | sound:&lt;sound&gt;
 *   permission: ""                     # optional: hidden without it
 *   click-sound: "ui.button.click"
 * </pre>
 */
public record MenuButton(String id, List<Integer> slots, ItemSpec item, List<String> actions,
                         String permission, SoundSpec clickSound) {

    public static MenuButton of(String id, ConfigurationSection section, SoundSpec defaultClick) {
        return new MenuButton(id,
                Slots.read(section, "slot", "slots"),
                ItemSpec.of(section),
                section.getStringList("actions"),
                section.getString("permission", ""),
                SoundSpec.parse(section.get("click-sound"), defaultClick));
    }
}
