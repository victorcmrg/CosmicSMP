package com.cosmicsmp.feature.brightness;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.fx.SoundSpec;
import com.cosmicsmp.core.task.EndReason;
import com.cosmicsmp.core.task.TimedEffect;
import com.cosmicsmp.core.text.Placeholders;
import com.cosmicsmp.feature.ability.CooldownService;
import com.cosmicsmp.feature.ability.Targeting;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Brightness perks: permanent Speed I at 5 brightness and a 15-block Dash at 7 (Sneak + F by default).
 * <p>
 * Speed is applied as a short effect refreshed every second instead of an infinite one, so if the plugin is removed
 * or the server crashes the effect simply runs out - it can never get stuck on a player.
 */
public final class PerkService implements Listener {

    private static final String DASH_KEY = "perk:dash";
    private static final int SPEED_DURATION = 60;

    private final CosmicSMP plugin;
    private final Map<UUID, Long> noFallUntil = new HashMap<>();
    private BukkitTask task;

    public PerkService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            removeSpeed(player);
        }
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void refresh(Player player) {
        Settings s = plugin.settings();
        boolean wantsSpeed = s.speedEnabled && plugin.players().get(player).brightness >= s.speedRequired
                && player.hasPermission("cosmicsmp.perk.speed") && !s.worldDisabled(player.getWorld().getName());
        if (wantsSpeed) {
            PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
            if (current == null || (isOurs(current) && current.getDuration() < SPEED_DURATION - 15)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, SPEED_DURATION, s.speedAmplifier, true, false, true));
            }
        } else {
            removeSpeed(player);
        }
    }

    private void removeSpeed(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.SPEED);
        if (current != null && isOurs(current)) {
            player.removePotionEffect(PotionEffectType.SPEED);
        }
    }

    /** Our refreshable effects are ambient, particle-less and short (beacons show particles, potions aren't ambient). */
    private static boolean isOurs(PotionEffect effect) {
        return effect.isAmbient() && !effect.hasParticles() && effect.getDuration() <= SPEED_DURATION;
    }

    public void grantNoFall(Player player, int ticks) {
        long until = System.currentTimeMillis() + ticks * 50L;
        noFallUntil.merge(player.getUniqueId(), until, Math::max);
    }

    public boolean dashReady(Player player) {
        return plugin.cooldowns().ready(plugin.players().get(player), DASH_KEY);
    }

    public long dashRemaining(Player player) {
        return plugin.cooldowns().remainingMillis(plugin.players().get(player), DASH_KEY);
    }

    public boolean canDash(Player player) {
        Settings s = plugin.settings();
        return s.dashEnabled && plugin.players().get(player).brightness >= s.dashRequired
                && player.hasPermission("cosmicsmp.perk.dash");
    }

    public void dash(Player player) {
        PlayerData data = plugin.players().get(player);
        long left = plugin.cooldowns().remainingMillis(data, DASH_KEY);
        if (left > 0 && !player.hasPermission("cosmicsmp.bypass.cooldown")) {
            plugin.hud().hold(player, 900);
            plugin.messages().actionBar(player, "perks.dash-cooldown", Placeholders.of("time", CooldownService.format(left)));
            return;
        }
        if (plugin.effects().find(player.getUniqueId(), DashEffect.class).isPresent() || player.isInsideVehicle()) {
            return;
        }
        Settings s = plugin.settings();
        plugin.cooldowns().set(data, DASH_KEY, s.dashCooldownSeconds * 1000L);
        plugin.effects().start(new DashEffect(player, s.dashDistance, s.dashTicks));
        grantNoFall(player, s.dashTicks + s.dashNoFallSeconds * 20);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !canDash(player) || player.getGameMode() == GameMode.SPECTATOR
                || plugin.settings().worldDisabled(player.getWorld().getName())) {
            return;
        }
        event.setCancelled(true);
        dash(player);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFall(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL || !(event.getEntity() instanceof Player player)) {
            return;
        }
        Long until = noFallUntil.get(player.getUniqueId());
        if (until != null) {
            if (until >= System.currentTimeMillis()) {
                event.setCancelled(true);
            } else {
                noFallUntil.remove(player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> refresh(event.getPlayer()), 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        noFallUntil.remove(event.getPlayer().getUniqueId());
    }

    /** Velocity-driven dash: exact distance, stops before walls, trail of light. */
    private static final class DashEffect extends TimedEffect {
        private static final ParticleSpec TRAIL = ParticleSpec.dust("#fde68a", 1.3f, 2, 0.15);
        private static final ParticleSpec SPARK = ParticleSpec.of(Particle.END_ROD, 1, 0.1, 0.02);
        private static final ParticleSpec BURST = ParticleSpec.of(Particle.CLOUD, 8, 0.3, 0.05);

        private final Player player;
        private final Vector step;

        DashEffect(Player player, double distance, int ticks) {
            super(ticks);
            this.player = player;
            Vector dir = player.getLocation().getDirection();
            dir.setY(Math.max(-0.25, Math.min(0.55, dir.getY())));
            dir.normalize();
            Location from = player.getLocation().add(0, 0.9, 0);
            double free = Targeting.freeDistance(from, dir, distance);
            this.step = dir.multiply(free / ticks);
        }

        @Override
        public UUID owner() {
            return player.getUniqueId();
        }

        @Override
        protected void onStart() {
            SoundSpec.of("entity.breeze.wind_burst", 0.9f, 1.3f).play(player.getLocation());
            SoundSpec.of("item.trident.riptide_1", 0.6f, 1.6f).play(player.getLocation());
            BURST.spawn(player.getLocation().add(0, 0.2, 0), Fx.viewers(player.getLocation()));
            if (step.lengthSquared() < 1.0E-4) {
                finish();
            }
        }

        @Override
        protected void onTick() {
            if (!player.isOnline() || player.isDead()) {
                cancel(EndReason.CANCELLED);
                return;
            }
            player.setVelocity(step);
            Location at = player.getLocation().add(0, 1, 0);
            List<Player> viewers = Fx.viewers(at);
            TRAIL.spawn(at, viewers);
            SPARK.spawn(at, viewers);
        }

        @Override
        protected void onEnd(EndReason reason) {
            if (player.isOnline()) {
                player.setVelocity(step.clone().multiply(0.25));
            }
        }
    }
}
