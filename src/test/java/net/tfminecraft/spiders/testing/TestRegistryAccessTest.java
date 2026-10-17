package net.tfminecraft.spiders.testing;

import io.papermc.paper.datacomponent.DataComponentType;
import io.papermc.paper.datacomponent.DataComponentTypes;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.block.BlockType;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class TestRegistryAccessTest {
    @Test
    @DisplayName("sound constants initialise in unit tests and keep their keys")
    void soundConstantsInitialise() {
        assertNotNull(Sound.BLOCK_WOOL_HIT);
        assertEquals("block.wool.hit", Sound.BLOCK_WOOL_HIT.getKey().getKey());
        assertSame(Sound.BLOCK_WOOL_HIT, Sound.BLOCK_WOOL_HIT);
    }

    /** Set before {@link Reentrant} initialises; its initialiser looks a value up through it, as {@code Sound} does. */
    static TestRegistryAccess access;

    interface Reentrant extends Keyed {
        Reentrant NESTED = access.getRegistry(Reentrant.class).get(NamespacedKey.minecraft("nested"));

        // a default method makes creating a mock initialise this interface
        default String describe() {
            return "reentrant";
        }
    }

    @Test
    @DisplayName("a lookup that initialises the element type, which looks values up itself, still works")
    void aLookupThatInitialisesTheTypeStillWorks() {
        access = new TestRegistryAccess();
        Registry<Reentrant> registry = access.getRegistry(Reentrant.class);

        Reentrant first = registry.get(NamespacedKey.minecraft("first"));

        assertNotNull(first);
        assertNotNull(Reentrant.NESTED);
        assertSame(Reentrant.NESTED, registry.get(NamespacedKey.minecraft("nested")));
        assertSame(first, registry.get(NamespacedKey.minecraft("first")));
    }

    @Test
    @DisplayName("nested lookups that store the same key first win, so each key keeps one instance")
    void nestedLookupsOfTheSameKeyKeepOneInstance() {
        Map<String, Object> map = new HashMap<>();
        Object inner = new Object();
        Object outer = TestRegistryAccess.lookUp(map, "key", () -> {
            map.put("key", inner);
            return new Object();
        });
        assertSame(inner, outer);
        assertSame(inner, TestRegistryAccess.lookUp(map, "key", Object::new));
    }

    @Test
    @DisplayName("block types and data component types initialise as their typed and valued subtypes")
    void typedRegistryEntriesInitialise() {
        assertInstanceOf(BlockType.Typed.class, Material.STONE.asBlockType());
        assertInstanceOf(DataComponentType.Valued.class, DataComponentTypes.ITEM_NAME);
        assertEquals("item_name", DataComponentTypes.ITEM_NAME.getKey().getKey());
    }
}
