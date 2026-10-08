package com.cosmicsmp.feature.ability;

/**
 * An active (primary) ability. Implementations are stateless singletons; all per-cast state lives in the
 * {@link AbilityContext} and in the {@link com.cosmicsmp.core.task.TimedEffect}s they start.
 * Register new ones with {@code CosmicAPI.registerAbility} and reference the id from any {@code stars/*.yml}.
 */
public interface Ability {

    String id();

    CastResult cast(AbilityContext context);
}
