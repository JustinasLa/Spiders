package net.tfminecraft.spiders.commands;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockIdSuggestionsTest {
    @Test
    @DisplayName("block id suggestions skip legacy materials, whose keys can't be read")
    void blockIdSuggestionsSkipLegacyMaterials() {
        List<String> ids = SetupCommands.getBlockIds();
        assertTrue(ids.contains("minecraft:stone"), "stone missing");
        assertFalse(ids.stream().anyMatch(id -> id.contains("legacy_")), "a legacy id was suggested");
    }
}
