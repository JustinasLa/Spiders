package net.tfminecraft.spiders.utilities.block_colors;

import org.bukkit.block.data.BlockData;

public class BlockColorMatch {
    private final BlockData block;
    private final int brightness;

    public BlockColorMatch(BlockData block, int brightness) {
        this.block = block;
        this.brightness = brightness;
    }

    public BlockData getBlock() {
        return block;
    }

    public int getBrightness() {
        return brightness;
    }
}
