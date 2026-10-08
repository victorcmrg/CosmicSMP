package com.cosmicsmp.feature.star;

import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.item.ItemSpec;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;

import java.util.List;

/**
 * One node of a star's skill tree: slot 0 is the passive, slots 1-3 the primaries.
 * {@code settings} is the free-form section each ability implementation reads its tuning values from.
 */
public final class AbilityDefinition {

    private final String starId;
    private final int slot;
    private final String abilityId;
    private final String name;
    private final List<String> description;
    private final int cost;
    private final int cooldownSeconds;
    private final ItemSpec icon;
    private final ConfigurationSection settings;

    public AbilityDefinition(String starId, int slot, ConfigurationSection s) {
        this.starId = starId;
        this.slot = slot;
        this.abilityId = s.getString("ability", "");
        this.name = s.getString("name", abilityId);
        this.description = s.getStringList("description");
        this.cost = Math.max(0, s.getInt("cost", 0));
        this.cooldownSeconds = Math.max(0, s.getInt("cooldown", 0));
        this.icon = ItemSpec.of(s.getConfigurationSection("icon"), ItemSpec.of("NETHER_STAR", null, List.of()));
        ConfigurationSection section = s.getConfigurationSection("settings");
        this.settings = section == null ? new MemoryConfiguration() : section;
    }

    public String starId() {
        return starId;
    }

    public int slot() {
        return slot;
    }

    public boolean isPassive() {
        return slot == 0;
    }

    public String abilityId() {
        return abilityId;
    }

    public String name() {
        return name;
    }

    public List<String> description() {
        return description;
    }

    public int cost() {
        return cost;
    }

    public int cooldownSeconds() {
        return cooldownSeconds;
    }

    public ItemSpec icon() {
        return icon;
    }

    public ConfigurationSection settings() {
        return settings;
    }

    /** Unique key used for cooldown storage: {@code starId:slot}. */
    public String key() {
        return starId + ":" + slot;
    }

    // ------------------------------------------------------------------ typed settings helpers

    public double num(String path, double def) {
        return settings.getDouble(path, def);
    }

    public int integer(String path, int def) {
        return settings.getInt(path, def);
    }

    public boolean bool(String path, boolean def) {
        return settings.getBoolean(path, def);
    }

    public String string(String path, String def) {
        return settings.getString(path, def);
    }

    public SoundSpec sound(String path, SoundSpec def) {
        return SoundSpec.parse(settings.get("sounds." + path), def);
    }

    public ParticleSpec particle(String path, ParticleSpec def) {
        return ParticleSpec.parse(settings.get("particles." + path), def);
    }

    public ItemSpec item(String path, ItemSpec def) {
        ConfigurationSection section = settings.getConfigurationSection(path);
        return section == null ? def : ItemSpec.of(section, def);
    }

    /** BetterModel id configured for this ability's visual (summon/mount), empty for vanilla. */
    public String model(String path) {
        return settings.getString("models." + path, "");
    }
}
