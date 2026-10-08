package com.cosmicsmp.feature.merchant;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.module.CosmicModule;
import org.bukkit.Bukkit;
import org.bukkit.event.HandlerList;

/** The Galactic Merchant NPCs. */
public final class MerchantModule implements CosmicModule {

    private final CosmicSMP plugin;
    private MerchantListener listener;

    public MerchantModule(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "merchant";
    }

    @Override
    public void enable() {
        listener = new MerchantListener(plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        plugin.merchants().start();
    }

    @Override
    public void reload() {
        plugin.merchants().reload();
    }

    @Override
    public void disable() {
        plugin.merchants().stop();
        HandlerList.unregisterAll(listener);
    }
}
