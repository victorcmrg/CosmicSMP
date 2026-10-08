package com.cosmicsmp.api;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.api.event.BrightnessChangeEvent;
import com.cosmicsmp.core.command.CommandCategory;
import com.cosmicsmp.core.module.CosmicModule;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.PassiveAbility;
import com.cosmicsmp.feature.star.StarDefinition;
import org.bukkit.entity.Player;

import java.util.Collection;

/**
 * Public entry point for expansions (add {@code depend: [CosmicSMP]} to your plugin.yml).
 * <pre>
 * CosmicAPI.registerAbility(new MyLaser());          // then use "ability: my_laser" in any stars/*.yml
 * CosmicAPI.registerPassive(new MyPassive());
 * CosmicAPI.registerModule(new MyModule());
 * CosmicAPI.registerCommands(new CommandCategory("myexp", "★").add(...));
 * </pre>
 * Listen to {@link com.cosmicsmp.api.event} events to react to purchases, casts, brightness and evaporation.
 */
public final class CosmicAPI {

    private static CosmicSMP plugin;

    private CosmicAPI() {
    }

    public static void init(CosmicSMP owner) {
        plugin = owner;
    }

    private static CosmicSMP plugin() {
        if (plugin == null) {
            throw new IllegalStateException("CosmicSMP is not enabled");
        }
        return plugin;
    }

    public static int getBrightness(Player player) {
        return plugin().brightness().get(player);
    }

    public static int addBrightness(Player player, int delta) {
        return plugin().brightness().change(player, delta, BrightnessChangeEvent.Cause.API);
    }

    public static boolean ownsStar(Player player, String starId) {
        return plugin().players().get(player).owns(starId);
    }

    public static boolean hasPassive(Player player, String passiveId) {
        return plugin().passives().has(player, passiveId);
    }

    public static Collection<StarDefinition> stars() {
        return plugin().stars().all();
    }

    public static void registerAbility(Ability ability) {
        plugin().abilities().register(ability);
    }

    public static void registerPassive(PassiveAbility passive) {
        plugin().abilities().register(passive);
        plugin().passives().registerLate(passive);
    }

    public static void registerModule(CosmicModule module) {
        plugin().modules().registerAndEnable(module);
    }

    public static void registerCommands(CommandCategory category) {
        plugin().commands().register(category);
    }
}
