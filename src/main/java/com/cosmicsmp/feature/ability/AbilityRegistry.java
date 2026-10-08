package com.cosmicsmp.feature.ability;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** All known ability implementations, by id. */
public final class AbilityRegistry {

    private final Map<String, Ability> actives = new LinkedHashMap<>();
    private final Map<String, PassiveAbility> passives = new LinkedHashMap<>();

    public void register(Ability ability) {
        actives.put(ability.id().toLowerCase(Locale.ROOT), ability);
    }

    public void register(PassiveAbility passive) {
        passives.put(passive.id().toLowerCase(Locale.ROOT), passive);
    }

    public Ability active(String id) {
        return id == null ? null : actives.get(id.toLowerCase(Locale.ROOT));
    }

    public PassiveAbility passive(String id) {
        return id == null ? null : passives.get(id.toLowerCase(Locale.ROOT));
    }

    public Collection<PassiveAbility> passives() {
        return passives.values();
    }

    public Collection<Ability> actives() {
        return actives.values();
    }
}
