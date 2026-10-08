package com.cosmicsmp.feature.merchant;

import com.cosmicsmp.CosmicSMP;
import com.cosmicsmp.feature.menu.MerchantMenu;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Opens the merchant menu on interaction and keeps the NPC untouchable. */
public final class MerchantListener implements Listener {

    private final CosmicSMP plugin;
    private final Map<UUID, Long> lastOpen = new HashMap<>();

    public MerchantListener(CosmicSMP plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractAt(PlayerInteractAtEntityEvent event) {
        handle(event.getPlayer(), event.getRightClicked(), event.getHand(), event);
    }

    private void handle(Player player, Entity entity, EquipmentSlot hand, org.bukkit.event.Cancellable event) {
        if (MerchantService.merchantId(entity) == null) {
            return;
        }
        event.setCancelled(true);
        if (hand != EquipmentSlot.HAND) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = lastOpen.put(player.getUniqueId(), now);
        if (last != null && now - last < 400) {
            return;
        }
        if (!player.hasPermission("cosmicsmp.merchant.use")) {
            plugin.messages().send(player, "command.no-permission");
            return;
        }
        new MerchantMenu(plugin, player, null, true).open();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (MerchantService.merchantId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCombust(EntityCombustEvent event) {
        if (MerchantService.merchantId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (MerchantService.merchantId(event.getEntity()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        plugin.merchants().onChunkLoad(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        plugin.merchants().onChunkUnload(event.getChunk());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastOpen.remove(event.getPlayer().getUniqueId());
    }
}
