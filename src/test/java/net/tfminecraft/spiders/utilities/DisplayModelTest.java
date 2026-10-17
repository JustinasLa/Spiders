package net.tfminecraft.spiders.utilities;

import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DisplayModelTest {
    private static BlockData block() {
        BlockData block = mock(BlockData.class);
        BlockData copy = mock(BlockData.class);
        when(block.clone()).thenReturn(copy);
        return block;
    }

    @Test
    @DisplayName("the shorter constructors leave no brightness, no tags and no item model")
    void constructorDefaults() {
        BlockData block = block();
        Matrix4f transform = new Matrix4f();
        Display.Brightness brightness = new Display.Brightness(3, 4);
        NamespacedKey model = new NamespacedKey("tfmcspiders", "leg");

        BlockDisplayModelPiece bare = new BlockDisplayModelPiece(block, transform);
        assertSame(block, bare.getBlock());
        assertSame(transform, bare.getTransform());
        assertNull(bare.getBrightness());
        assertTrue(bare.getTags().isEmpty());
        assertNull(bare.getItemModel());
        assertEquals(0f, bare.getItemModelData());

        assertSame(brightness, new BlockDisplayModelPiece(block, transform, brightness).getBrightness());
        assertEquals(List.of("leg"), new BlockDisplayModelPiece(block, transform, brightness, List.of("leg")).getTags());
        BlockDisplayModelPiece modelled = new BlockDisplayModelPiece(block, transform, brightness, List.of(), model);
        assertSame(model, modelled.getItemModel());
        assertEquals(0f, modelled.getItemModelData());
        assertEquals(2f, new BlockDisplayModelPiece(block, transform, brightness, List.of(), model, 2f).getItemModelData());
    }

    @Test
    @DisplayName("setters replace each property")
    void settersReplaceProperties() {
        BlockDisplayModelPiece piece = new BlockDisplayModelPiece(block(), new Matrix4f());
        BlockData block = block();
        Matrix4f transform = new Matrix4f().scale(2f);
        Display.Brightness brightness = new Display.Brightness(15, 15);
        NamespacedKey model = new NamespacedKey("tfmcspiders", "eye");
        piece.setBlock(block);
        piece.setTransform(transform);
        piece.setBrightness(brightness);
        piece.setTags(List.of("eye"));
        piece.setItemModel(model);
        piece.setItemModelData(5f);
        assertSame(block, piece.getBlock());
        assertSame(transform, piece.getTransform());
        assertSame(brightness, piece.getBrightness());
        assertEquals(List.of("eye"), piece.getTags());
        assertSame(model, piece.getItemModel());
        assertEquals(5f, piece.getItemModelData());
    }

    @Test
    @DisplayName("scaling applies after the piece's own transform")
    void scalingAppliesAfterTheTransform() {
        BlockDisplayModelPiece piece = new BlockDisplayModelPiece(block(), new Matrix4f().translate(1f, 0f, 0f));
        piece.scale(2f);
        assertEquals(new Vector3f(2f, 0f, 0f), piece.getTransform().getTranslation(new Vector3f()));
        piece.scale(1f, 3f, 0.5f);
        assertEquals(new Vector3f(2f, 0f, 0f), piece.getTransform().getTranslation(new Vector3f()));
        assertEquals(new Vector3f(2f, 6f, 1f), piece.getTransform().getScale(new Vector3f()));
    }

    @Test
    @DisplayName("a cloned piece shares nothing writable with the original")
    void cloneIsDeep() {
        BlockData block = block();
        Display.Brightness brightness = new Display.Brightness(7, 9);
        NamespacedKey model = new NamespacedKey("tfmcspiders", "fang");
        BlockDisplayModelPiece piece = new BlockDisplayModelPiece(block, new Matrix4f().scale(2f), brightness, new ArrayList<>(List.of("fang")), model, 3f);

        BlockDisplayModelPiece clone = piece.clone();
        assertSame(block.clone(), clone.getBlock());
        assertEquals(piece.getTransform(), clone.getTransform());
        assertNotSame(piece.getTransform(), clone.getTransform());
        assertEquals(brightness, clone.getBrightness());
        assertNotSame(brightness, clone.getBrightness());
        clone.getTags().add("tip");
        assertEquals(List.of("fang"), piece.getTags());
        assertSame(model, clone.getItemModel());
        assertEquals(3f, clone.getItemModelData());

        assertNull(new BlockDisplayModelPiece(block(), new Matrix4f()).clone().getBrightness());
    }

    @Test
    @DisplayName("a model scales and clones every piece")
    void modelScalesAndClonesEveryPiece() {
        DisplayModel model = DisplayModel.empty();
        assertTrue(model.getPieces().isEmpty());
        model.setPieces(new ArrayList<>(List.of(new BlockDisplayModelPiece(block(), new Matrix4f()), new BlockDisplayModelPiece(block(), new Matrix4f()))));

        assertSame(model, model.scale(2f));
        assertSame(model, model.scale(1f, 2f, 3f));
        for (BlockDisplayModelPiece piece : model.getPieces()) {
            assertEquals(new Vector3f(2f, 4f, 6f), piece.getTransform().getScale(new Vector3f()));
        }

        DisplayModel clone = model.clone();
        assertEquals(2, clone.getPieces().size());
        assertNotSame(model.getPieces().getFirst(), clone.getPieces().getFirst());
        clone.scale(10f);
        assertEquals(new Vector3f(2f, 4f, 6f), model.getPieces().getFirst().getTransform().getScale(new Vector3f()));
    }
}
