package net.tfminecraft.spiders.utilities;

import com.google.gson.JsonParseException;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKHingeJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKSwingTwistJoint3D;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class SerializerTest {
    static class Holder {
        BlockData block;
        Sound sound;
        Matrix4f matrix;
        IKJoint3D joint;
        int count = 2;
    }

    @Test
    @DisplayName("block data is stored as its string form and read back through the server")
    void blockDataRoundTrips() {
        BlockData stone = mock(BlockData.class);
        when(stone.getAsString()).thenReturn("minecraft:stone");
        Holder holder = new Holder();
        holder.block = stone;

        String json = Serializer.serialize(holder);
        assertTrue(json.contains("\"block\":\"minecraft:stone\""), json);

        BlockData parsed = mock(BlockData.class);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(() -> Bukkit.createBlockData("minecraft:stone")).thenReturn(parsed);
            assertSame(parsed, Serializer.deserialize(json, Holder.class).block);
        }
    }

    @Test
    @DisplayName("sounds are stored by registry key")
    void soundsRoundTrip() {
        Holder holder = new Holder();
        holder.sound = Sound.ENTITY_ITEM_PICKUP;

        String json = Serializer.serialize(holder);
        assertTrue(json.contains("\"sound\":\"minecraft:entity.item.pickup\""), json);
        assertSame(Sound.ENTITY_ITEM_PICKUP, Serializer.deserialize(json, Holder.class).sound);
    }

    @Test
    @DisplayName("matrices are stored as their 16 values")
    void matricesRoundTrip() {
        Holder holder = new Holder();
        holder.matrix = new Matrix4f().translate(1f, 2f, 3f).scale(2f);

        String json = Serializer.serialize(holder);
        assertTrue(json.contains("\"matrix\":[2.0,0.0,0.0,0.0,0.0,2.0,0.0,0.0,0.0,0.0,2.0,0.0,1.0,2.0,3.0,1.0]"), json);
        assertEquals(holder.matrix, Serializer.deserialize(json, Holder.class).matrix);
    }

    @Test
    @DisplayName("joints carry a tag naming their type, and come back as that type")
    void jointsRoundTripWithTheirTag() {
        Holder holder = new Holder();
        holder.joint = new IKHingeJoint3D(-1.0, 1.0, new Vector(0, 1, 0), new Vector(1, 0, 0));
        String json = Serializer.serialize(holder);
        assertTrue(json.contains("\"joint\":{\"joint\":\"hinge\",\"minAngle\":-1.0"), json);
        IKHingeJoint3D hinge = assertInstanceOf(IKHingeJoint3D.class, Serializer.deserialize(json, Holder.class).joint);
        assertEquals(1.0, hinge.getMaxAngle());
        assertEquals(new Vector(0, 1, 0), hinge.getAxis());

        holder.joint = new IKSwingTwistJoint3D(0.5, -0.25, 0.25, new Vector(0, 0, 1));
        json = Serializer.serialize(holder);
        assertTrue(json.contains("\"joint\":{\"joint\":\"swingTwist\""), json);
        IKSwingTwistJoint3D swingTwist = assertInstanceOf(IKSwingTwistJoint3D.class, Serializer.deserialize(json, Holder.class).joint);
        assertEquals(0.5, swingTwist.getMaxSwing());
        assertEquals(new Vector(0, 0, 1), swingTwist.getReferenceAxis());
    }

    @Test
    @DisplayName("a joint without a known tag is rejected")
    void untaggedJointsAreRejected() {
        assertThrows(JsonParseException.class, () -> Serializer.deserialize("{\"joint\":{\"maxSwing\":1.0}}", Holder.class));
        assertThrows(JsonParseException.class, () -> Serializer.deserialize("{\"joint\":{\"joint\":\"ball\"}}", Holder.class));
    }

    @Test
    @DisplayName("copies are deep and toMap gives plain JSON values")
    void copiesAreDeep() {
        Holder holder = new Holder();
        holder.matrix = new Matrix4f().scale(3f);
        Holder copy = Serializer.copyOf(holder);
        assertNotSame(holder.matrix, copy.matrix);
        assertEquals(holder.matrix, copy.matrix);
        assertEquals(2, copy.count);

        @SuppressWarnings("unchecked")
        Map<String, Object> map = (Map<String, Object>) Serializer.toMap(holder);
        assertEquals(2.0, map.get("count"));
        assertEquals(16, ((List<?>) map.get("matrix")).size());
    }

    @Test
    @DisplayName("the shared path helper uses the same JSON rules")
    void thePathHelperUsesTheSameGson() {
        Holder holder = new Holder();
        assertTrue(Serializer.getPath().set(holder, "matrix", List.of(1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 4, 5, 6, 1)));
        assertEquals(new Matrix4f().translate(4f, 5f, 6f), holder.matrix);
        assertSame(Serializer.getGson(), Serializer.getGson());
    }
}
