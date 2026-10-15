package net.tfminecraft.spiders.utilities;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.block.data.BlockData;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

public final class ParseModelFromCommand {
    private ParseModelFromCommand() {
    }


    @SuppressWarnings("unchecked")
    public static DisplayModel parseModelFromCommand(String command) {
//    /summon block_display ~-0.5 ~ ~-0.5 {Passengers:[{id:"minecraft:block_display",block_state:{Name:"minecraft:smooth_quartz",Properties:{}},transformation:[0.1f,0f,0f,0.15f,0f,0.0427f,0.0288f,0.4922f,0f,-0.0022f,0.5492f,-0.8771f,0f,0f,0f,1f]}...

        List<BlockDisplayModelPiece> pieces = new ArrayList<>();

        String json = command.substring("/summon block_display ~-0.5 ~ ~-0.5 ".length());

        // convert 1.0f -> 1.0
        json = json.replaceAll("(\\d*\\.*\\d+)f", "$1");

        Map<?, ?> parsed = new Gson().fromJson(json, Map.class);

        for (Map<?, ?> passenger : (List<Map<?, ?>>) parsed.get("Passengers")) {
            if (!(passenger.get("block_state") instanceof Map<?, ?> blockDisplay)) throw new IllegalArgumentException("Missing block_state");
            if (!(blockDisplay.get("Name") instanceof String blockName)) throw new IllegalArgumentException("Missing block_state.Name");
            Map<?, ?> blockProperties = blockDisplay.get("Properties") instanceof Map<?, ?> properties ? properties : new LinkedHashMap<>();

            // convert nbt form ({Name, Properties}) to the string form NAME[key=value,...]
            String blockString;
            if (blockProperties.isEmpty()) {
                blockString = blockName;
            } else {
                StringJoiner joined = new StringJoiner(",");
                for (Map.Entry<?, ?> entry : blockProperties.entrySet()) joined.add(entry.getKey() + "=" + entry.getValue());
                blockString = blockName + "[" + joined + "]";
            }

            BlockData blockData;
            try {
                blockData = Bukkit.createBlockData(blockString);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Invalid block_state " + blockString + ": " + e.getMessage());
            }

            if (!(passenger.get("transformation") instanceof List<?> transformation)) throw new IllegalArgumentException("Missing transformation");
            Matrix4f matrix = new Matrix4f(
                element(transformation, 0), element(transformation, 4), element(transformation, 8), element(transformation, 12),
                element(transformation, 1), element(transformation, 5), element(transformation, 9), element(transformation, 13),
                element(transformation, 2), element(transformation, 6), element(transformation, 10), element(transformation, 14),
                element(transformation, 3), element(transformation, 7), element(transformation, 11), element(transformation, 15)
            );


            pieces.add(new BlockDisplayModelPiece(
                blockData,
                matrix,
                null,
                passenger.get("Tags") instanceof List<?> tags ? (List<String>) tags : new ArrayList<String>()
            ));
        }

        return new DisplayModel(pieces);
    }

    private static float element(List<?> list, int index) {
        return ((Number) list.get(index)).floatValue();
    }
}
