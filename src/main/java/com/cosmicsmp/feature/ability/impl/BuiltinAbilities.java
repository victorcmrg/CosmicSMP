package com.cosmicsmp.feature.ability.impl;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.feature.ability.AbilityRegistry;
import com.cosmicsmp.feature.ability.impl.cosmic.AstralPhase;
import com.cosmicsmp.feature.ability.impl.cosmic.CometRide;
import com.cosmicsmp.feature.ability.impl.cosmic.MirrorClone;
import com.cosmicsmp.feature.ability.impl.cosmic.NightResistancePassive;
import com.cosmicsmp.feature.ability.impl.elder.ElderLaser;
import com.cosmicsmp.feature.ability.impl.elder.FatigueStrikePassive;
import com.cosmicsmp.feature.ability.impl.elder.ForceField;
import com.cosmicsmp.feature.ability.impl.elder.TidalWave;
import com.cosmicsmp.feature.ability.impl.nature.EarthClap;
import com.cosmicsmp.feature.ability.impl.nature.FlowerVitalityPassive;
import com.cosmicsmp.feature.ability.impl.nature.GiantBee;
import com.cosmicsmp.feature.ability.impl.nature.VineEruption;
import com.cosmicsmp.feature.ability.impl.sonic.SculkGrasp;
import com.cosmicsmp.feature.ability.impl.sonic.SonicBeam;
import com.cosmicsmp.feature.ability.impl.sonic.SpaceWarden;
import com.cosmicsmp.feature.ability.impl.sonic.WardenPeacePassive;
import com.cosmicsmp.feature.ability.impl.trail.DragonBreath;
import com.cosmicsmp.feature.ability.impl.trail.DragonFists;
import com.cosmicsmp.feature.ability.impl.trail.DragonRide;
import com.cosmicsmp.feature.ability.impl.trail.NoFallPassive;

/** Registers the Season 3 abilities. Ids are what {@code stars/*.yml} reference in {@code ability:}. */
public final class BuiltinAbilities {

    private BuiltinAbilities() {
    }

    public static void register(CosmicSMP plugin, AbilityRegistry registry) {
        // Sonic Star
        registry.register(new WardenPeacePassive(plugin));
        registry.register(new SculkGrasp());
        registry.register(new SonicBeam());
        registry.register(new SpaceWarden(plugin));
        // Trail Star
        registry.register(new NoFallPassive(plugin));
        registry.register(new DragonFists());
        registry.register(new DragonBreath());
        registry.register(new DragonRide());
        // Elder Star
        registry.register(new FatigueStrikePassive(plugin));
        registry.register(new ElderLaser());
        registry.register(new TidalWave());
        registry.register(new ForceField());
        // Cosmic Star
        registry.register(new NightResistancePassive());
        registry.register(new CometRide());
        registry.register(new MirrorClone(plugin));
        registry.register(new AstralPhase(plugin));
        // Nature Star
        registry.register(new FlowerVitalityPassive());
        registry.register(new VineEruption());
        registry.register(new GiantBee());
        registry.register(new EarthClap());
    }
}
