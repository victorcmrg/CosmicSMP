package com.cosmicsmp.core.hook;

/** An optional integration with another plugin. Hooks resolve lazily so load order never matters. */
public interface Hook {

    String pluginName();

    /** True when the target plugin is enabled and its API was bound successfully. */
    boolean available();
}
