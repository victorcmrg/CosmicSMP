package com.cosmicsmp.core.config;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * A YAML file inside the data folder backed by a default copy shipped in the jar.
 * <ul>
 *     <li>Missing files are copied from the jar on first load.</li>
 *     <li>When {@code mergeDefaults} is on, keys added in new plugin versions are written into the user's file
 *     (comments are preserved).</li>
 *     <li>A broken file never wipes the running configuration: the previous state is kept and the error logged.</li>
 * </ul>
 */
public final class YamlFile {

    private final JavaPlugin plugin;
    private final String resourcePath;
    private final File file;
    private final boolean mergeDefaults;
    private YamlConfiguration config = new YamlConfiguration();

    public YamlFile(JavaPlugin plugin, String resourcePath, boolean mergeDefaults) {
        this.plugin = plugin;
        this.resourcePath = resourcePath;
        this.file = new File(plugin.getDataFolder(), resourcePath);
        this.mergeDefaults = mergeDefaults;
    }

    public YamlFile(JavaPlugin plugin, File file) {
        this.plugin = plugin;
        this.resourcePath = null;
        this.file = file;
        this.mergeDefaults = false;
    }

    public boolean load() {
        if (!file.exists() && resourcePath != null && plugin.getResource(resourcePath) != null) {
            plugin.saveResource(resourcePath, false);
        }
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Could not load " + file.getPath() + " (" + ex.getMessage().trim()
                    + "). The previous version stays active until the file is fixed.");
            return false;
        }

        YamlConfiguration defaults = defaults();
        if (defaults != null) {
            loaded.setDefaults(defaults);
            if (mergeDefaults && merge(loaded, defaults)) {
                try {
                    loaded.save(file);
                } catch (IOException ex) {
                    plugin.getLogger().warning("Could not write new default keys to " + file.getName() + ": " + ex.getMessage());
                }
            }
        }
        this.config = loaded;
        return true;
    }

    private YamlConfiguration defaults() {
        if (resourcePath == null) {
            return null;
        }
        InputStream in = plugin.getResource(resourcePath);
        if (in == null) {
            return null;
        }
        try (InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            return YamlConfiguration.loadConfiguration(reader);
        } catch (IOException ex) {
            return null;
        }
    }

    private static boolean merge(YamlConfiguration target, YamlConfiguration defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(key)) {
                continue;
            }
            if (!target.isSet(key)) {
                target.set(key, defaults.get(key));
                target.setComments(key, defaults.getComments(key));
                changed = true;
            }
        }
        return changed;
    }

    public void save() {
        try {
            config.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save " + file.getName() + ": " + ex.getMessage());
        }
    }

    public YamlConfiguration get() {
        return config;
    }

    public File file() {
        return file;
    }
}
