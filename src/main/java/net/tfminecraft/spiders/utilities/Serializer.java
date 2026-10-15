package net.tfminecraft.spiders.utilities;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import net.kyori.adventure.key.Key;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKHingeJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKSwingTwistJoint3D;
import org.bukkit.Bukkit;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.joml.Matrix4f;

import java.lang.reflect.Type;
import java.util.Map;

public final class Serializer {
    private Serializer() {
    }

    private static final Gson gson = new GsonBuilder()
        .registerTypeHierarchyAdapter(BlockData.class, new BlockDataAdapter())
        .registerTypeHierarchyAdapter(Sound.class, new SoundAdapter())
        .registerTypeAdapter(IKJoint3D.class, new IKJointAdapter())
        .registerTypeAdapter(Matrix4f.class, new Matrix4fAdapter())
        .disableHtmlEscaping()
        .create();

    private static final ObjectPath path = new ObjectPath(gson);

    public static Gson getGson() {
        return gson;
    }

    public static ObjectPath getPath() {
        return path;
    }

    public static <T> String serialize(T value) {
        return gson.toJson(value);
    }

    public static <T> T deserialize(String json, Class<T> clazz) {
        return gson.fromJson(json, clazz);
    }

    @SuppressWarnings("unchecked")
    public static <T> T copyOf(T value) {
        return deserialize(serialize(value), (Class<T>) value.getClass());
    }

    public static Object toMap(Object obj) {
        return gson.fromJson(gson.toJson(obj), Object.class);
    }

    private static final class BlockDataAdapter implements JsonSerializer<BlockData>, JsonDeserializer<BlockData> {
        @Override
        public JsonElement serialize(BlockData src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(src.getAsString());
        }

        @Override
        public BlockData deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            return Bukkit.createBlockData(json.getAsString());
        }
    }

    private static final class Matrix4fAdapter implements JsonSerializer<Matrix4f>, JsonDeserializer<Matrix4f> {
        @Override
        public JsonElement serialize(Matrix4f src, Type type, JsonSerializationContext ctx) {
            JsonArray array = new JsonArray();
            for (float value : src.get(new float[16])) array.add(value);
            return array;
        }

        @Override
        public Matrix4f deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            JsonArray array = json.getAsJsonArray();
            float[] values = new float[16];
            for (int i = 0; i < 16; i++) values[i] = array.get(i).getAsFloat();
            return new Matrix4f().set(values);
        }
    }

    private static final class SoundAdapter implements JsonSerializer<Sound>, JsonDeserializer<Sound> {
        @Override
        public JsonElement serialize(Sound src, Type type, JsonSerializationContext ctx) {
            return new JsonPrimitive(String.valueOf(Registry.SOUNDS.getKey(src)));
        }

        @Override
        public Sound deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            return Registry.SOUNDS.getOrThrow(Key.key(json.getAsString()));
        }
    }

    private static final class IKJointAdapter implements JsonSerializer<IKJoint3D>, JsonDeserializer<IKJoint3D> {
        private static final String KEY = "joint";
        private static final String HINGE = "hinge";
        private static final String SWING_TWIST = "swingTwist";

        private static String jointTagOf(IKJoint3D joint) {
            if (joint instanceof IKHingeJoint3D) return HINGE;
            if (joint instanceof IKSwingTwistJoint3D) return SWING_TWIST;
            throw new IllegalStateException("Unknown IKJoint3D implementation " + joint.getClass().getName());
        }

        @Override
        public JsonElement serialize(IKJoint3D src, Type type, JsonSerializationContext ctx) {
            JsonElement body = ctx.serialize(src, src.getClass());
            if (!(body instanceof JsonObject bodyObject)) {
                throw new IllegalArgumentException("An IKJoint3D must serialise to a JSON object, got " + body);
            }

            JsonObject out = new JsonObject();
            out.addProperty(KEY, jointTagOf(src));
            for (Map.Entry<String, JsonElement> entry : bodyObject.entrySet()) out.add(entry.getKey(), entry.getValue());
            return out;
        }

        @Override
        public IKJoint3D deserialize(JsonElement json, Type type, JsonDeserializationContext ctx) {
            JsonObject body = json.getAsJsonObject();

            JsonElement tagElement = body.get(KEY);
            String tag = tagElement == null ? null : tagElement.getAsString();
            if (tag == null) throw new JsonParseException("IKJoint3D is missing its \"" + KEY + "\" discriminator");

            // Drop the tag so the implementation only sees its own fields
            JsonObject fields = new JsonObject();
            for (Map.Entry<String, JsonElement> entry : body.entrySet()) if (!entry.getKey().equals(KEY)) fields.add(entry.getKey(), entry.getValue());

            return switch (tag) {
                case HINGE -> ctx.deserialize(fields, IKHingeJoint3D.class);
                case SWING_TWIST -> ctx.deserialize(fields, IKSwingTwistJoint3D.class);
                default -> throw new JsonParseException("Unknown IKJoint3D type \"" + tag + "\"");
            };
        }
    }
}
