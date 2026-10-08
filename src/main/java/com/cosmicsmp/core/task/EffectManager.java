package com.cosmicsmp.core.task;

import com.cosmicsmp.CosmicSMP;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;

/** Drives every {@link TimedEffect} from a single repeating task. */
public final class EffectManager {

    private final CosmicSMP plugin;
    private final List<TimedEffect> active = new ArrayList<>();
    private final List<TimedEffect> incoming = new ArrayList<>();
    private BukkitTask task;
    private boolean ticking;

    public EffectManager(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task == null) {
            task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
        }
    }

    public <T extends TimedEffect> T start(T effect) {
        effect.bind(plugin);
        try {
            effect.onStart();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Effect " + effect.getClass().getSimpleName() + " failed to start", t);
            effect.end(EndReason.CANCELLED);
            return effect;
        }
        if (effect.isFinished()) {
            effect.end(effect.reason());
            return effect;
        }
        if (ticking) {
            incoming.add(effect);
        } else {
            active.add(effect);
        }
        return effect;
    }

    private void tick() {
        ticking = true;
        try {
            Iterator<TimedEffect> it = active.iterator();
            while (it.hasNext()) {
                TimedEffect effect = it.next();
                if (!effect.isFinished()) {
                    effect.age++;
                    try {
                        effect.onTick();
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "Effect " + effect.getClass().getSimpleName() + " crashed and was stopped", t);
                        effect.cancel(EndReason.CANCELLED);
                    }
                    if (effect.age >= effect.maxTicks()) {
                        effect.finish();
                    }
                }
                if (effect.isFinished()) {
                    it.remove();
                    effect.end(effect.reason());
                }
            }
        } finally {
            ticking = false;
        }
        if (!incoming.isEmpty()) {
            active.addAll(incoming);
            incoming.clear();
        }
    }

    /** Stops every effect the player participates in, right now (player is still valid during quit/death). */
    public void cancelFor(UUID participant, EndReason reason) {
        List<TimedEffect> toEnd = new ArrayList<>();
        for (List<TimedEffect> list : List.of(active, incoming)) {
            for (TimedEffect effect : list) {
                if (!effect.isFinished() && effect.participants().contains(participant)) {
                    toEnd.add(effect);
                }
            }
        }
        for (TimedEffect effect : toEnd) {
            effect.cancel(reason);
            if (!ticking) {
                active.remove(effect);
                incoming.remove(effect);
            }
            effect.end(reason);
        }
    }

    public <T extends TimedEffect> Optional<T> find(UUID owner, Class<T> type) {
        for (List<TimedEffect> list : List.of(active, incoming)) {
            for (TimedEffect effect : list) {
                if (!effect.isFinished() && type.isInstance(effect) && owner.equals(effect.owner())) {
                    return Optional.of(type.cast(effect));
                }
            }
        }
        return Optional.empty();
    }

    public int size() {
        return active.size() + incoming.size();
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        List<TimedEffect> all = new ArrayList<>(active);
        all.addAll(incoming);
        active.clear();
        incoming.clear();
        for (TimedEffect effect : all) {
            effect.end(EndReason.SHUTDOWN);
        }
    }
}
