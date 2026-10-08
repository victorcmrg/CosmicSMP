package com.cosmicsmp.core.module;

import com.cosmicsmp.CosmicSMP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

/** Enables modules in registration order and disables them in reverse order. */
public final class ModuleManager {

    private final CosmicSMP plugin;
    private final Map<String, CosmicModule> modules = new LinkedHashMap<>();
    private final List<CosmicModule> enabled = new ArrayList<>();

    public ModuleManager(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void register(CosmicModule module) {
        if (modules.containsKey(module.id())) {
            throw new IllegalArgumentException("Module already registered: " + module.id());
        }
        modules.put(module.id(), module);
    }

    /** Registers and immediately enables (for expansions registering after startup). */
    public void registerAndEnable(CosmicModule module) {
        register(module);
        enable(module);
    }

    public void enableAll() {
        for (CosmicModule module : modules.values()) {
            enable(module);
        }
    }

    private void enable(CosmicModule module) {
        if (!plugin.settings().moduleEnabled(module.id())) {
            plugin.getLogger().info("Module '" + module.id() + "' is disabled in config.yml");
            return;
        }
        try {
            module.enable();
            enabled.add(module);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "Module '" + module.id() + "' failed to enable", t);
        }
    }

    public void reloadAll() {
        for (CosmicModule module : enabled) {
            try {
                module.reload();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Module '" + module.id() + "' failed to reload", t);
            }
        }
    }

    public void disableAll() {
        List<CosmicModule> reversed = new ArrayList<>(enabled);
        Collections.reverse(reversed);
        for (CosmicModule module : reversed) {
            try {
                module.disable();
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Module '" + module.id() + "' failed to disable", t);
            }
        }
        enabled.clear();
    }

    public boolean isEnabled(String id) {
        for (CosmicModule module : enabled) {
            if (module.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public List<String> enabledIds() {
        List<String> ids = new ArrayList<>();
        for (CosmicModule module : enabled) {
            ids.add(module.id());
        }
        return ids;
    }

    public List<String> allIds() {
        return new ArrayList<>(modules.keySet());
    }
}
