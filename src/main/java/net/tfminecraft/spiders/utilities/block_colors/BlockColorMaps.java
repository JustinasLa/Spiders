package net.tfminecraft.spiders.utilities.block_colors;

import com.google.gson.Gson;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.colors.Oklab;
import org.bukkit.Color;
import org.bukkit.Material;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BlockColorMaps {
    private BlockColorMaps() {
    }

    private static final Map<Material, Color> blocks = loadBlocks();

    private static final List<BlockColorInfo> colorToBlock = buildColorToBlock();

    private static final Map<Material, Color> blockToColor = buildBlockToColor();

    public static List<BlockColorInfo> getColorToBlock() {
        return colorToBlock;
    }

    public static Map<Material, Color> getBlockToColor() {
        return blockToColor;
    }

    @SuppressWarnings("unchecked")
    private static Map<Material, Color> loadBlocks() {
        String text;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Core.requireResource("block_colors.json"), StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            char[] buffer = new char[8192];
            int read;
            while ((read = reader.read(buffer)) != -1) builder.append(buffer, 0, read);
            text = builder.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        Map<Material, Color> colorMap = new LinkedHashMap<>();

        Map<String, List<?>> json = (Map<String, List<?>>) new Gson().fromJson(text, Map.class);

        for (Map.Entry<String, List<?>> entry : json.entrySet()) {
            Material material = Material.matchMaterial("minecraft:" + entry.getKey());
            if (material == null) continue;
            List<?> value = entry.getValue();
            colorMap.put(material, Color.fromRGB(((Number) value.get(0)).intValue(), ((Number) value.get(1)).intValue(), ((Number) value.get(2)).intValue()));
        }

        // replace missing/incorrect colors
        colorMap.put(Material.GRASS_BLOCK, Objects.requireNonNull(colorMap.get(Material.MOSS_BLOCK)));
        colorMap.put(Material.OAK_LEAVES, BlockColors.withBrightness(Objects.requireNonNull(colorMap.get(Material.MOSS_BLOCK)), 10));
        colorMap.put(Material.BIRCH_LEAVES, BlockColors.withBrightness(Objects.requireNonNull(colorMap.get(Material.MOSS_BLOCK)), 8));
        colorMap.put(Material.SPRUCE_LEAVES, BlockColors.withBrightness(Objects.requireNonNull(colorMap.get(Material.MOSS_BLOCK)), 6));
        colorMap.put(Material.WARPED_TRAPDOOR, Objects.requireNonNull(colorMap.get(Material.WARPED_PLANKS)));
        colorMap.put(Material.BARREL, Objects.requireNonNull(colorMap.get(Material.SPRUCE_PLANKS)));
        colorMap.put(Material.RESPAWN_ANCHOR, Objects.requireNonNull(colorMap.get(Material.CRYING_OBSIDIAN)));
        // fluids are shroud targets only (not occluding, so never chosen as a display block)
        colorMap.put(Material.WATER, Color.fromRGB(63, 118, 228)); // default biome water tint
        colorMap.put(Material.LAVA, Color.fromRGB(207, 92, 20));

        // add wood from logs
        List<Pair<Material, Color>> snapshot = new ArrayList<>();
        for (Map.Entry<Material, Color> entry : colorMap.entrySet()) snapshot.add(new Pair<>(entry.getKey(), entry.getValue()));
        for (Pair<Material, Color> pair : snapshot) {
            Material woodMaterial = getWoodVariant(pair.first());
            if (woodMaterial != null) colorMap.put(woodMaterial, pair.second());
        }

        return new LinkedHashMap<>(colorMap);
    }

    private static List<BlockColorInfo> buildColorToBlock() {
        Map<Color, BlockColorInfo> out = new LinkedHashMap<>();

        for (int brightness = 15; brightness >= 0; brightness--) {
            for (Map.Entry<Material, Color> entry : blocks.entrySet()) {
                Material material = entry.getKey();
                Color color = entry.getValue();

                // skip non occluding blocks
                if (!material.isOccluding()) continue;

                // skip logs if there is a wood variant
                Material woodVariant = getWoodVariant(material);
                if (woodVariant != null && blocks.containsKey(woodVariant)) continue;

                // skip spawners
                if (material == Material.SPAWNER || material == Material.TRIAL_SPAWNER) continue;


                // skip shulker boxes
                String id = idString(material);
                if (id.endsWith("shulker_box")) continue;

                Color newColor = BlockColors.withBrightness(color, brightness);
                if (out.containsKey(newColor)) continue;

                out.put(newColor, new BlockColorInfo(
                    material,
                    brightness,
                    newColor,
                    Oklab.toOklab(newColor)
                ));
            }
        }

        return new ArrayList<>(out.values());
    }

    private static Map<Material, Color> buildBlockToColor() {
        Map<Material, Color> out = new LinkedHashMap<>(blocks);

        // infer color of partial blocks from their full block counterparts
        for (Map.Entry<Material, Color> entry : blocks.entrySet()) {
            Material material = entry.getKey();
            Color color = entry.getValue();
            String id = idString(material);

            List<String> variations = new ArrayList<>();
            for (String baseId : List.of(
                id,
                replaceEnd(id, "_planks", ""),
                replaceEnd(id, "_wood", ""),
                replaceEnd(id, "s", "")
            )) {
                variations.add(baseId + "_slab");
                variations.add(baseId + "_stairs");
                variations.add(baseId + "_wall");
                variations.add(baseId + "_trapdoor");
                variations.add("waxed_" + baseId);
            }

            for (String variantId : variations) {
                Material variantMaterial = Material.matchMaterial(variantId);
                if (variantMaterial == null) continue;
                if (out.containsKey(variantMaterial)) continue;
                out.put(variantMaterial, color);
//            println("Inferred: ${variantId.padEnd(40)} from $id")
            }
        }

        out.put(Material.CAMPFIRE, Objects.requireNonNull(out.get(Material.OAK_LOG)));

        return out;
    }

    private static String replaceEnd(String value, String suffix, String with) {
        if (value.endsWith(suffix)) {
            return value.substring(0, value.length() - suffix.length()) + with;
        } else {
            return value;
        }
    }

    private static Material getWoodVariant(Material material) {
        String id = idString(material);

        if (id.endsWith("_log")) {
            return Material.matchMaterial(replaceEnd(id, "_log", "_wood"));
        }

        if (id.endsWith("_stem")) {
            return Material.matchMaterial(replaceEnd(id, "_stem", "_hyphae"));
        }

        return null;
    }

    private static String idString(Material material) {
        return material.getKey().toString();
    }
}
