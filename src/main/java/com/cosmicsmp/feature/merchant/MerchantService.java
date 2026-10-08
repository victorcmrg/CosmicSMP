package com.cosmicsmp.feature.merchant;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.core.Keys;
import com.cosmicsmp.core.config.Settings;
import com.cosmicsmp.core.data.GlobalData;
import com.cosmicsmp.core.fx.DisplayFx;
import com.cosmicsmp.core.fx.Fx;
import com.cosmicsmp.core.fx.ParticleSpec;
import com.cosmicsmp.core.text.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Galactic Merchant NPCs.
 * <p>
 * Merchant points are stored in {@code global.json}; the NPC entities themselves are <b>not persistent</b>. They are
 * spawned when their chunk loads and simply vanish when it unloads, so a crash can never duplicate a merchant or leave
 * a stray NPC. A watchdog re-spawns any merchant that disappears (e.g. /kill).
 */
public final class MerchantService {

    private static final class Live {
        Entity npc;
        TextDisplay nameplate;
        boolean bobUp;
    }

    private final CosmicSMP plugin;
    private final Map<String, Live> live = new HashMap<>();
    private BukkitTask watchdog;
    private BukkitTask ambience;

    public MerchantService(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    public void start() {
        for (String id : plugin.global().data().merchants.keySet()) {
            ensure(id);
        }
        watchdog = Bukkit.getScheduler().runTaskTimer(plugin, this::watchdog, 100L, 100L);
        ambience = Bukkit.getScheduler().runTaskTimer(plugin, this::ambience, 20L, 10L);
    }

    public void stop() {
        if (watchdog != null) {
            watchdog.cancel();
        }
        if (ambience != null) {
            ambience.cancel();
        }
        for (String id : new ArrayList<>(live.keySet())) {
            despawn(id);
        }
    }

    public void reload() {
        stop();
        start();
    }

    public Map<String, GlobalData.MerchantPoint> points() {
        return plugin.global().data().merchants;
    }

    public String create(Location location, String requestedId) {
        String id = requestedId == null || requestedId.isBlank() ? nextId() : requestedId.toLowerCase(Locale.ROOT);
        points().put(id, new GlobalData.MerchantPoint(location.getWorld().getName(), location.getX(), location.getY(), location.getZ(),
                location.getYaw(), 0));
        plugin.global().markDirty();
        ensure(id);
        return id;
    }

    public boolean remove(String id) {
        if (points().remove(id) == null) {
            return false;
        }
        despawn(id);
        plugin.global().markDirty();
        return true;
    }

    public String nearest(Location location, double maxDistance) {
        String best = null;
        double bestDistance = maxDistance * maxDistance;
        for (Map.Entry<String, GlobalData.MerchantPoint> entry : points().entrySet()) {
            Location point = location(entry.getValue());
            if (point == null || !point.getWorld().equals(location.getWorld())) {
                continue;
            }
            double d = point.distanceSquared(location);
            if (d <= bestDistance) {
                bestDistance = d;
                best = entry.getKey();
            }
        }
        return best;
    }

    public Location location(GlobalData.MerchantPoint point) {
        World world = Bukkit.getWorld(point.world);
        return world == null ? null : new Location(world, point.x, point.y, point.z, point.yaw, point.pitch);
    }

    private String nextId() {
        int i = 1;
        while (points().containsKey("merchant-" + i)) {
            i++;
        }
        return "merchant-" + i;
    }

    public static String merchantId(Entity entity) {
        return entity.getPersistentDataContainer().get(Keys.MERCHANT, PersistentDataType.STRING);
    }

    /** Spawns the merchant if its chunk is loaded and it's not alive. */
    public void ensure(String id) {
        GlobalData.MerchantPoint point = points().get(id);
        if (point == null) {
            return;
        }
        Location location = location(point);
        if (location == null || !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        Live current = live.get(id);
        if (current != null && current.npc != null && current.npc.isValid()) {
            return;
        }
        despawn(id);
        spawn(id, location);
    }

    private void spawn(String id, Location location) {
        Settings s = plugin.settings();
        Entity npc = plugin.hooks().spawnEntity(s.merchantEntity, location, EntityType.WANDERING_TRADER, e -> {
            e.setPersistent(false);
            e.getPersistentDataContainer().set(Keys.MERCHANT, PersistentDataType.STRING, id);
            e.setInvulnerable(true);
            e.setSilent(true);
            e.setGlowing(s.merchantGlowing);
            if (e instanceof Mob mob) {
                mob.setAI(false);
                mob.setRemoveWhenFarAway(false);
                mob.setCanPickupItems(false);
            }
            if (e instanceof LivingEntity living) {
                living.setCollidable(false);
                AttributeInstance scale = living.getAttribute(Attribute.SCALE);
                if (scale != null) {
                    scale.setBaseValue(s.merchantScale);
                }
            }
            if (e instanceof WanderingTrader trader) {
                trader.setDespawnDelay(0);
            }
            if (e instanceof AbstractVillager villager) {
                villager.setRecipes(List.of());
            }
        });
        // MythicMobs-spawned entities don't pass through the consumer before spawning: re-apply the essentials
        npc.setPersistent(false);
        npc.getPersistentDataContainer().set(Keys.MERCHANT, PersistentDataType.STRING, id);
        plugin.hooks().applyModel(npc, s.merchantModel);

        Live entry = new Live();
        entry.npc = npc;
        if (!s.merchantNameplate.isEmpty()) {
            Component text = Text.parse(String.join("\n", s.merchantNameplate));
            Location plate = location.clone().add(0, s.merchantNameplateHeight * Math.max(0.5, s.merchantScale), 0);
            entry.nameplate = DisplayFx.text(plugin, plate, text, d -> {
                d.setLineWidth(220);
                d.setViewRange(0.5f);
            });
        }
        live.put(id, entry);
    }

    private void despawn(String id) {
        Live entry = live.remove(id);
        if (entry == null) {
            return;
        }
        if (entry.npc != null && entry.npc.isValid()) {
            plugin.hooks().removeModel(entry.npc);
            entry.npc.remove();
        }
        plugin.tempEntities().remove(entry.nameplate);
    }

    /** A chunk loaded: spawn merchants that belong to it. */
    public void onChunkLoad(Chunk chunk) {
        for (Map.Entry<String, GlobalData.MerchantPoint> entry : points().entrySet()) {
            GlobalData.MerchantPoint p = entry.getValue();
            if (p.world.equals(chunk.getWorld().getName()) && ((int) Math.floor(p.x)) >> 4 == chunk.getX()
                    && ((int) Math.floor(p.z)) >> 4 == chunk.getZ()) {
                Bukkit.getScheduler().runTask(plugin, () -> ensure(entry.getKey()));
            }
        }
    }

    /** A chunk unloaded: non-persistent NPCs are discarded by the server, forget them. */
    public void onChunkUnload(Chunk chunk) {
        for (Map.Entry<String, GlobalData.MerchantPoint> entry : points().entrySet()) {
            GlobalData.MerchantPoint p = entry.getValue();
            if (p.world.equals(chunk.getWorld().getName()) && ((int) Math.floor(p.x)) >> 4 == chunk.getX()
                    && ((int) Math.floor(p.z)) >> 4 == chunk.getZ()) {
                despawn(entry.getKey());
            }
        }
    }

    private void watchdog() {
        for (String id : points().keySet()) {
            ensure(id);
        }
    }

    /** Floating nameplate (client-interpolated bobbing) and ambient particles, only when someone is near. */
    private void ambience() {
        if (live.isEmpty()) {
            return;
        }
        ParticleSpec portal = ParticleSpec.of(Particle.REVERSE_PORTAL, 1, 0.0, 0.0);
        ParticleSpec sparkle = ParticleSpec.dust("#c084fc", 1.0f);
        long tick = Bukkit.getCurrentTick();
        for (Live entry : live.values()) {
            if (entry.npc == null || !entry.npc.isValid()) {
                continue;
            }
            Location base = entry.npc.getLocation();
            List<Player> viewers = Fx.viewers(base, 24);
            if (viewers.isEmpty()) {
                continue;
            }
            if (entry.nameplate != null && tick % 40 < 10) {
                entry.bobUp = !entry.bobUp;
                DisplayFx.animate(entry.nameplate, 40, DisplayFx.transform(0, entry.bobUp ? 0.12f : 0f, 0, 1f));
            }
            if (plugin.settings().merchantParticles) {
                double height = entry.npc.getHeight();
                double angle = tick * 0.15;
                for (int i = 0; i < 3; i++) {
                    double a = angle + i * (Math.PI * 2 / 3);
                    double y = base.getY() + (Math.sin(angle * 0.5 + i) * 0.5 + 0.5) * height;
                    sparkle.spawn(base.getX() + Math.cos(a) * 0.9, y, base.getZ() + Math.sin(a) * 0.9, viewers);
                }
                portal.spawn(base.getX(), base.getY() + height * 0.6, base.getZ(), viewers);
            }
        }
    }

    public int liveCount() {
        return live.size();
    }
}
