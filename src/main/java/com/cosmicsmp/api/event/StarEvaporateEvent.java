package com.cosmicsmp.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Set;

/** Fired after a player's stars evaporated (death). Informational. */
public final class StarEvaporateEvent extends PlayerEvent {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Set<String> starIds;

    public StarEvaporateEvent(Player player, Set<String> starIds) {
        super(player);
        this.starIds = Set.copyOf(starIds);
    }

    public Set<String> getStarIds() {
        return starIds;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
