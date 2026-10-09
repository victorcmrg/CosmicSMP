package com.cosmicsmp;

import com.cosmicsmp.api.event.BrightnessChangeEvent;
import com.cosmicsmp.core.data.PlayerData;
import com.cosmicsmp.feature.star.StarDefinition;
import com.cosmicsmp.feature.star.StarItemService;
import com.cosmicsmp.feature.star.StarService;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GameplayTest {

    private ServerMock server;
    private CosmicSMP plugin;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(CosmicSMP.class);
        // cosmetic client-side features MockBukkit doesn't simulate
        plugin.settings().itemCooldownOverlay = false;
        plugin.settings().starAnimation = false;
        plugin.settings().brightnessHolograms = false;
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    /** Total number of star items (amounts included). */
    private int starItems() {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (StarItemService.isStar(item)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    @Test
    void brightnessIsCappedAtSeven() {
        plugin.brightness().set(player, 50, BrightnessChangeEvent.Cause.ADMIN);
        assertEquals(7, plugin.brightness().get(player));
    }

    @Test
    void buyingAStarCostsSixGivesTheItemAndUsesStock() {
        StarDefinition trail = plugin.stars().get("trail");
        int stockBefore = plugin.stock().stock(trail);
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);

        assertEquals(StarService.Result.SUCCESS, plugin.starService().purchase(player, trail));
        assertEquals(1, plugin.brightness().get(player));
        assertEquals(1, starItems());
        assertEquals(stockBefore - 1, plugin.stock().stock(trail));
        assertTrue(plugin.players().get(player).discovered.contains("trail"));
    }

    @Test
    void notEnoughBrightnessIsRefused() {
        plugin.brightness().set(player, 3, BrightnessChangeEvent.Cause.ADMIN);
        assertEquals(StarService.Result.NOT_ENOUGH_BRIGHTNESS, plugin.starService().purchase(player, plugin.stars().get("trail")));
        assertEquals(0, starItems());
    }

    @Test
    void unlockingNodesSpendsTheirCost() {
        StarDefinition sonic = plugin.stars().get("sonic");
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);
        plugin.starService().purchase(player, sonic);
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);

        assertEquals(StarService.Result.SUCCESS, plugin.starService().unlock(player, sonic, 1)); // 4
        assertEquals(StarService.Result.SUCCESS, plugin.starService().unlock(player, sonic, 0)); // 2
        assertEquals(1, plugin.brightness().get(player));
        PlayerData.OwnedStar owned = plugin.players().get(player).star("sonic");
        assertTrue(owned.passive && owned.primaries.contains(1));
        assertEquals(1, owned.selected);
        assertTrue(plugin.passives().has(player, "warden_peace"));
    }

    @Test
    void rebuyAfterDeathIgnoresStockEvenAtZero() {
        StarDefinition elder = plugin.stars().get("elder");
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);
        plugin.starService().purchase(player, elder);
        plugin.starService().evaporate(player, player.getLocation());
        assertFalse(plugin.players().get(player).owns("elder"));

        plugin.stock().set("elder", 0);
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);
        assertEquals(StarService.Result.SUCCESS, plugin.starService().purchase(player, elder));
        assertEquals(0, plugin.stock().stock(elder));
    }

    @Test
    void newPlayerCannotBuyOutOfStock() {
        StarDefinition nature = plugin.stars().get("nature");
        plugin.stock().set("nature", 0);
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);
        assertEquals(StarService.Result.OUT_OF_STOCK, plugin.starService().purchase(player, nature));
    }

    @Test
    void foreignOrDuplicateStarItemsAreRemoved() {
        StarDefinition cosmic = plugin.stars().get("cosmic");
        plugin.brightness().set(player, 7, BrightnessChangeEvent.Cause.ADMIN);
        plugin.starService().purchase(player, cosmic);
        ItemStack original = player.getInventory().getItem(plugin.starItems().find(player, "cosmic"));
        assertEquals(1, original.getMaxStackSize(), "star items never stack");
        ItemStack copy = original.clone();
        player.getInventory().setItem(20, copy);                // duplicate in another slot
        original.setAmount(3);                                    // and a forced stack
        player.getInventory().setItem(plugin.starItems().find(player, "cosmic"), original);
        assertEquals(4, starItems());
        plugin.starItems().sanitize(player);
        assertEquals(1, starItems());
    }
}
