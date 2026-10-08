package com.cosmicsmp.core.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Typed, cached view over {@code config.yml}. Values are read once per (re)load so hot paths never touch YAML.
 */
public final class Settings {

    public enum TreeMode { FREE, SEQUENTIAL }

    public enum CycleControl { SWAP_HAND, SNEAK_RIGHT_CLICK }

    private final YamlFile file;

    // general
    public boolean debug;
    public Set<String> disabledWorlds = Set.of();

    // storage / performance
    public int autosaveMinutes;
    public int particleViewDistance;
    public int hudUpdateTicks;
    public boolean hudEnabled;

    // brightness
    public int brightnessMin;
    public int brightnessMax;
    public int brightnessStart;
    public int gainOnKill;
    public int loseOnDeath;
    public boolean loseOnAnyDeath;
    public boolean antiFarm;
    public long antiFarmMillis;
    public boolean brightnessTitles;
    public boolean brightnessHolograms;

    // perks
    public boolean speedEnabled;
    public int speedRequired;
    public int speedAmplifier;
    public boolean dashEnabled;
    public int dashRequired;
    public double dashDistance;
    public int dashTicks;
    public int dashCooldownSeconds;
    public int dashNoFallSeconds;

    // death ban
    public boolean banEnabled;
    public long banDurationMinutes;
    public int banResetBrightness;
    public boolean banBroadcast;
    public boolean banBypassOps;

    // stars
    public boolean evaporateOnDeath;
    public boolean resetAbilitiesOnDeath;
    public boolean purchasesRequireMerchant;
    public boolean autoRestoreItems;
    public boolean targetPlayers;
    public boolean targetMobs;
    public TreeMode treeMode;
    public boolean starAnimation;

    // controls
    public CycleControl cycleControl;
    public long castThrottleMillis;

    // stock
    public boolean restockEnabled;
    public long restockIntervalMillis;
    public int restockAmount;

    // combat
    public String damageType;
    public boolean ignoreInvulnerability;
    public long killCreditMillis;

    // merchant
    public String merchantEntity;
    public String merchantModel;
    public double merchantScale;
    public boolean merchantGlowing;
    public List<String> merchantNameplate;
    public double merchantNameplateHeight;
    public boolean merchantParticles;

    // hooks
    public boolean hookItemsAdder;
    public boolean hookMythicMobs;
    public boolean hookBetterModel;

    public Settings(JavaPlugin plugin) {
        this.file = new YamlFile(plugin, "config.yml", true);
    }

