package com.cosmicsmp.feature.ability;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.feature.star.AbilityDefinition;
import com.cosmicsmp.feature.star.StarDefinition;
import org.bukkit.entity.Player;

/** Everything an ability needs for one cast. */
public record AbilityContext(CosmicSMP plugin, Player caster, StarDefinition star, AbilityDefinition def) {
}
