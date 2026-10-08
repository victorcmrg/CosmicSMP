package com.cosmicsmp.feature.ability;

import com.cosmicsmp.feature.star.AbilityDefinition;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

/**
 * A passive effect. Event-driven passives implement {@link Listener} handlers and check
 * {@code plugin.passives().has(player, id())}; periodic passives return a {@link #tickInterval()}.
 * {@link #onDeactivate} must undo anything {@link #onActivate}/{@link #tick} applied.
 */
public interface PassiveAbility extends Listener {

    String id();

    /** Ticks between {@link #tick} calls; 0 = event-only passive. */
    default int tickInterval() {
        return 0;
    }

    default void tick(Player player, AbilityDefinition def) {
    }

    default void onActivate(Player player, AbilityDefinition def) {
    }

    default void onDeactivate(Player player, AbilityDefinition def) {
    }
}
