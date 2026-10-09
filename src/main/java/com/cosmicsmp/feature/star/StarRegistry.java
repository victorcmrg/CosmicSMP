package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.config.YamlFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads every {@code stars/*.yml}. The default five stars are written on first start. */
public final class StarRegistry {

    private static final List<String> DEFAULTS = List.of("sonic", "trail", "elder", "cosmic", "nature");
    /** Controls line shipped in 1.0.0 star files; replaced by the {controls} placeholder at the end of the lore. */
    private static final String OLD_CONTROLS_LINE = "<#71717a>Right-click <#a1a1aa>cast · <#71717a>F <#a1a1aa>switch";

    private final CosmicSMP plugin;
    private Map<String, StarDefinition> stars = Map.of();

    public StarRegistry(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "stars");
        boolean firstRun = !folder.exists();
        if (firstRun || plugin.settings().restoreDefaultStars) {
            restoreMissingDefaults(folder, firstRun);
        }
        File[] files = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        List<StarDefinition> loaded = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                YamlFile yaml = new YamlFile(plugin, file);
                if (!yaml.load()) {
                    StarDefinition previous = stars.get(file.getName().replace(".yml", "").toLowerCase(Locale.ROOT));
                    if (previous != null) {
                        loaded.add(previous);
                    }
                    continue;
                }
                migrate(yaml);
                StarDefinition star = new StarDefinition(file.getName().substring(0, file.getName().length() - 4), yaml.get());
                if (star.enabled()) {
                    loaded.add(star);
                }
            }
        }
        loaded.sort(Comparator.comparingInt(StarDefinition::order).thenComparing(StarDefinition::id));
        Map<String, StarDefinition> map = new LinkedHashMap<>();
        for (StarDefinition star : loaded) {
            map.put(star.id(), star);
        }
        stars = Collections.unmodifiableMap(map);
        plugin.getLogger().info("Loaded " + stars.size() + " stars: " + String.join(", ", stars.keySet()));
    }

    /**
     * Re-creates any default star file that was deleted. A default is only skipped when another file already declares
     * the same id (e.g. the file was renamed). To remove a default star for good use {@code enabled: false} in its file,
     * or turn off {@code stars.restore-default-files} in config.yml.
     */
    private void restoreMissingDefaults(File folder, boolean firstRun) {
        java.util.Set<String> declared = new java.util.HashSet<>();
        File[] existing = folder.listFiles((dir, name) -> name.endsWith(".yml"));
        if (existing != null) {
            for (File file : existing) {
                String fileId = file.getName().substring(0, file.getName().length() - 4).toLowerCase(Locale.ROOT);
                declared.add(fileId);
                String id = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file).getString("id");
                if (id != null) {
                    declared.add(id.toLowerCase(Locale.ROOT));
                }
            }
        }
        for (String id : DEFAULTS) {
            if (!declared.contains(id) && plugin.getResource("stars/" + id + ".yml") != null) {
                plugin.saveResource("stars/" + id + ".yml", false);
                if (!firstRun) {
                    plugin.getLogger().info("Restored missing default star file stars/" + id + ".yml");
                }
            }
        }
    }

    /** Upgrades untouched default lore from older versions (custom lore is left alone). */
    private void migrate(YamlFile yaml) {
        List<String> lore = new ArrayList<>(yaml.get().getStringList("item.lore"));
        if (!lore.remove(OLD_CONTROLS_LINE)) {
            return;
        }
        if (!lore.contains("{controls}")) {
            lore.add("{controls}");
        }
        yaml.get().set("item.lore", lore);
        yaml.save();
        plugin.getLogger().info("Updated the item lore of " + yaml.file().getName() + " (controls line moved to the end).");
    }

    /** Warns about stars that reference abilities nobody registered (typo or missing expansion). */
    public void validate() {
        for (StarDefinition star : stars.values()) {
            for (AbilityDefinition def : star.all()) {
                boolean known = def.isPassive() ? plugin.abilities().passive(def.abilityId()) != null
                        : plugin.abilities().active(def.abilityId()) != null;
                if (!known) {
                    plugin.getLogger().warning("Star '" + star.id() + "' slot " + def.slot() + " uses unknown ability '"
                            + def.abilityId() + "' - that node will be shown but cannot be used.");
                }
            }
        }
    }

    public StarDefinition get(String id) {
        return id == null ? null : stars.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<StarDefinition> all() {
        return stars.values();
    }

    public List<String> ids() {
        return new ArrayList<>(stars.keySet());
    }
}
