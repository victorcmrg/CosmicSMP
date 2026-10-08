package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.module.CosmicModule;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.impl.RideStates;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

/** Stars, abilities, passives, summons, HUD and stock. */
public final class StarModule implements CosmicModule {

    private final CosmicSMP plugin;
    private StarListener listener;

    public StarModule(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return "stars";
    }

    @Override
    public void enable() {
        RideStates.register(plugin);
        listener = new StarListener(plugin);
        Bukkit.getPluginManager().registerEvents(listener, plugin);
        Bukkit.getPluginManager().registerEvents(plugin.combat(), plugin);
        Bukkit.getPluginManager().registerEvents(plugin.timedStates(), plugin);
        for (Ability ability : plugin.abilities().actives()) {
            if (ability instanceof Listener abilityListener) {
                Bukkit.getPluginManager().registerEvents(abilityListener, plugin);
            }
        }
        plugin.summons().start();
        plugin.passives().start();
        plugin.hud().start();
        plugin.stock().start();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.timedStates().restore(player);
            plugin.starItems().sanitize(player);
        }
    }

    @Override
    public void reload() {
        plugin.hud().stop();
        plugin.hud().start();
        for (Player player : Bukkit.getOnlinePlayers()) {
            plugin.passives().refresh(player);
            plugin.starItems().sanitize(player);
        }
    }

    @Override
    public void disable() {
        plugin.stock().stop();
        plugin.hud().stop();
        plugin.passives().stop();
        plugin.summons().stop();
        HandlerList.unregisterAll(listener);
        HandlerList.unregisterAll(plugin.combat());
        HandlerList.unregisterAll(plugin.timedStates());
        for (Ability ability : plugin.abilities().actives()) {
            if (ability instanceof Listener abilityListener) {
                HandlerList.unregisterAll(abilityListener);
            }
        }
    }
}
