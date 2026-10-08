package com.cosmicsmp.core.hook;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.logging.Logger;

/**
 * Base for hooks bound through reflection: the plugin compiles without the third-party APIs and the integration
 * switches on automatically when the other plugin is installed.
 */
abstract class ReflectiveHook implements Hook {

    protected final Logger logger;
    private final boolean enabledInConfig;
    private Boolean bound;

    ReflectiveHook(Logger logger, boolean enabledInConfig) {
        this.logger = logger;
        this.enabledInConfig = enabledInConfig;
    }

    @Override
    public final boolean available() {
        if (!enabledInConfig) {
            return false;
        }
        Plugin target = Bukkit.getPluginManager().getPlugin(pluginName());
        if (target == null || !target.isEnabled()) {
            return false;
        }
        if (bound == null) {
            try {
                bind(target.getClass().getClassLoader());
                bound = true;
                logger.info("Hooked into " + pluginName() + " " + target.getPluginMeta().getVersion());
            } catch (Throwable t) {
                bound = false;
                logger.warning("Could not hook into " + pluginName() + " (" + t.getClass().getSimpleName() + ": "
                        + t.getMessage() + "). The integration stays disabled; vanilla fallbacks are used.");
            }
        }
        return bound;
    }

    protected abstract void bind(ClassLoader loader) throws ReflectiveOperationException;
}
