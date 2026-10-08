package com.cosmicsmp.feature.star;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.api.event.AbilityCastEvent;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.ability.Ability;
import com.cosmicsmp.feature.ability.AbilityContext;
import com.cosmicsmp.feature.ability.CastResult;
import com.cosmicsmp.feature.ability.CooldownService;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;

import java.util.logging.Level;

/** Validates and executes primary ability casts. */
public final class CastService {

    private final CosmicSMP plugin;

    public CastService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void castSelected(Player player, String starId) {
        StarDefinition star = plugin.stars().get(starId);
        PlayerData.OwnedStar owned = plugin.players().get(player).star(starId);
        if (star == null || owned == null) {
            plugin.messages().actionBar(player, "abilities.not-owned", Placeholders.empty());
            plugin.starItems().sanitize(player);
            return;
        }
        if (owned.selected == 0) {
            plugin.messages().actionBar(player, "abilities.none-unlocked", Placeholders.of("star", star.displayName()));
            return;
        }
        cast(player, star, owned.selected);
    }

    public CastResult cast(Player player, StarDefinition star, int slot) {
        PlayerData data = plugin.players().get(player);
        PlayerData.OwnedStar owned = data.star(star.id());
        AbilityDefinition def = star.ability(slot);
        if (owned == null || def == null || slot < 1) {
            return CastResult.BLOCKED;
        }
        Placeholders ph = Placeholders.of("star", star.displayName(), "ability", def.name());
        if (player.getGameMode() == GameMode.SPECTATOR || plugin.settings().worldDisabled(player.getWorld().getName())) {
            plugin.messages().actionBar(player, "abilities.disabled-world", ph);
            return CastResult.BLOCKED;
        }
        if (!owned.unlocked(slot)) {
            plugin.messages().actionBar(player, "abilities.locked", ph);
            return CastResult.BLOCKED;
        }
        if (plugin.combat().isIntangible(player.getUniqueId())) {
            plugin.messages().actionBar(player, "abilities.intangible", ph);
            return CastResult.BLOCKED;
        }
        Ability ability = plugin.abilities().active(def.abilityId());
        if (ability == null) {
            plugin.messages().actionBar(player, "abilities.unavailable", ph);
            return CastResult.BLOCKED;
        }
        long left = plugin.cooldowns().remainingMillis(data, def.key());
        if (left > 0 && !player.hasPermission("cosmicsmp.bypass.cooldown")) {
            plugin.messages().actionBar(player, "abilities.cooldown", ph.with("time", CooldownService.format(left)));
            plugin.hud().hold(player, 900);
            return CastResult.BLOCKED;
        }
        AbilityCastEvent event = new AbilityCastEvent(player, star.id(), slot, def.abilityId());
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return CastResult.BLOCKED;
        }
        CastResult result;
        try {
            result = ability.cast(new AbilityContext(plugin, player, star, def));
        } catch (RuntimeException ex) {
            plugin.getLogger().log(Level.WARNING, "Ability " + def.abilityId() + " failed for " + player.getName(), ex);
            result = CastResult.BLOCKED;
        }
        switch (result) {
            case SUCCESS -> {
                if (!player.hasPermission("cosmicsmp.bypass.cooldown")) {
                    plugin.cooldowns().set(data, def.key(), def.cooldownSeconds() * 1000L);
                }
                data.stats.abilitiesCast++;
                plugin.starItems().updateCooldownOverlay(player, star.id());
                plugin.messages().actionBar(player, "abilities.cast", ph);
            }
            case NO_TARGET -> plugin.messages().actionBar(player, "abilities.no-target", ph);
            case BLOCKED -> plugin.messages().actionBar(player, "abilities.blocked", ph);
        }
        plugin.hud().hold(player, 1200);
        return result;
    }
}
