package net.tfminecraft.spiders.utilities.block_colors;

import net.tfminecraft.spiders.utilities.colors.Oklab;
import org.bukkit.Color;

import java.util.function.Function;

public enum FindBlockWithColor {
    RGB(false, color -> BlockColors.findBlockWithColor(color, false)),
    RGB_WITH_BRIGHTNESS(true, color -> BlockColors.findBlockWithColor(color, true)),
    OKLAB(false, color -> BlockColors.findBlockWithColor(Oklab.toOklab(color), false)),
    OKLAB_WITH_BRIGHTNESS(true, color -> BlockColors.findBlockWithColor(Oklab.toOklab(color), true));

    private final boolean customBrightness;
    private final Function<Color, BlockColorMatch> match;

    FindBlockWithColor(boolean customBrightness, Function<Color, BlockColorMatch> match) {
        this.customBrightness = customBrightness;
        this.match = match;
    }

    public boolean getCustomBrightness() {
        return customBrightness;
    }

    public BlockColorMatch match(Color color) {
        return match.apply(color);
    }
}