    public void load() {
        file.load();
        YamlConfiguration c = file.get();

        debug = c.getBoolean("general.debug", false);
        disabledWorlds = lower(c.getStringList("general.disabled-worlds"));

        autosaveMinutes = Math.max(1, c.getInt("storage.autosave-minutes", 5));
        particleViewDistance = Math.max(8, c.getInt("performance.particle-view-distance", 48));
        hudEnabled = c.getBoolean("performance.hud.enabled", true);
        hudUpdateTicks = Math.max(2, c.getInt("performance.hud.update-ticks", 5));

        brightnessMin = c.getInt("brightness.min", -3);
        brightnessMax = c.getInt("brightness.max", 7);
        brightnessStart = c.getInt("brightness.start", 0);
        gainOnKill = c.getInt("brightness.gain-on-kill", 1);
        loseOnDeath = c.getInt("brightness.lose-on-death", 1);
        loseOnAnyDeath = c.getBoolean("brightness.lose-on-any-death", false);
        antiFarm = c.getBoolean("brightness.anti-farm.enabled", true);
        antiFarmMillis = c.getLong("brightness.anti-farm.same-victim-cooldown-seconds", 600) * 1000L;
        brightnessTitles = c.getBoolean("brightness.feedback.titles", true);
        brightnessHolograms = c.getBoolean("brightness.feedback.holograms", true);

        speedEnabled = c.getBoolean("perks.speed.enabled", true);
        speedRequired = c.getInt("perks.speed.required-brightness", 5);
        speedAmplifier = Math.max(0, c.getInt("perks.speed.amplifier", 0));
        dashEnabled = c.getBoolean("perks.dash.enabled", true);
        dashRequired = c.getInt("perks.dash.required-brightness", 7);
        dashDistance = c.getDouble("perks.dash.distance", 15);
        dashTicks = Math.max(2, c.getInt("perks.dash.duration-ticks", 6));
        dashCooldownSeconds = Math.max(0, c.getInt("perks.dash.cooldown-seconds", 8));
        dashNoFallSeconds = Math.max(0, c.getInt("perks.dash.no-fall-seconds", 4));

        banEnabled = c.getBoolean("death-ban.enabled", true);
        banDurationMinutes = c.getLong("death-ban.duration-minutes", 0);
        banResetBrightness = c.getInt("death-ban.reset-brightness-on-return", 0);
        banBroadcast = c.getBoolean("death-ban.broadcast", true);
        banBypassOps = c.getBoolean("death-ban.bypass-ops", false);

        evaporateOnDeath = c.getBoolean("stars.evaporate-on-death", true);
        resetAbilitiesOnDeath = c.getBoolean("stars.reset-abilities-on-death", true);
        purchasesRequireMerchant = c.getBoolean("stars.purchases-require-merchant", true);
        autoRestoreItems = c.getBoolean("stars.auto-restore-items", true);
        targetPlayers = c.getBoolean("stars.ability-targets.players", true);
        targetMobs = c.getBoolean("stars.ability-targets.mobs", true);
        treeMode = enumValue(TreeMode.class, c.getString("stars.skill-tree-mode"), TreeMode.FREE);
        starAnimation = c.getBoolean("stars.purchase-animation", true);

        cycleControl = enumValue(CycleControl.class, c.getString("controls.cycle-ability"), CycleControl.SWAP_HAND);
        castThrottleMillis = Math.max(50, c.getLong("controls.click-throttle-ms", 200));

        restockEnabled = c.getBoolean("stock.restock.enabled", false);
        restockIntervalMillis = Math.max(1, c.getLong("stock.restock.interval-minutes", 1440)) * 60_000L;
        restockAmount = Math.max(1, c.getInt("stock.restock.amount", 1));

        damageType = c.getString("combat.damage-type", "magic");
        ignoreInvulnerability = c.getBoolean("combat.ignore-invulnerability-ticks", true);
        killCreditMillis = c.getLong("combat.kill-credit-seconds", 15) * 1000L;

        ConfigurationSection m = c.getConfigurationSection("merchant");
        merchantEntity = m == null ? "WANDERING_TRADER" : m.getString("entity", "WANDERING_TRADER");
        merchantModel = m == null ? "" : m.getString("model", "");
        merchantScale = m == null ? 1.0 : m.getDouble("scale", 1.0);
        merchantGlowing = m != null && m.getBoolean("glowing", false);
        merchantNameplate = m == null ? List.of() : m.getStringList("nameplate");
        merchantNameplateHeight = m == null ? 2.6 : m.getDouble("nameplate-height", 2.6);
        merchantParticles = m == null || m.getBoolean("ambient-particles", true);

        hookItemsAdder = c.getBoolean("hooks.itemsadder", true);
        hookMythicMobs = c.getBoolean("hooks.mythicmobs", true);
        hookBetterModel = c.getBoolean("hooks.bettermodel", true);
    }

    public boolean moduleEnabled(String id) {
        return file.get().getBoolean("modules." + id, true);
    }

    public boolean worldDisabled(String worldName) {
        return disabledWorlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public YamlConfiguration raw() {
        return file.get();
    }

    private static Set<String> lower(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String v : values) {
            out.add(v.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value, E def) {
        if (value == null) {
            return def;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return def;
        }
    }
}
