package com.cosmicsmp;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import java.io.File;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StarFilesTest {

    private ServerMock server;
    private CosmicSMP plugin;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(CosmicSMP.class);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private File stars() {
        return new File(plugin.getDataFolder(), "stars");
    }

    @Test
    void allDefaultStarsLoadOnFirstStart() {
        assertEquals(5, plugin.stars().all().size());
        for (String id : new String[]{"sonic", "trail", "elder", "cosmic", "nature"}) {
            assertTrue(new File(stars(), id + ".yml").exists(), id + ".yml written");
            assertNotNull(plugin.stars().get(id), id + " loaded");
        }
    }

    @Test
    void deletedDefaultStarIsRestoredOnReload() {
        File trail = new File(stars(), "trail.yml");
        assertTrue(trail.delete());
        plugin.reload();
        assertTrue(trail.exists(), "trail.yml re-created");
        assertNotNull(plugin.stars().get("trail"), "trail loaded again");
    }

    @Test
    void deletedDefaultStarIsRestoredOnRestart() {
        File trail = new File(stars(), "trail.yml");
        assertTrue(trail.delete());
        server.getPluginManager().disablePlugin(plugin);
        server.getPluginManager().enablePlugin(plugin);
        assertTrue(trail.exists(), "trail.yml re-created");
        assertNotNull(plugin.stars().get("trail"));
    }

    @Test
    void renamedDefaultIsNotDuplicated() throws Exception {
        File trail = new File(stars(), "trail.yml");
        Files.move(trail.toPath(), new File(stars(), "dragon.yml").toPath());
        plugin.reload();
        assertTrue(!trail.exists(), "no duplicate trail.yml when another file declares id: trail");
        assertNotNull(plugin.stars().get("trail"));
        assertEquals(5, plugin.stars().all().size());
    }

    @Test
    void disabledStarStaysDisabled() throws Exception {
        File trail = new File(stars(), "trail.yml");
        Files.writeString(trail.toPath(), Files.readString(trail.toPath()).replace("enabled: true", "enabled: false"));
        plugin.reload();
        assertNull(plugin.stars().get("trail"));
        assertTrue(trail.exists());
    }
}
