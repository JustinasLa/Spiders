package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.ParseModelFromCommand;
import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

public final class SpiderLegModel {
    private SpiderLegModel() {}

    private static final DisplayModel BASE = base(ParseModelFromCommand.parseModelFromCommand(
        """
            /summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,0.25f,-0.125f,0.1f,0f,0f,-0.125f,0f,1f,0f,0f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cauldron",Properties:{}},transformation:[-0.25f,0f,0f,0.125f,0f,0f,0.205f,-0.0625f,0f,1.1875f,0f,-0.1875f,0f,0f,0f,1f]}]}"""
    ));

    private static final DisplayModel FEMUR = femur(ParseModelFromCommand.parseModelFromCommand(
        """
            /summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,0.125f,-0.125f,-0.1f,0f,0f,0f,0f,-1f,0f,1f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:cauldron",Properties:{}},transformation:[-0.205f,0f,0f,0.1025f,0f,0f,0.125f,0f,0f,1f,0f,0f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,0.125f,0f,-0.1f,0f,0f,0f,0f,-1f,0f,1f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0f,0f,-0.25f,0.125f,0.1f,0f,0f,0.0125f,0f,-1f,0f,0.9981f,0f,0f,0f,1f]}]}"""
    ));

    private static final DisplayModel TIBIA = tibia(ParseModelFromCommand.parseModelFromCommand(
        """
            /summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,-0.248f,0f,0.124f,0.0739f,0f,-0.0739f,0.1494f,0.4722f,0f,0.4722f,-0.1541f,0f,0f,0f,1f],Tags:["cloak"]},{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.25f,-0.125f,-0.0012f,0.1185f,0f,0.0325f,-0.9457f,-0.0827f,0f,0.875f,0f,0f,0f,1f],Tags:["cloak"]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[-0.1425f,0f,0f,0.0625f,0f,0f,0.16f,-0.0925f,0f,0.375f,0f,0.625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_shulker_box",Properties:{}},transformation:[-0.08f,0f,0f,0.04f,0f,0f,0.08f,-0.155f,0f,0.5f,0f,0.25f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.0566f,0f,-0.0566f,-0.0119f,-0.0566f,0f,-0.0566f,0.0756f,0f,0.5f,0f,0f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.1425f,0f,0f,-0.0712f,0f,0f,0.16f,-0.1325f,0f,-0.3125f,0f,0.3125f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:anvil",Properties:{facing:"east"}},transformation:[0.0566f,0f,0.0566f,-0.0688f,-0.0566f,0f,0.0566f,0.0187f,0f,-0.3125f,0f,0.8125f,0f,0f,0f,1f]}]}"""
    ));

    private static final DisplayModel TIP = tip(ParseModelFromCommand.parseModelFromCommand(
        """
            /summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0f,0f,0.1875f,-0.0938f,-0.0008f,0.1185f,0f,0.0193f,-0.619f,-0.0827f,0f,0.5627f,0f,0f,0f,1f],Tags:["cloak"]},{id:"minecraft:block_display",block_state:{Name:"minecraft:gray_shulker_box",Properties:{}},transformation:[0f,0f,0.0813f,-0.0406f,0.0813f,0f,0f,-0.0744f,0f,0.9375f,0f,0.0625f,0f,0f,0f,1f]},{id:"minecraft:block_display",block_state:{Name:"minecraft:netherite_block",Properties:{}},transformation:[0f,0f,0.1705f,-0.085f,0f,-0.125f,0f,0.0625f,0.5f,0f,0f,0f,0f,0f,0f,1f]}]}"""
    ));

    public static DisplayModel getBASE() {
        return BASE;
    }

    public static DisplayModel getFEMUR() {
        return FEMUR;
    }

    public static DisplayModel getTIBIA() {
        return TIBIA;
    }

    public static DisplayModel getTIP() {
        return TIP;
    }

    private static DisplayModel base(DisplayModel model) {
        for (BlockDisplayModelPiece it : model.getPieces()) {
            addTag(it, "base");
            addTag(it, "leg");
        }
        return model;
    }

    private static DisplayModel femur(DisplayModel model) {
        for (BlockDisplayModelPiece it : model.getPieces()) {
            addTag(it, "femur");
            addTag(it, "leg");
        }
        return model;
    }

    private static DisplayModel tibia(DisplayModel model) {
        for (BlockDisplayModelPiece it : model.getPieces()) {
            addTag(it, "tibia");
            addTag(it, "leg");
            if (it.getBlock().getMaterial() == Material.SMOOTH_QUARTZ) addTag(it, "cloak");
            if (it.getTags().contains("cloak")) it.setBlock(Material.WHITE_CONCRETE.createBlockData());
        }
        return model;
    }

    private static DisplayModel tip(DisplayModel model) {
        for (BlockDisplayModelPiece it : model.getPieces()) {
            addTag(it, "tip");
            addTag(it, "leg");
            if (it.getBlock().getMaterial() == Material.SMOOTH_QUARTZ) addTag(it, "cloak");
            if (it.getTags().contains("cloak")) it.setBlock(Material.WHITE_CONCRETE.createBlockData());
        }
        return model;
    }

    private static void addTag(BlockDisplayModelPiece piece, String tag) {
        List<String> tags = new ArrayList<>(piece.getTags());
        tags.add(tag);
        piece.setTags(tags);
    }
}
