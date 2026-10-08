package com.cosmicsmp.core.hook;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;
import java.util.logging.Logger;

/**
 * ItemsAdder bridge: custom items (as menu icons, star items, display models) and custom blocks.
 * Sounds from ItemsAdder packs need no hook - they are regular resource-pack keys (see SoundSpec).
 */
public final class ItemsAdderHook extends ReflectiveHook {

    private Method getInstance;
    private Method getItemStack;
    private Method placeBlock;

    public ItemsAdderHook(Logger logger, boolean enabled) {
        super(logger, enabled);
    }

    @Override
    public String pluginName() {
        return "ItemsAdder";
    }

    @Override
    protected void bind(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> customStack = Class.forName("dev.lone.itemsadder.api.CustomStack", true, loader);
        getInstance = customStack.getMethod("getInstance", String.class);
        getItemStack = customStack.getMethod("getItemStack");
        try {
            Class<?> customBlock = Class.forName("dev.lone.itemsadder.api.CustomBlock", true, loader);
            placeBlock = customBlock.getMethod("place", String.class, Location.class);
        } catch (ReflectiveOperationException ignored) {
            placeBlock = null;
        }
    }

    /** @param id namespaced id, e.g. {@code cosmic:sonic_star} */
    public ItemStack item(String id) {
        if (!available()) {
            return null;
        }
        try {
            Object stack = getInstance.invoke(null, id);
            return stack == null ? null : ((ItemStack) getItemStack.invoke(stack)).clone();
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return null;
        }
    }

    public boolean placeBlock(String id, Location location) {
        if (!available() || placeBlock == null) {
            return false;
        }
        try {
            return placeBlock.invoke(null, id, location) != null;
        } catch (ReflectiveOperationException | RuntimeException ex) {
            return false;
        }
    }
}
