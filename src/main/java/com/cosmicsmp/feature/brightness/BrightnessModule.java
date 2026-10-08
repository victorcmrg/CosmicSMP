package com.cosmicsmp.feature.brightness;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.module.CosmicModule;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/** Brightness gain/loss, perks and death bans. */
public final class BrightnessModule implements CosmicModule {

    private final CosmicSMP plugin;
    private BrightnessListener listener;

    public BrightnessModule(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "brightness";
    }

    @Override
    public void enable() {
        listener = new BrightnessListener(plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        Bukkit.getPluginManager().registerEvents(plugin.deathBans(), plugin);
        plugin.perks().start();
    }

    @Override
    public void disable() {
        plugin.perks().stop();
        HandlerList.unregisterAll(listener);
        HandlerList.unregisterAll(plugin.deathBans());
    }
}
