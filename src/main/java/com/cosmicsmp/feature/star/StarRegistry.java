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

    private final CosmicSMP plugin;
    private Map<String, StarDefinition> stars = Map.of();

    public StarRegistry(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void load() {
        File folder = new File(plugin.getDataFolder(), "stars");
        if (!folder.exists()) {
            for (String id : DEFAULTS) {
                plugin.saveResource("stars/" + id + ".yml", false);
            }
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
