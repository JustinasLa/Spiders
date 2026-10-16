package net.tfminecraft.spiders.testing;

import org.bukkit.Sound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestRegistryAccessTest {
    @Test
    @DisplayName("sound constants initialise in unit tests and keep their keys")
    void soundConstantsInitialise() {
        assertNotNull(Sound.BLOCK_WOOL_HIT);
        assertEquals("block.wool.hit", Sound.BLOCK_WOOL_HIT.getKey().getKey());
        assertSame(Sound.BLOCK_WOOL_HIT, Sound.BLOCK_WOOL_HIT);
    }
}
