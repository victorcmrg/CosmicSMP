package com.cosmicsmp.feature.ability.impl;

import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.task.TimedEffect;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.star.AbilityDefinition;
import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.UUID;

/** Base for ability animations: owned by the caster, ends automatically if the caster leaves or dies. */
public abstract class AbilityEffect extends TimedEffect {

    protected final AbilityContext ctx;
    protected final Player caster;
    protected final AbilityDefinition def;

    protected AbilityEffect(AbilityContext ctx, int maxTicks) {
        super(maxTicks);
        this.ctx = ctx;
        this.caster = ctx.caster();
        this.def = ctx.def();
    }

    @Override
    public UUID owner() {
        return caster.getUniqueId();
    }

    protected boolean casterGone() {
        return !caster.isOnline() || caster.isDead();
    }

    protected static List<Player> viewers(Location location) {
        return Fx.viewers(location);
    }
}
