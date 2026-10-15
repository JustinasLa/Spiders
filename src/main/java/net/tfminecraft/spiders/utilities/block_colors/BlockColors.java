package net.tfminecraft.spiders.utilities.block_colors;

import net.tfminecraft.spiders.utilities.colors.Colors;
import net.tfminecraft.spiders.utilities.colors.Oklab;
import org.bukkit.Color;
import org.bukkit.block.data.BlockData;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

public final class BlockColors {
    private BlockColors() {
    }

    public static Color getBlockColor(BlockData block, int brightness) {
        Color color = getBlockColor(block);
        return color == null ? null : withBrightness(color, brightness);
    }

    public static Color getBlockColor(BlockData block) {
        return BlockColorMaps.getBlockToColor().get(block.getMaterial());
    }

    public static BlockColorMatch findBlockWithColor(Oklab color, boolean allowCustomBrightness) {
        List<BlockColorInfo> list = allowCustomBrightness ? BlockColorMaps.getColorToBlock() : fullBrightness(BlockColorMaps.getColorToBlock());

        if (list.isEmpty()) throw new NoSuchElementException();
        BlockColorInfo bestMatch = list.getFirst();
        float bestDistance = Oklab.distanceTo(bestMatch.getOklab(), color);
        for (int i = 1; i < list.size(); i++) {
            BlockColorInfo candidate = list.get(i);
            float distance = Oklab.distanceTo(candidate.getOklab(), color);
            if (Float.compare(bestDistance, distance) > 0) {
                bestMatch = candidate;
                bestDistance = distance;
            }
        }
        return new BlockColorMatch(
            bestMatch.getMaterial().createBlockData(),
            bestMatch.getBrightness()
        );
    }

    public static BlockColorMatch findBlockWithColor(Color color, boolean allowCustomBrightness) {
        List<BlockColorInfo> list = allowCustomBrightness ? BlockColorMaps.getColorToBlock() : fullBrightness(BlockColorMaps.getColorToBlock());

        if (list.isEmpty()) throw new NoSuchElementException();
        BlockColorInfo bestMatch = list.getFirst();
        float bestDistance = Colors.distanceTo(bestMatch.getRgb(), color);
        for (int i = 1; i < list.size(); i++) {
            BlockColorInfo candidate = list.get(i);
            float distance = Colors.distanceTo(candidate.getRgb(), color);
            if (Float.compare(bestDistance, distance) > 0) {
                bestMatch = candidate;
                bestDistance = distance;
            }
        }
        return new BlockColorMatch(
            bestMatch.getMaterial().createBlockData(),
            bestMatch.getBrightness()
        );
    }

    private static List<BlockColorInfo> fullBrightness(List<BlockColorInfo> colorToBlock) {
        List<BlockColorInfo> out = new ArrayList<>();
        for (BlockColorInfo info : colorToBlock) {
            if (info.getBrightness() == 15) out.add(info);
        }
        return out;
    }


    public static Color withBrightness(Color color, int brightness) {
        return Color.fromRGB(
            (int) (color.getRed() * (double) brightness / 15),
            (int) (color.getGreen() * (double) brightness / 15),
            (int) (color.getBlue() * (double) brightness / 15)
        );
    }
}
