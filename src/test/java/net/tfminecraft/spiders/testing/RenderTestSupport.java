package net.tfminecraft.spiders.testing;

import io.papermc.paper.entity.TeleportFlag;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Disposable;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.rendering.RenderEntityTracker;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A server for the rendering code: a mocked plugin, scheduler and world whose {@code spawn} makes a stateful
 * mock entity (location, scoreboard tags, transformation, validity) and runs the spawn callback on it, plus
 * a static {@link Bukkit} mock for the online players and block data. Close it after each test.
 */
public final class RenderTestSupport implements AutoCloseable {
    public final JavaPlugin plugin = mock(JavaPlugin.class);
    public final Server server = mock(Server.class);
    public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    public final World world = mock(World.class);
    /** What the world holds; spawned entities join it, removed ones leave it. */
    public final List<Entity> worldEntities = new ArrayList<>();
    /** Everything {@link #world} spawned, in order. */
    public final List<Entity> spawned = new ArrayList<>();
    public final List<Player> online = new ArrayList<>();

    private final MockedStatic<Bukkit> bukkit;
    private final Map<Entity, Location> locations = new IdentityHashMap<>();

    @SuppressWarnings({"unchecked", "rawtypes"})
    public RenderTestSupport() {
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> mock(BukkitTask.class));
        when(server.getWorlds()).thenAnswer(call -> List.of(world));
        when(world.getEntities()).thenAnswer(call -> new ArrayList<>(worldEntities));
        when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class))).thenAnswer(call -> {
            Entity entity = entity(call.getArgument(1), call.getArgument(0));
            ((Consumer) call.getArgument(2)).accept(entity);
            spawned.add(entity);
            worldEntities.add(entity);
            return entity;
        });
        Core.setCurrentPlugin(plugin);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.copyOf(online));
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createBlockData(anyString())).thenAnswer(call -> blockData(Material.STONE));

        // load the tracker (and the tick schedule it registers with) against this server, whichever test comes first
        initialise(RenderEntityTracker.class);
        shutdownHooks = (List<Object>) staticField(Core.class, "closeableList");
        tickHooks = (List<Object>) staticField(TICK_SCHEDULE, "main");
        tickEndHooks = (List<Object>) staticField(TICK_SCHEDULE, "end");
        savedShutdownHooks = new ArrayList<>(shutdownHooks);
        savedTickHooks = new ArrayList<>(tickHooks);
        savedTickEndHooks = new ArrayList<>(tickEndHooks);
        resetTracker();
    }

    private static final Class<?> TICK_SCHEDULE = nested(Scheduler.class, "TickSchedule");

    /** The shared hook lists and their contents before this test, put back on close so nothing registered here leaks. */
    private final List<Object> shutdownHooks;
    private final List<Object> tickHooks;
    private final List<Object> tickEndHooks;
    private final List<Object> savedShutdownHooks;
    private final List<Object> savedTickHooks;
    private final List<Object> savedTickEndHooks;

    public MockedStatic<Bukkit> bukkit() {
        return bukkit;
    }

    @Override
    public void close() {
        restore(shutdownHooks, savedShutdownHooks);
        restore(tickHooks, savedTickHooks);
        restore(tickEndHooks, savedTickEndHooks);
        resetTracker();
        bukkit.close();
    }

    private static void restore(List<Object> list, List<Object> saved) {
        list.clear();
        list.addAll(saved);
    }

    private static void initialise(Class<?> type) {
        try {
            Class.forName(type.getName(), true, type.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The tick-end and shutdown hooks one call to {@code install} registered. */
    public record Hooks(Runnable tickEnd, Disposable shutdown) {
    }

    /**
     * Runs {@code install} and returns the one tick-end hook and the one shutdown hook it added, so a test
     * needn't rely on hooks registered when the class loaded still being there (a shutdown clears them).
     */
    public Hooks capture(Runnable install) {
        int tickEndBefore = tickEndHooks.size();
        int shutdownBefore = shutdownHooks.size();
        install.run();
        assertEquals(tickEndBefore + 1, tickEndHooks.size(), "one tick-end hook");
        assertEquals(shutdownBefore + 1, shutdownHooks.size(), "one shutdown hook");
        return new Hooks((Runnable) tickEndHooks.getLast(), (Disposable) shutdownHooks.getLast());
    }

    /** Block data that knows its material. */
    public static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    /** A mock entity of {@code type} at {@code at} that remembers where it is, its tags, transformation and validity. */
    public <T extends Entity> T entity(Class<T> type, Location at) {
        T entity = mock(type);
        locations.put(entity, at.clone());
        Set<String> tags = new HashSet<>();
        boolean[] valid = {true};

        when(entity.getType()).thenReturn(typeOf(type));
        when(entity.getLocation()).thenAnswer(call -> locations.get(entity).clone());
        when(entity.getWorld()).thenAnswer(call -> locations.get(entity).getWorld());
        when(entity.isValid()).thenAnswer(call -> valid[0]);
        when(entity.getScoreboardTags()).thenReturn(tags);
        when(entity.addScoreboardTag(anyString())).thenAnswer(call -> tags.add(call.getArgument(0)));
        when(entity.removeScoreboardTag(anyString())).thenAnswer(call -> tags.remove((String) call.getArgument(0)));
        when(entity.teleport(any(Location.class), any(TeleportFlag[].class))).thenAnswer(call -> {
            locations.put(entity, ((Location) call.getArgument(0)).clone());
            return true;
        });
        doAnswer(call -> {
            valid[0] = false;
            worldEntities.remove(entity);
            return null;
        }).when(entity).remove();

        if (entity instanceof Display display) {
            Transformation[] transformation = {decompose(new Matrix4f())};
            when(display.getTransformation()).thenAnswer(call -> transformation[0]);
            doAnswer(call -> transformation[0] = call.getArgument(0)).when(display).setTransformation(any());
            doAnswer(call -> transformation[0] = decompose(call.getArgument(0))).when(display).setTransformationMatrix(any());
        }
        return entity;
    }

    /** Moves an entity without going through the code under test. */
    public void place(Entity entity, Location location) {
        locations.put(entity, location.clone());
    }

    private static EntityType typeOf(Class<?> type) {
        if (type == BlockDisplay.class) return EntityType.BLOCK_DISPLAY;
        if (type == ItemDisplay.class) return EntityType.ITEM_DISPLAY;
        if (type == TextDisplay.class) return EntityType.TEXT_DISPLAY;
        return EntityType.UNKNOWN;
    }

    /** Roughly what the server keeps of a matrix: its translation, rotation and scale. */
    public static Transformation decompose(Matrix4f matrix) {
        return new Transformation(
            matrix.getTranslation(new Vector3f()),
            matrix.getNormalizedRotation(new Quaternionf()),
            matrix.getScale(new Vector3f()),
            new Quaternionf()
        );
    }

    /** The last block given to a display. */
    public static Material blockOf(BlockDisplay display) {
        ArgumentCaptor<BlockData> block = ArgumentCaptor.forClass(BlockData.class);
        verify(display, atLeastOnce()).setBlock(block.capture());
        return block.getValue().getMaterial();
    }

    /** The entities spawned of {@code type}, in order. */
    public <T extends Entity> List<T> spawned(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (Entity entity : spawned) if (type.isInstance(entity)) out.add(type.cast(entity));
        return out;
    }

    /** Forgets every tracked display, as a fresh server would. */
    public static void resetTracker() {
        ((Map<?, ?>) staticField(RenderEntityTracker.class, "rendered")).clear();
        ((Set<?>) staticField(RenderEntityTracker.class, "used")).clear();
    }

    private static Class<?> nested(Class<?> outer, String simpleName) {
        for (Class<?> inner : outer.getDeclaredClasses()) if (inner.getSimpleName().equals(simpleName)) return inner;
        throw new IllegalStateException(outer + " has no " + simpleName);
    }

    public static Object staticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
