package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.PaletteEntry;
import net.tfminecraft.spiders.utilities.Pair;
import org.bukkit.Material;
import org.bukkit.entity.Display;

import java.util.ArrayList;
import java.util.List;

public enum AnimatedPalettes {
    CYAN_EYES(lit(
        Material.CYAN_SHULKER_BOX, Material.CYAN_SHULKER_BOX, Material.CYAN_SHULKER_BOX,
        Material.CYAN_CONCRETE,
        Material.CYAN_CONCRETE_POWDER,

        Material.LIGHT_BLUE_SHULKER_BOX,
        Material.LIGHT_BLUE_CONCRETE,
        Material.LIGHT_BLUE_CONCRETE_POWDER
    )),

    CYAN_BLINKING_LIGHTS(withBrightness(List.of(
        new Pair<>(Material.BLACK_SHULKER_BOX, new Display.Brightness(0, 15)),
        new Pair<>(Material.BLACK_SHULKER_BOX, new Display.Brightness(0, 15)),
        new Pair<>(Material.BLACK_SHULKER_BOX, new Display.Brightness(0, 15)),
        new Pair<>(Material.VERDANT_FROGLIGHT, new Display.Brightness(15, 15)),
        new Pair<>(Material.VERDANT_FROGLIGHT, new Display.Brightness(15, 15)),
        new Pair<>(Material.VERDANT_FROGLIGHT, new Display.Brightness(15, 15)),
        new Pair<>(Material.LIGHT_BLUE_SHULKER_BOX, new Display.Brightness(15, 15)),
        new Pair<>(Material.LIGHT_BLUE_CONCRETE, new Display.Brightness(15, 15)),
        new Pair<>(Material.LIGHT_BLUE_CONCRETE_POWDER, new Display.Brightness(15, 15))
    ))),


    RED_EYES(lit(
        Material.RED_SHULKER_BOX, Material.RED_SHULKER_BOX, Material.RED_SHULKER_BOX,
        Material.RED_CONCRETE,
        Material.RED_CONCRETE_POWDER,

        Material.FIRE_CORAL_BLOCK,
        Material.REDSTONE_BLOCK
    )),

    RED_BLINKING_LIGHTS(withBrightness(List.of(
        new Pair<>(Material.BLACK_SHULKER_BOX, new Display.Brightness(0, 15)),
        new Pair<>(Material.BLACK_SHULKER_BOX, new Display.Brightness(0, 15)),
        new Pair<>(Material.BLACK_SHULKER_BOX, new Display.Brightness(0, 15)),
        new Pair<>(Material.PEARLESCENT_FROGLIGHT, new Display.Brightness(15, 15)),
        new Pair<>(Material.PEARLESCENT_FROGLIGHT, new Display.Brightness(15, 15)),
        new Pair<>(Material.PEARLESCENT_FROGLIGHT, new Display.Brightness(15, 15)),
        new Pair<>(Material.RED_TERRACOTTA, new Display.Brightness(15, 15)),
        new Pair<>(Material.REDSTONE_BLOCK, new Display.Brightness(15, 15)),
        new Pair<>(Material.FIRE_CORAL_BLOCK, new Display.Brightness(15, 15))
    )));

    private final List<PaletteEntry> palette;

    AnimatedPalettes(List<PaletteEntry> palette) {
        this.palette = palette;
    }

    public List<PaletteEntry> getPalette() {
        return palette;
    }

    private static List<PaletteEntry> lit(Material... materials) {
        List<PaletteEntry> entries = new ArrayList<>();
        for (Material material : materials) {
            entries.add(new PaletteEntry(material.createBlockData(), new Display.Brightness(15, 15)));
        }
        return entries;
    }

    private static List<PaletteEntry> withBrightness(List<Pair<Material, Display.Brightness>> blocks) {
        List<PaletteEntry> entries = new ArrayList<>();
        for (Pair<Material, Display.Brightness> pair : blocks) {
            entries.add(new PaletteEntry(pair.first().createBlockData(), pair.second()));
        }
        return entries;
    }
}
