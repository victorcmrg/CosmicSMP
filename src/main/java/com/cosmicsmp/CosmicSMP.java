package com.cosmicsmp;

import com.cosmicsmp.api.CosmicAPI;
import com.cosmicsmp.core.CoreListener;
import com.cosmicsmp.core.Keys;
import com.cosmicsmp.core.command.CommandManager;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.GlobalDataManager;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.data.PlayerDataManager;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.hook.HookManager;
import com.cosmicsmp.core.menu.MenuManager;
import com.cosmicsmp.core.module.ModuleManager;
import com.cosmicsmp.core.task.EffectManager;
import com.cosmicsmp.core.task.TempEntities;
import com.cosmicsmp.core.text.Messages;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.core.text.Text;
import com.cosmicsmp.feature.CosmicCommands;
import com.cosmicsmp.feature.ability.AbilityRegistry;
import com.cosmicsmp.feature.ability.CombatService;
import com.cosmicsmp.feature.ability.CooldownService;
import com.cosmicsmp.feature.ability.PassiveService;
import com.cosmicsmp.feature.ability.SummonService;
import com.cosmicsmp.feature.ability.TimedStateService;
import com.cosmicsmp.feature.ability.impl.BuiltinAbilities;
import com.cosmicsmp.feature.brightness.BrightnessModule;
import com.cosmicsmp.feature.brightness.BrightnessService;
import com.cosmicsmp.feature.brightness.DeathBanService;
import com.cosmicsmp.feature.brightness.PerkService;
import com.cosmicsmp.feature.menu.MerchantMenu;
import com.cosmicsmp.feature.menu.ProfileMenu;
import com.cosmicsmp.feature.menu.StockMenu;
import com.cosmicsmp.feature.merchant.MerchantModule;
import com.cosmicsmp.feature.merchant.MerchantService;
import com.cosmicsmp.feature.star.CastService;
import com.cosmicsmp.feature.star.HudService;
import com.cosmicsmp.feature.star.StarItemService;
import com.cosmicsmp.feature.star.StarModule;
import com.cosmicsmp.feature.star.StarRegistry;
import com.cosmicsmp.feature.star.StarService;
import com.cosmicsmp.feature.star.StockService;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Cosmic SMP - Season 3.
 * <p>
 * Boot order: configs -> hooks -> engines (effects, temp entities, data) -> content (stars, menus) -> services ->
 * modules -> commands. Shutdown runs in reverse and flushes every byte of data synchronously.
 */
public final class CosmicSMP extends JavaPlugin {

    private Settings settings;
    private Messages messages;
    private HookManager hooks;
    private EffectManager effects;
    private TempEntities tempEntities;
    private PlayerDataManager players;
    private GlobalDataManager global;
    private MenuManager menus;
    private ModuleManager modules;
    private CommandManager commands;

    private StarRegistry stars;
    private AbilityRegistry abilities;
    private CooldownService cooldowns;
    private CombatService combat;
    private SummonService summons;
    private PassiveService passives;
    private TimedStateService timedStates;
    private StockService stock;
    private StarItemService starItems;
    private StarService starService;
    private CastService casts;
    private HudService hud;
    private BrightnessService brightness;
    private DeathBanService deathBans;
    private PerkService perks;
    private MerchantService merchants;

    @Override
    public void onEnable() {
        long start = System.currentTimeMillis();
        Keys.init(this);

        settings = new Settings(this);
        settings.load();
        messages = new Messages(this);
        messages.load();
        Fx.viewDistance(settings.particleViewDistance);
        hooks = new HookManager(this);

        effects = new EffectManager(this);
        effects.start();
        tempEntities = new TempEntities(this);
        tempEntities.start();
        global = new GlobalDataManager(this);
        global.load();
        players = new PlayerDataManager(this);

        stars = new StarRegistry(this);
        stars.load();
        menus = new MenuManager(this);
        menus.load();
        menus.start();

        cooldowns = new CooldownService();
        combat = new CombatService(this);
        summons = new SummonService(this);
        timedStates = new TimedStateService(this);
        passives = new PassiveService(this);
        stock = new StockService(this);
        starItems = new StarItemService(this);
        starService = new StarService(this);
        casts = new CastService(this);
        hud = new HudService(this);
        brightness = new BrightnessService(this);
        deathBans = new DeathBanService(this);
        perks = new PerkService(this);
        merchants = new MerchantService(this);

        abilities = new AbilityRegistry();
        BuiltinAbilities.register(this, abilities);
        registerMenus();

        players.start();
        Bukkit.getPluginManager().registerEvents(new CoreListener(this), this);

        modules = new ModuleManager(this);
        modules.register(new BrightnessModule(this));
        modules.register(new StarModule(this));
        modules.register(new MerchantModule(this));
        modules.enableAll();

        commands = new CommandManager(this);
        new CosmicCommands(this).register(commands);
        commands.bind(getCommand("cosmic"));

        CosmicAPI.init(this);
        // validate after every plugin enabled, so expansion abilities registered in their onEnable count
        Bukkit.getScheduler().runTask(this, stars::validate);

        Bukkit.getConsoleSender().sendMessage(Text.parse(
                "<gradient:#a855f7:#22d3ee><bold>CosmicSMP</bold></gradient> <gray>v" + getPluginMeta().getVersion()
                        + " enabled in " + (System.currentTimeMillis() - start) + "ms <dark_gray>(" + stars.all().size()
                        + " stars, " + abilities.actives().size() + " primaries, " + abilities.passives().size() + " passives)"));
    }

