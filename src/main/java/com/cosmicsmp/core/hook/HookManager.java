package com.cosmicsmp.core.hook;

import com.cosmicsmp.CosmicSMP;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Single entry point for every "content provider" the plugin can use. Configs reference content with prefixes:
 * <ul>
 *     <li>items: {@code NETHER_STAR}, {@code itemsadder:ns:id} / {@code ia:ns:id}, {@code mythic:ItemName}</li>
 *     <li>entities: {@code WARDEN}, {@code mythic:SpaceWarden}</li>
 *     <li>models: a BetterModel model id applied on top of the base entity</li>
 * </ul>
 * New providers (Nexo, Oraxen, ModelEngine...) plug in here without touching gameplay code.
 */
public final class HookManager {

    private final CosmicSMP plugin;
    private ItemsAdderHook itemsAdder;
    private MythicMobsHook mythicMobs;
    private BetterModelHook betterModel;
    private final Set<String> warned = new HashSet<>();

    public HookManager(CosmicSMP plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        if (betterModel != null) {
            betterModel.clear();
        }
        itemsAdder = new ItemsAdderHook(plugin.getLogger(), plugin.settings().hookItemsAdder);
        mythicMobs = new MythicMobsHook(plugin.getLogger(), plugin.settings().hookMythicMobs);
        betterModel = new BetterModelHook(plugin.getLogger(), plugin.settings().hookBetterModel);
        warned.clear();
    }

    public ItemsAdderHook itemsAdder() {
        return itemsAdder;
    }

    public MythicMobsHook mythicMobs() {
        return mythicMobs;
    }

    public BetterModelHook betterModel() {
        return betterModel;
    }

    /** Resolves an item reference into a fresh ItemStack (never null; BARRIER on failure). */
    public ItemStack resolveItem(String reference) {
        if (reference == null || reference.isBlank()) {
            return new ItemStack(Material.STONE);
        }
        String ref = reference.trim();
        String lower = ref.toLowerCase(Locale.ROOT);
        ItemStack custom = null;
        if (lower.startsWith("itemsadder:")) {
            custom = itemsAdder.item(ref.substring("itemsadder:".length()));
        } else if (lower.startsWith("ia:")) {
            custom = itemsAdder.item(ref.substring(3));
        } else if (lower.startsWith("mythic:")) {
            custom = mythicMobs.item(ref.substring(7));
        } else {
            Material material = Material.matchMaterial(ref);
            if (material != null && material.isItem() && !material.isAir()) {
                return new ItemStack(material);
            }
        }
        if (custom != null) {
            return custom;
        }
        warnOnce("item:" + ref, "Unknown item '" + ref + "' (plugin missing or id wrong) - showing a barrier.");
        return new ItemStack(Material.BARRIER);
    }

    /**
     * Spawns an entity from a reference. The configurer runs before the entity is added to the world for vanilla
     * types, and right after spawning for MythicMobs.
     */
    public Entity spawnEntity(String reference, Location location, EntityType fallback, Consumer<Entity> configure) {
        String ref = reference == null ? "" : reference.trim();
        if (ref.toLowerCase(Locale.ROOT).startsWith("mythic:")) {
            Entity mythic = mythicMobs.spawn(ref.substring(7), location);
            if (mythic != null) {
                configure.accept(mythic);
                return mythic;
            }
            warnOnce("mob:" + ref, "Could not spawn MythicMob '" + ref + "' - falling back to " + fallback);
        }
        EntityType type = fallback;
        if (!ref.isEmpty() && !ref.toLowerCase(Locale.ROOT).startsWith("mythic:")) {
            try {
                type = EntityType.valueOf(ref.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                warnOnce("type:" + ref, "Unknown entity type '" + ref + "' - using " + fallback);
            }
        }
        if (type.getEntityClass() == null || !type.isSpawnable()) {
            type = fallback;
        }
        return location.getWorld().spawn(location, type.getEntityClass(), CreatureSpawnEvent.SpawnReason.CUSTOM, configure::accept);
    }

    /** Attaches a custom model when one is configured and available. Hides the vanilla body on success. */
    public boolean applyModel(Entity entity, String modelId) {
        if (modelId == null || modelId.isBlank()) {
            return false;
        }
        boolean applied = betterModel.apply(entity, modelId);
        if (applied && entity instanceof LivingEntity living) {
            living.setInvisible(true);
        }
        return applied;
    }

    public void removeModel(Entity entity) {
        betterModel.remove(entity);
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) {
            plugin.getLogger().warning(message);
        }
    }
}
