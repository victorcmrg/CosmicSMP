package com.cosmicsmp.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before a player's brightness changes. The new value can be modified or the change cancelled. */
public final class BrightnessChangeEvent extends PlayerEvent implements Cancellable {

    public enum Cause { KILL, DEATH, PURCHASE, ADMIN, BAN_RESET, API }

    private static final HandlerList HANDLERS = new HandlerList();

    private final int oldValue;
    private int newValue;
    private final Cause cause;
    private boolean cancelled;

    public BrightnessChangeEvent(Player player, int oldValue, int newValue, Cause cause) {
        super(player);
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.cause = cause;
    }

    public int getOldValue() {
        return oldValue;
    }

    public int getNewValue() {
        return newValue;
    }

    public void setNewValue(int newValue) {
        this.newValue = newValue;
    }

    public Cause getCause() {
        return cause;
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
