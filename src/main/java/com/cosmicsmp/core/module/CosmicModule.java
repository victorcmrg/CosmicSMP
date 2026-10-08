package com.cosmicsmp.core.module;

/**
 * A self-contained feature (listeners, tasks, commands, menus). Modules can be switched off in
 * {@code config.yml -> modules.<id>} and expansions can register their own through {@code CosmicAPI.registerModule}.
 */
public interface CosmicModule {

    String id();

    void enable();

    void disable();

    /** Called after configs are reloaded with {@code /cosmic admin reload}. */
    default void reload() {
    }
}
