package com.cosmicsmp.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before a star is bought from the merchant. {@code firstPurchase} is true when it consumes stock. */
public final class StarPurchaseEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String starId;
    private final int price;
    private final boolean firstPurchase;
    private boolean cancelled;

    public StarPurchaseEvent(Player player, String starId, int price, boolean firstPurchase) {
        super(player);
        this.starId = starId;
        this.price = price;
        this.firstPurchase = firstPurchase;
    }

    public String getStarId() {
        return starId;
    }

    public int getPrice() {
        return price;
    }

    public boolean isFirstPurchase() {
        return firstPurchase;
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
