package net.tfminecraft.spiders.utilities;

import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

/**
 * One block of a model: the unit cube mapped by {@code transform}.
 * With an {@code itemModel} it is drawn as an item display of that resource-pack model instead, filling
 * the same cube; {@code itemModelData} is its first custom model data float. {@code block} stays as the fallback.
 */
public class BlockDisplayModelPiece {
    private BlockData block;
    private Matrix4f transform;
    private Display.Brightness brightness;
    private List<String> tags;
    private NamespacedKey itemModel;
    private float itemModelData;

    public BlockDisplayModelPiece(
        BlockData block,
        Matrix4f transform,
        Display.Brightness brightness,
        List<String> tags,
        NamespacedKey itemModel,
        float itemModelData
    ) {
        this.block = block;
        this.transform = transform;
        this.brightness = brightness;
        this.tags = tags;
        this.itemModel = itemModel;
        this.itemModelData = itemModelData;
    }

    public BlockDisplayModelPiece(BlockData block, Matrix4f transform, Display.Brightness brightness, List<String> tags, NamespacedKey itemModel) {
        this(block, transform, brightness, tags, itemModel, 0f);
    }

    public BlockDisplayModelPiece(BlockData block, Matrix4f transform, Display.Brightness brightness, List<String> tags) {
        this(block, transform, brightness, tags, null, 0f);
    }

    public BlockDisplayModelPiece(BlockData block, Matrix4f transform, Display.Brightness brightness) {
        this(block, transform, brightness, new ArrayList<>(), null, 0f);
    }

    public BlockDisplayModelPiece(BlockData block, Matrix4f transform) {
        this(block, transform, null, new ArrayList<>(), null, 0f);
    }

    public BlockData getBlock() {
        return block;
    }

    public void setBlock(BlockData block) {
        this.block = block;
    }

    public Matrix4f getTransform() {
        return transform;
    }

    public void setTransform(Matrix4f transform) {
        this.transform = transform;
    }

    public Display.Brightness getBrightness() {
        return brightness;
    }

    public void setBrightness(Display.Brightness brightness) {
        this.brightness = brightness;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public NamespacedKey getItemModel() {
        return itemModel;
    }

    public void setItemModel(NamespacedKey itemModel) {
        this.itemModel = itemModel;
    }

    public float getItemModelData() {
        return itemModelData;
    }

    public void setItemModelData(float itemModelData) {
        this.itemModelData = itemModelData;
    }

    public void scale(float scale) {
        transform.set(new Matrix4f().scale(scale).mul(transform));
    }

    public void scale(float x, float y, float z) {
        transform.set(new Matrix4f().scale(x, y, z).mul(transform));
    }

    public BlockDisplayModelPiece clone() {
        return new BlockDisplayModelPiece(
            block.clone(),
            new Matrix4f(transform),
            brightness == null ? null : new Display.Brightness(brightness.getBlockLight(), brightness.getSkyLight()),
            // tags are writable through /spider options, so a clone must not share them
            new ArrayList<>(tags),
            itemModel,
            itemModelData
        );
    }
}
