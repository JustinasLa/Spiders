package net.tfminecraft.spiders.testing;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.BlockType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/**
 * Stands in for the server's registries in unit tests, found through {@code META-INF/services}, so
 * {@code Sound.ENTITY_SPIDER_AMBIENT} and the other registry constants can initialise without a server.
 * Every key resolves: enum registries to the matching constant, the others to a mock that knows its key.
 */
public final class TestRegistryAccess implements RegistryAccess {
    private final Map<Class<?>, Registry<?>> registries = new ConcurrentHashMap<>();

    @Override
    @SuppressWarnings({"unchecked", "deprecation"})
    public <T extends Keyed> Registry<T> getRegistry(Class<T> type) {
        return (Registry<T>) lookUp(registries, type, () -> registry(type));
    }

    /**
     * The value for {@code key}, made if missing. Making a mock can initialise its class, and a class
     * like {@code Sound} or {@code Registry} looks values up again while it initialises, so this must
     * not hold the map (as {@code computeIfAbsent} does) while it makes one. If the nested lookups
     * already stored this key, their value wins, so each key still has one instance.
     */
    static <K, V> V lookUp(Map<K, V> map, K key, Supplier<V> make) {
        V existing = map.get(key);
        if (existing != null) return existing;
        V made = make.get();
        if (made == null) return null;
        V raced = map.putIfAbsent(key, made);
        return raced != null ? raced : made;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends Keyed> Registry<T> getRegistry(RegistryKey<T> key) {
        return getRegistry((Class<T>) typeOf(key));
    }

    /** The element type a {@link RegistryKey} constant was declared with. */
    private static Class<?> typeOf(RegistryKey<?> key) {
        for (Field field : RegistryKey.class.getFields()) {
            if (!Modifier.isStatic(field.getModifiers())) continue;
            try {
                if (field.get(null) != key) continue;
            } catch (IllegalAccessException e) {
                continue;
            }
            Type argument = ((ParameterizedType) field.getGenericType()).getActualTypeArguments()[0];
            if (argument instanceof ParameterizedType parameterized) argument = parameterized.getRawType();
            return (Class<?>) argument;
        }
        return Keyed.class;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Registry<?> registry(Class<?> type) {
        Map<NamespacedKey, Object> entries = new ConcurrentHashMap<>();
        Registry registry = mock(Registry.class, withSettings().defaultAnswer(call -> switch (call.getMethod().getName()) {
            case "get", "getOrThrow" -> {
                Object argument = call.getArgument(0);
                if (!(argument instanceof Key key)) yield null;
                NamespacedKey namespaced = new NamespacedKey(key.namespace(), key.value());
                yield lookUp(entries, namespaced, () -> entry(type, namespaced));
            }
            case "getKey", "getKeyOrThrow" -> call.getArgument(0) instanceof Keyed keyed ? keyed.getKey() : null;
            case "stream" -> entries.values().stream();
            case "keyStream" -> entries.keySet().stream();
            case "iterator" -> entries.values().iterator();
            default -> null;
        }));
        return registry;
    }

    /** Bukkit casts some registry values to a nested {@code Typed} subtype (e.g. {@code BlockType.Typed}); mock that one. */
    private static Class<?> mockedType(Class<?> type) {
        for (Class<?> nested : type.getDeclaredClasses()) {
            if (nested.getSimpleName().equals("Typed") && type.isAssignableFrom(nested)) return nested;
        }
        return type;
    }

    /**
     * The nested subtypes Bukkit may cast a value to besides {@link #mockedType}, e.g. {@code DataComponentType.Valued}
     * and {@code NonValued}: {@code DataComponentTypes} checks each constant is the one it expects, so a mock is both.
     */
    private static Class<?>[] variants(Class<?> type) {
        return Arrays.stream(type.getDeclaredClasses()).filter(it -> it.isInterface() && type.isAssignableFrom(it)).toArray(Class<?>[]::new);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object entry(Class<?> type, NamespacedKey key) {
        if (type.isEnum()) {
            String name = key.getKey().toUpperCase(Locale.ROOT).replace('.', '_').replace('/', '_');
            try {
                return Enum.valueOf((Class) type, name);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        Class<?> mocked = mockedType(type);
        Class<?>[] variants = variants(mocked);
        Keyed entry = (Keyed) (variants.length == 0 ? mock(mocked) : mock(mocked, withSettings().extraInterfaces(variants)));
        when(entry.getKey()).thenReturn(key);
        when(entry.key()).thenReturn(key);
        // block colour tables only offer occluding blocks
        if (entry instanceof BlockType block) when(block.isOccluding()).thenReturn(true);
        return entry;
    }
}
