package net.tfminecraft.spiders.testing;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import org.bukkit.Keyed;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
        return (Registry<T>) registries.computeIfAbsent(type, TestRegistryAccess::registry);
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
                yield entries.computeIfAbsent(namespaced, it -> entry(type, it));
            }
            case "getKey", "getKeyOrThrow" -> call.getArgument(0) instanceof Keyed keyed ? keyed.getKey() : null;
            case "stream" -> entries.values().stream();
            case "keyStream" -> entries.keySet().stream();
            case "iterator" -> entries.values().iterator();
            default -> null;
        }));
        return registry;
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
        Keyed entry = (Keyed) mock(type);
        when(entry.getKey()).thenReturn(key);
        when(entry.key()).thenReturn(key);
        return entry;
    }
}
