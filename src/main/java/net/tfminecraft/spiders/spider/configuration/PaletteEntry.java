package net.tfminecraft.spiders.spider.configuration;

import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;

public class PaletteEntry {
    private final BlockData block;
    private final Display.Brightness brightness;

    public PaletteEntry(BlockData block, Display.Brightness brightness) {
        this.block = block;
        this.brightness = brightness;
    }

    public BlockData getBlock() {
        return block;
    }

    public Display.Brightness getBrightness() {
        return brightness;
    }
}