    private void registerMenus() {
        menus.registerFactory("profile", (player, parent) -> new ProfileMenu(this, player, parent));
        menus.registerFactory("merchant", (player, parent) -> new MerchantMenu(this, player, parent,
                parent instanceof MerchantMenu || player.hasPermission("cosmicsmp.merchant.remote")));
        menus.registerFactory("stock", (player, parent) -> player.hasPermission("cosmicsmp.admin.stock") ? new StockMenu(this, player, parent) : null);
        menus.basePlaceholders(this::basePlaceholders);
    }

    private Placeholders basePlaceholders(Player player) {
        PlayerData data = players.get(player);
        return brightness.placeholders(player, data.brightness)
                .with("kills", data.stats.kills)
                .with("deaths", data.stats.deaths)
                .with("stars_owned", data.stars.size())
                .with("stars_discovered", data.discovered.size())
                .with("stars_bought", data.stats.starsBought)
                .with("abilities_cast", data.stats.abilitiesCast)
                .with("brightness_spent", data.stats.brightnessSpent)
                .with("speed_required", settings.speedRequired)
                .with("dash_required", settings.dashRequired)
                .with("ban_threshold", settings.brightnessMin);
    }

    /** {@code /cosmic admin reload}: configs, messages, menus, stars and hooks. Player data is untouched. */
    public void reload() {
        menus.closeAll();
        settings.load();
        messages.load();
        Text.clearCache();
        Fx.viewDistance(settings.particleViewDistance);
        hooks.reload();
        stars.load();
        menus.load();
        modules.reloadAll();
        stars.validate();
    }

    @Override
    public void onDisable() {
        if (menus != null) {
            menus.closeAll();
        }
        if (effects != null) {
            effects.shutdown();
        }
        if (modules != null) {
            modules.disableAll();
        }
        if (tempEntities != null) {
            tempEntities.removeAll();
        }
        if (players != null) {
            players.shutdown();
        }
        if (global != null) {
            global.saveSync();
        }
    }

    // ------------------------------------------------------------------ accessors

    public Settings settings() {
        return settings;
    }

    public Messages messages() {
        return messages;
    }

    public HookManager hooks() {
        return hooks;
    }

    public EffectManager effects() {
        return effects;
    }

    public TempEntities tempEntities() {
        return tempEntities;
    }

    public PlayerDataManager players() {
        return players;
    }

    public GlobalDataManager global() {
        return global;
    }

    public MenuManager menus() {
        return menus;
    }

    public ModuleManager modules() {
        return modules;
    }

    public CommandManager commands() {
        return commands;
    }

    public StarRegistry stars() {
        return stars;
    }

    public AbilityRegistry abilities() {
        return abilities;
    }

    public CooldownService cooldowns() {
        return cooldowns;
    }

    public CombatService combat() {
        return combat;
    }

    public SummonService summons() {
        return summons;
    }

    public PassiveService passives() {
        return passives;
    }

    public TimedStateService timedStates() {
        return timedStates;
    }

    public StockService stock() {
        return stock;
    }

    public StarItemService starItems() {
        return starItems;
    }

    public StarService starService() {
        return starService;
    }

    public CastService casts() {
        return casts;
    }

    public HudService hud() {
        return hud;
    }

    public BrightnessService brightness() {
        return brightness;
    }

    public DeathBanService deathBans() {
        return deathBans;
    }

    public PerkService perks() {
        return perks;
    }

    public MerchantService merchants() {
        return merchants;
    }
}
