package net.tfminecraft.spiders.utilities.persistence;

import org.bukkit.persistence.PersistentDataAdapterContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class UUIDDataTypeTest {
    @Test
    @DisplayName("a UUID is stored as its 16 big-endian bytes and read back unchanged")
    void uuidsRoundTripThroughSixteenBytes() {
        UUIDDataType type = UUIDDataType.INSTANCE;
        PersistentDataAdapterContext context = mock(PersistentDataAdapterContext.class);
        UUID uuid = new UUID(0x0102030405060708L, 0x090A0B0C0D0E0F10L);

        byte[] bytes = type.toPrimitive(uuid, context);
        assertArrayEquals(new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, bytes);
        assertEquals(uuid, type.fromPrimitive(bytes, context));
        UUID random = UUID.randomUUID();
        assertEquals(random, type.fromPrimitive(type.toPrimitive(random, context), context));
        assertEquals(byte[].class, type.getPrimitiveType());
        assertEquals(UUID.class, type.getComplexType());
    }
}
