package net.tfminecraft.spiders.utilities;

import net.tfminecraft.spiders.utilities.events.SchedulerTestSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CoreTest {
    private Object savedPlugin;
    private Object savedTick;
    private Object savedMinecart;
    private List<Disposable> savedHooks;

    private static Field field(String name) throws ReflectiveOperationException {
        Field field = Core.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    @SuppressWarnings("unchecked")
    private static List<Disposable> hooks() throws ReflectiveOperationException {
        return (List<Disposable>) field("closeableList").get(null);
    }

    @BeforeEach
    void save() throws ReflectiveOperationException {
        savedPlugin = field("currentPlugin").get(null);
        savedTick = field("currentTick").get(null);
        savedMinecart = field("commandBlockMinecart").get(null);
        savedHooks = new ArrayList<>(hooks());
        hooks().clear();
        field("commandBlockMinecart").set(null, null);
    }

    @AfterEach
    void restore() throws ReflectiveOperationException {
        field("currentPlugin").set(null, savedPlugin);
        field("currentTick").set(null, savedTick);
        field("commandBlockMinecart").set(null, savedMinecart);
        hooks().clear();
        hooks().addAll(savedHooks);
    }

    @Test
    @DisplayName("the current plugin must be set before it is used")
    void currentPluginMustBeSet() {
        Core.setCurrentPlugin(null);
        assertThrows(IllegalStateException.class, Core::getCurrentPlugin);
        UtilitiesTestSupport support = UtilitiesTestSupport.install();
        assertSame(support.plugin, Core.getCurrentPlugin());
        assertEquals(new NamespacedKey("tfmcspiders", "spider_uuid"), Core.namespacedID("spider_uuid"));
    }

    @Test
    @DisplayName("setup sets the plugin and counts ticks")
    void setupCountsTicks() throws ReflectiveOperationException {
        UtilitiesTestSupport support = UtilitiesTestSupport.install();
        SchedulerTestSupport.clearTickHandlers();
        Core.setCurrentPlugin(null);
        field("currentTick").set(null, 0);

        try {
            Core.setupCoreUtils(support.plugin);
            assertSame(support.plugin, Core.getCurrentPlugin());
            SchedulerTestSupport.tick();
            SchedulerTestSupport.tick();
            assertEquals(2, Core.getCurrentTick());
        } finally {
            SchedulerTestSupport.clearTickHandlers();
        }
    }

    @Test
    @DisplayName("every shutdown hook runs once, even after one throws")
    void shutdownRunsEveryHookOnce() {
        UtilitiesTestSupport support = UtilitiesTestSupport.install();
        List<String> ran = new ArrayList<>();
        RuntimeException failure = new IllegalStateException("broken");
        assertTrue(Core.onPluginShutdown(() -> ran.add("first")));
        Core.onPluginShutdown(() -> {
            throw failure;
        });
        Core.onPluginShutdown(() -> ran.add("third"));

        Core.shutdownCoreUtils(support.plugin);
        assertEquals(List.of("first", "third"), ran);
        verify(support.logger).log(eq(Level.SEVERE), anyString(), eq(failure));

        Core.shutdownCoreUtils(support.plugin);
        assertEquals(List.of("first", "third"), ran, "the hooks were cleared");
    }

    @Test
    @DisplayName("resources come from the plugin jar")
    void resourcesComeFromTheJar() throws IOException {
        try (InputStream stream = Core.requireResource("block_colors.json")) {
            assertTrue(stream.read() >= 0);
        }
        assertThrows(IllegalStateException.class, () -> Core.requireResource("missing.json"));
    }

    @Test
    @DisplayName("silent commands run as one hidden command minecart, made at the first world's spawn")
    void silentCommandsReuseOneMinecart() {
        World world = mock(World.class);
        Location spawn = new Location(world, 1, 2, 3);
        when(world.getSpawnLocation()).thenReturn(spawn);
        CommandMinecart minecart = mock(CommandMinecart.class);
        when(world.spawn(eq(spawn), eq(CommandMinecart.class), any(Consumer.class))).thenAnswer(call -> {
            Consumer<CommandMinecart> init = call.getArgument(2);
            init.accept(minecart);
            return minecart;
        });
        org.bukkit.Server server = mock(org.bukkit.Server.class);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
            bukkit.when(Bukkit::getServer).thenReturn(server);

            Core.runCommandSilently("say one");
            verify(minecart).remove();
            verify(server).dispatchCommand(minecart, "say one");

            World other = mock(World.class);
            when(other.getSpawnLocation()).thenReturn(new Location(other, 0, 64, 0));
            Core.runCommandSilently("say two", other);
            Core.runCommandSilently("say three", other, new Vector(5, 6, 7));
            verify(server).dispatchCommand(minecart, "say two");
            verify(server).dispatchCommand(minecart, "say three");
            verify(world, times(1)).spawn(any(Location.class), eq(CommandMinecart.class), any(Consumer.class));
            verify(other, never()).spawn(any(Location.class), eq(CommandMinecart.class), any(Consumer.class));
        }
    }
}
