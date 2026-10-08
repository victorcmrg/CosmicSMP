package com.cosmicsmp.core.hook;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.logging.Logger;

/** MythicMobs bridge: spawn Mythic mobs as summons / merchant and use Mythic items in configs. */
public final class MythicMobsHook extends ReflectiveHook {

    private Method inst;
    private Method apiHelper;
    private Method spawnMob;
    private Method itemManager;
    private Method getItemStack;

    public MythicMobsHook(Logger logger, boolean enabled) {
        super(logger, enabled);
    }

    @Override
    public String pluginName() {
        return "MythicMobs";
    }

    @Override
    protected void bind(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mythic = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, loader);
        inst = mythic.getMethod("inst");
        apiHelper = mythic.getMethod("getAPIHelper");
        spawnMob = apiHelper.getReturnType().getMethod("spawnMythicMob", String.class, Location.class);
        try {
            itemManager = mythic.getMethod("getItemManager");
            getItemStack = itemManager.getReturnType().getMethod("getItemStack", String.class);
        } catch (NoSuchMethodException ignored) {
            itemManager = null;
        }
    }

    public Entity spawn(String mobId, Location location) {
        if (!available()) {
            return null;
        }
        try {
            Object helper = apiHelper.invoke(inst.invoke(null));
            return (Entity) spawnMob.invoke(helper, mobId, location);
        } catch (ReflectiveOperationException | RuntimeException ex) {
            logger.warning("MythicMobs could not spawn '" + mobId + "': " + ex.getMessage());
            return null;
        }
    }

    public ItemStack item(String id) {
        if (!available() || itemManager == null) {
            return null;
        }
        try {
            Object result = getItemStack.invoke(itemManager.invoke(inst.invoke(null)), id);
            if (result instanceof Optional<?> optional) {
                result = optional.orElse(null);
            }
            return result instanceof ItemStack stack ? stack.clone() : null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }
}
