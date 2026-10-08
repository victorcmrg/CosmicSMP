package com.cosmicsmp.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before a passive (slot 0) or primary (slots 1-3) is unlocked on an owned star. */
public final class AbilityUnlockEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String starId;
    private final int slot;
    private final int cost;
    private boolean cancelled;

    public AbilityUnlockEvent(Player player, String starId, int slot, int cost) {
        super(player);
        this.starId = starId;
        this.slot = slot;
        this.cost = cost;
    }

    public String getStarId() {
        return starId;
    }

    public int getSlot() {
        return slot;
    }

    public int getCost() {
        return cost;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
