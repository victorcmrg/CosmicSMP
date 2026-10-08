package com.cosmicsmp.core.task;

import com.cosmicsmp.CosmicSMP;

import java.util.Set;
import java.util.UUID;

/**
 * A short-lived, tick-driven effect (ability animation, ride, field, disguise...).
 * <p>
 * All effects are ticked by one shared {@link EffectManager} task instead of one Bukkit task each, and every effect is
 * guaranteed to receive {@link #onEnd(EndReason)} exactly once - including on quit, death, reload and shutdown - so
 * nothing (entities, profiles, modifiers) is ever left behind.
 */
public abstract class TimedEffect {

    protected CosmicSMP plugin;
    protected int age;
    private final int maxTicks;
    private boolean finished;
    private boolean ended;
    private EndReason reason = EndReason.COMPLETED;

    protected TimedEffect(int maxTicks) {
        this.maxTicks = maxTicks;
    }

    /** The player that owns this effect (may be null for world effects). */
    public abstract UUID owner();

    /** Every player whose quit/death must stop this effect. Defaults to the owner. */
    public Set<UUID> participants() {
        UUID owner = owner();
        return owner == null ? Set.of() : Set.of(owner);
    }

    protected void onStart() {
    }

    protected abstract void onTick();

    protected void onEnd(EndReason reason) {
    }

    /** Ends the effect at the end of the current tick with {@link EndReason#COMPLETED}. */
    public final void finish() {
        finished = true;
    }

    public final void cancel(EndReason endReason) {
        if (!finished) {
            this.reason = endReason;
        }
        finished = true;
    }

    public final boolean isFinished() {
        return finished;
    }

    public final int age() {
        return age;
    }

    final int maxTicks() {
        return maxTicks;
    }

    final void bind(CosmicSMP owner) {
        this.plugin = owner;
    }

    final EndReason reason() {
        return reason;
    }

    final void end(EndReason endReason) {
        if (ended) {
            return;
        }
        ended = true;
        finished = true;
        try {
            onEnd(endReason);
        } catch (Throwable t) {
            plugin.getLogger().warning("Error while ending effect " + getClass().getSimpleName() + ": " + t);
        }
    }
}
