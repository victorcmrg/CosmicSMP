package com.cosmicsmp.feature.ability.impl.elder;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.feature.ability.CombatService;
import com.cosmicsmp.feature.ability.PassiveAbility;
import com.cosmicsmp.feature.star.AbilityDefinition;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/** Elder Star passive: hits (melee or projectile) inflict Mining Fatigue for 5 seconds. */
public final class FatigueStrikePassive implements PassiveAbility {

    public static final String ID = "fatigue_strike";
    private static final ParticleSpec MARK = ParticleSpec.dust("#5eead4", 1.0f, 6, 0.3);

    private final CosmicSMP plugin;

    public FatigueStrikePassive(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @Override
    public String id() {
        return ID;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        Player attacker = CombatService.attacker(event.getDamager());
        if (attacker == null || !(event.getEntity() instanceof LivingEntity victim) || victim.equals(attacker)
                || !plugin.passives().has(attacker, ID)) {
            return;
        }
        AbilityDefinition def = plugin.passives().definition(attacker, ID);
        int seconds = def == null ? 5 : def.integer("duration-seconds", 5);
        int amplifier = def == null ? 2 : def.integer("amplifier", 2);
        victim.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, seconds * 20, amplifier, false, true, true));
        MARK.spawn(victim.getLocation().add(0, victim.getHeight() * 0.6, 0), Fx.viewers(victim.getLocation()));
    }
}
