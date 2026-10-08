package com.cosmicsmp.core.menu;

import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.item.ItemSpec;
import com.cosmicsmp.core.util.Slots;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed menu file ({@code menus/<name>.yml}). All menus are {@code GENERIC_9X6} (54 slots).
 * Static {@code items} are rendered first; feature menus then read their own sections (slots, templates, states)
 * through {@link #section(String)} / {@link #item(String, ItemSpec)}.
 */
public final class MenuTemplate {

    private final String name;
    private final String title;
    private final SoundSpec openSound;
    private final SoundSpec clickSound;
    private final SoundSpec errorSound;
    private final List<MenuButton> buttons;
    private final YamlConfiguration config;

    public MenuTemplate(String name, YamlConfiguration config) {
        this.name = name;
        this.config = config;
        this.title = config.getString("title", name);
        this.openSound = SoundSpec.parse(config.get("open-sound"), SoundSpec.NONE);
        this.clickSound = SoundSpec.parse(config.get("click-sound"), SoundSpec.of("ui.button.click", 0.4f, 1.6f));
        this.errorSound = SoundSpec.parse(config.get("error-sound"), SoundSpec.of("entity.villager.no", 0.6f, 1.0f));
        List<MenuButton> list = new ArrayList<>();
        ConfigurationSection items = config.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection section = items.getConfigurationSection(key);
                if (section != null) {
                    list.add(MenuButton.of(key, section, clickSound));
                }
            }
        }
        this.buttons = List.copyOf(list);
    }

    public String name() {
        return name;
    }

    public String title() {
        return title;
    }

    public SoundSpec openSound() {
        return openSound;
    }

    public SoundSpec clickSound() {
        return clickSound;
    }

    public SoundSpec errorSound() {
        return errorSound;
    }

    public List<MenuButton> buttons() {
        return buttons;
    }

    public ConfigurationSection section(String path) {
        return config.getConfigurationSection(path);
    }

    public ItemSpec item(String path, ItemSpec fallback) {
        ConfigurationSection section = config.getConfigurationSection(path);
        return section == null ? fallback : ItemSpec.of(section, fallback);
    }

    public String string(String path, String def) {
        return config.getString(path, def);
    }

    public List<String> strings(String path) {
        return config.getStringList(path);
    }

    public boolean bool(String path, boolean def) {
        return config.getBoolean(path, def);
    }

    public int integer(String path, int def) {
        return config.getInt(path, def);
    }

    public List<Integer> slots(String path) {
        return Slots.list(config, path);
    }
}
