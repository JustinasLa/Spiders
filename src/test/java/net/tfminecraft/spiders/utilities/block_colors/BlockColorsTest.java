package net.tfminecraft.spiders.utilities.block_colors;

import net.tfminecraft.spiders.utilities.colors.Oklab;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class BlockColorsTest {
    private static final Color GREY = Color.fromRGB(120, 120, 120);
    private static final Color BROWN = Color.fromRGB(130, 90, 60);

    private final List<BlockColorInfo> savedColorToBlock = new ArrayList<>();
    private final Map<Material, Color> savedBlockToColor = new LinkedHashMap<>();
    /** The occlusion each stubbed block type had before, to put back. */
    private final Map<Material, Boolean> occluding = new LinkedHashMap<>();

    @BeforeEach
    void save() {
        savedColorToBlock.addAll(BlockColorMaps.getColorToBlock());
        savedBlockToColor.putAll(BlockColorMaps.getBlockToColor());
    }

    @AfterEach
    void restore() {
        BlockColorMaps.getColorToBlock().clear();
        BlockColorMaps.getColorToBlock().addAll(savedColorToBlock);
        BlockColorMaps.getBlockToColor().clear();
        BlockColorMaps.getBlockToColor().putAll(savedBlockToColor);
        // the registry's block types are shared by every test
        occluding.forEach((material, before) -> when(material.asBlockType().isOccluding()).thenReturn(before));
    }

    private void occlude(Material... materials) {
        for (Material material : materials) {
            occluding.putIfAbsent(material, material.asBlockType().isOccluding());
            when(material.asBlockType().isOccluding()).thenReturn(true);
        }
    }

    private static InputStream json(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    private static BlockData block(Material material) {
        BlockData block = mock(BlockData.class);
        when(block.getMaterial()).thenReturn(material);
        return block;
    }

    private static BlockColorInfo info(Material material, int brightness, Color color) {
        return new BlockColorInfo(material, brightness, color, Oklab.toOklab(color));
    }

    @Test
    @DisplayName("the packaged colour table loads")
    void thePackagedTableLoads() {
        assertNotNullColor(BlockColorMaps.getBlockToColor().get(Material.STONE));
        assertNotNullColor(BlockColorMaps.getBlockToColor().get(Material.CAMPFIRE));
    }

    private static void assertNotNullColor(Color color) {
        assertTrue(color != null, "missing colour");
    }

    @Test
    @DisplayName("loading corrects a few colours, adds fluids and gives wood its log's colour")
    void loadingCorrectsAndExtends() {
        Map<Material, Color> colors = BlockColorMaps.loadBlocks(json("""
            {"moss_block": [90, 150, 30], "warped_planks": [40, 100, 90], "spruce_planks": [110, 80, 50],
             "crying_obsidian": [30, 10, 60], "oak_log": [100, 80, 50], "crimson_stem": [90, 20, 30],
             "not_a_block": [1, 2, 3], "grass_block": [0, 0, 0]}
            """));

        assertEquals(Color.fromRGB(90, 150, 30), colors.get(Material.GRASS_BLOCK), "grass looks like moss");
        assertEquals(Color.fromRGB(60, 100, 20), colors.get(Material.OAK_LEAVES));
        assertEquals(Color.fromRGB(48, 80, 16), colors.get(Material.BIRCH_LEAVES));
        assertEquals(Color.fromRGB(36, 60, 12), colors.get(Material.SPRUCE_LEAVES));
        assertEquals(Color.fromRGB(40, 100, 90), colors.get(Material.WARPED_TRAPDOOR));
        assertEquals(Color.fromRGB(110, 80, 50), colors.get(Material.BARREL));
        assertEquals(Color.fromRGB(30, 10, 60), colors.get(Material.RESPAWN_ANCHOR));
        assertEquals(Color.fromRGB(63, 118, 228), colors.get(Material.WATER));
        assertEquals(Color.fromRGB(207, 92, 20), colors.get(Material.LAVA));
        assertEquals(Color.fromRGB(100, 80, 50), colors.get(Material.OAK_WOOD));
        assertEquals(Color.fromRGB(90, 20, 30), colors.get(Material.CRIMSON_HYPHAE));
        assertEquals(17, colors.size(), "the unknown block is skipped: " + colors.keySet());
    }

    @Test
    @DisplayName("an unreadable table fails loudly")
    void anUnreadableTableFails() {
        InputStream broken = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("disk gone");
            }
        };
        assertThrows(UncheckedIOException.class, () -> BlockColorMaps.loadBlocks(broken));
    }

    @Test
    @DisplayName("display candidates are occluding full blocks at each brightness, without logs that have wood, spawners or shulker boxes")
    void displayCandidates() {
        occlude(Material.STONE, Material.DIRT, Material.OAK_LOG, Material.OAK_WOOD, Material.BIRCH_LOG, Material.SPAWNER, Material.TRIAL_SPAWNER, Material.SHULKER_BOX);
        Map<Material, Color> blocks = new LinkedHashMap<>();
        blocks.put(Material.STONE, GREY);
        blocks.put(Material.ANDESITE, GREY);
        blocks.put(Material.DIRT, BROWN);
        blocks.put(Material.GLASS, Color.fromRGB(200, 220, 230));
        blocks.put(Material.OAK_LOG, Color.fromRGB(100, 80, 50));
        blocks.put(Material.OAK_WOOD, Color.fromRGB(101, 80, 50));
        blocks.put(Material.BIRCH_LOG, Color.fromRGB(200, 200, 190));
        blocks.put(Material.SPAWNER, Color.fromRGB(1, 2, 3));
        blocks.put(Material.TRIAL_SPAWNER, Color.fromRGB(4, 5, 6));
        blocks.put(Material.SHULKER_BOX, Color.fromRGB(7, 8, 9));

        List<BlockColorInfo> candidates = BlockColorMaps.buildColorToBlock(blocks);

        List<Material> fullBright = candidates.stream().filter(it -> it.getBrightness() == 15).map(BlockColorInfo::getMaterial).toList();
        assertEquals(List.of(Material.STONE, Material.DIRT, Material.OAK_WOOD, Material.BIRCH_LOG), fullBright);
        BlockColorInfo first = candidates.getFirst();
        assertEquals(GREY, first.getRgb());
        assertEquals(Oklab.toOklab(GREY).getL(), first.getOklab().getL());
        // every block is black at brightness 0, and only the first black is kept
        assertEquals(1, candidates.stream().filter(it -> it.getRgb().equals(Color.BLACK)).count());
        assertTrue(candidates.stream().anyMatch(it -> it.getMaterial() == Material.STONE && it.getBrightness() == 7 && it.getRgb().equals(BlockColors.withBrightness(GREY, 7))));
    }

    @Test
    @DisplayName("slabs, stairs, walls and trapdoors take their full block's colour unless they have their own")
    void partialBlocksTakeTheirFullBlocksColour() {
        Color oak = Color.fromRGB(100, 80, 50);
        Color planks = Color.fromRGB(160, 130, 80);
        Color ownSlab = Color.fromRGB(1, 1, 1);
        Map<Material, Color> blocks = new LinkedHashMap<>();
        blocks.put(Material.OAK_LOG, oak);
        blocks.put(Material.STONE_SLAB, ownSlab);
        blocks.put(Material.STONE, GREY);
        blocks.put(Material.OAK_PLANKS, planks);
        blocks.put(Material.BRICKS, BROWN);
        blocks.put(Material.BIRCH_WOOD, Color.fromRGB(200, 200, 190));

        Map<Material, Color> colors = BlockColorMaps.buildBlockToColor(blocks);

        assertEquals(ownSlab, colors.get(Material.STONE_SLAB));
        assertEquals(GREY, colors.get(Material.STONE_STAIRS));
        assertEquals(planks, colors.get(Material.OAK_SLAB), "planks drop their suffix");
        assertEquals(planks, colors.get(Material.OAK_TRAPDOOR));
        assertEquals(BROWN, colors.get(Material.BRICK_WALL), "plurals drop their s");
        assertEquals(Color.fromRGB(200, 200, 190), colors.get(Material.BIRCH_STAIRS), "wood drops its suffix");
        assertEquals(oak, colors.get(Material.CAMPFIRE));
        assertFalse(colors.containsKey(Material.DIRT));
    }

    @Test
    @DisplayName("a block's colour can be dimmed to a light level")
    void blockColourAtABrightness() {
        BlockColorMaps.getBlockToColor().put(Material.STONE, Color.fromRGB(150, 90, 30));
        BlockColorMaps.getBlockToColor().remove(Material.BARRIER);

        assertEquals(Color.fromRGB(150, 90, 30), BlockColors.getBlockColor(block(Material.STONE)));
        assertEquals(Color.fromRGB(50, 30, 10), BlockColors.getBlockColor(block(Material.STONE), 5));
        assertNull(BlockColors.getBlockColor(block(Material.BARRIER)));
        assertNull(BlockColors.getBlockColor(block(Material.BARRIER), 5));
        assertEquals(Color.fromRGB(0, 0, 0), BlockColors.withBrightness(Color.fromRGB(150, 90, 30), 0));
    }

    @Test
    @DisplayName("the closest block colour wins, at full brightness unless dimmer blocks are allowed")
    void closestBlockColourWins() {
        List<BlockColorInfo> candidates = BlockColorMaps.getColorToBlock();
        candidates.clear();
        candidates.add(info(Material.STONE, 15, GREY));
        candidates.add(info(Material.DIRT, 15, BROWN));
        candidates.add(info(Material.STONE, 7, BlockColors.withBrightness(GREY, 7)));
        BlockData stone = mock(BlockData.class);
        BlockData dirt = mock(BlockData.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData(Material.STONE)).thenReturn(stone);
            bukkit.when(() -> Bukkit.createBlockData(Material.DIRT)).thenReturn(dirt);
            Color darkGrey = Color.fromRGB(55, 55, 55);

            BlockColorMatch dim = BlockColors.findBlockWithColor(darkGrey, true);
            assertSame(stone, dim.getBlock());
            assertEquals(7, dim.getBrightness());
            assertEquals(15, BlockColors.findBlockWithColor(darkGrey, false).getBrightness());
            assertSame(dirt, BlockColors.findBlockWithColor(Color.fromRGB(140, 90, 50), true).getBlock());

            BlockColorMatch dimOklab = BlockColors.findBlockWithColor(Oklab.toOklab(darkGrey), true);
            assertSame(stone, dimOklab.getBlock());
            assertEquals(7, dimOklab.getBrightness());
            assertEquals(15, BlockColors.findBlockWithColor(Oklab.toOklab(darkGrey), false).getBrightness());
            assertSame(dirt, BlockColors.findBlockWithColor(Oklab.toOklab(Color.fromRGB(140, 90, 50)), true).getBlock());
        }
    }

    @Test
    @DisplayName("with no candidates there is no match")
    void noCandidatesNoMatch() {
        List<BlockColorInfo> candidates = BlockColorMaps.getColorToBlock();
        candidates.clear();
        candidates.add(info(Material.STONE, 7, GREY));
        assertThrows(NoSuchElementException.class, () -> BlockColors.findBlockWithColor(GREY, false));
        assertThrows(NoSuchElementException.class, () -> BlockColors.findBlockWithColor(Oklab.toOklab(GREY), false));
    }
}
