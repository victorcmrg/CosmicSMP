package com.cosmicsmp.api.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/** Fired before a primary ability is cast (after cooldown checks). Cancel to block the cast. */
public final class AbilityCastEvent extends PlayerEvent implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final String starId;
    private final int slot;
    private final String abilityId;
    private boolean cancelled;

    public AbilityCastEvent(Player player, String starId, int slot, String abilityId) {
        super(player);
        this.starId = starId;
        this.slot = slot;
        this.abilityId = abilityId;
    }

    public String getStarId() {
        return starId;
    }

    public int getSlot() {
        return slot;
    }

    public String getAbilityId() {
        return abilityId;
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
