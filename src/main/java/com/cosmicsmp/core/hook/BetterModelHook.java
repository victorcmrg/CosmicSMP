package com.cosmicsmp.core.hook;

import org.bukkit.entity.Entity;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * BetterModel bridge: attaches a BetterModel model to a base entity (summons, mounts, the merchant).
 * <p>
 * BetterModel's API has changed shape between releases, so binding is defensive: it looks up
 * {@code BetterModel.model(String)} / {@code modelOrNull(String)} and then any {@code create}/{@code getOrCreate}
 * method that accepts the entity (directly or through {@code BukkitAdapter.adapt}). When anything is missing the
 * vanilla entity is simply shown - nothing breaks.
 */
public final class BetterModelHook extends ReflectiveHook {

    private Class<?> api;
    private Method adapt;
    private final Map<UUID, Object> trackers = new HashMap<>();

    public BetterModelHook(Logger logger, boolean enabled) {
        super(logger, enabled);
    }

    @Override
    public String pluginName() {
        return "BetterModel";
    }

    @Override
    protected void bind(ClassLoader loader) throws ReflectiveOperationException {
        api = Class.forName("kr.toxicity.model.api.BetterModel", true, loader);
        try {
            Class<?> adapter = Class.forName("kr.toxicity.model.api.bukkit.platform.BukkitAdapter", true, loader);
            adapt = adapter.getMethod("adapt", Entity.class);
        } catch (ReflectiveOperationException ignored) {
            adapt = null;
        }
        if (findRendererLookup() == null) {
            throw new NoSuchMethodException("BetterModel.model(String)");
        }
    }

    private Method findRendererLookup() {
        for (String name : new String[]{"model", "modelOrNull", "renderer"}) {
            try {
                Method m = api.getMethod(name, String.class);
                if (Modifier.isStatic(m.getModifiers())) {
                    return m;
                }
            } catch (NoSuchMethodException ignored) {
                // try next
            }
        }
        return null;
    }

    /**
     * @return true when the model was attached (callers then hide the vanilla entity)
     */
    public boolean apply(Entity entity, String modelId) {
        if (modelId == null || modelId.isBlank() || !available()) {
            return false;
        }
        try {
            Object renderer = findRendererLookup().invoke(null, modelId);
            if (renderer instanceof Optional<?> optional) {
                renderer = optional.orElse(null);
            }
            if (renderer == null) {
                logger.warning("BetterModel model '" + modelId + "' not found - using vanilla visuals.");
                return false;
            }
            Object tracker = create(renderer, entity);
            if (tracker == null) {
                return false;
            }
            trackers.put(entity.getUniqueId(), tracker);
            return true;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("BetterModel could not apply '" + modelId + "': " + ex.getMessage());
            return false;
        }
    }

    private Object create(Object renderer, Entity entity) throws ReflectiveOperationException {
        Object adapted = adapt == null ? null : adapt.invoke(null, entity);
        for (String name : new String[]{"getOrCreate", "create"}) {
            for (Method method : renderer.getClass().getMethods()) {
                if (!method.getName().equals(name) || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> param = method.getParameterTypes()[0];
                if (param.isInstance(entity)) {
                    return method.invoke(renderer, entity);
                }
                if (adapted != null && param.isInstance(adapted)) {
                    return method.invoke(renderer, adapted);
                }
            }
        }
        return null;
    }

    public void remove(Entity entity) {
        Object tracker = trackers.remove(entity.getUniqueId());
        if (tracker == null) {
            return;
        }
        try {
            tracker.getClass().getMethod("close").invoke(tracker);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // model is removed with its base entity anyway
        }
    }

    public void clear() {
        trackers.clear();
    }
}
