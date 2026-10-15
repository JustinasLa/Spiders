package net.tfminecraft.spiders.utilities.block_colors;

import net.tfminecraft.spiders.utilities.colors.Oklab;
import org.bukkit.Color;
import org.bukkit.Material;

public class BlockColorInfo {
    private final Material material;
    private final int brightness;
    private final Color rgb;
    private final Oklab oklab;

    public BlockColorInfo(Material material, int brightness, Color rgb, Oklab oklab) {
        this.material = material;
        this.brightness = brightness;
        this.rgb = rgb;
        this.oklab = oklab;
    }

    public Material getMaterial() {
        return material;
    }

    public int getBrightness() {
        return brightness;
    }

    public Color getRgb() {
        return rgb;
    }

    public Oklab getOklab() {
        return oklab;
    }
}
