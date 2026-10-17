package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Disposable;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Just enough of a server for the mimic code, built on {@link AppTestSupport} (which puts back the current plugin,
 * the tick counter, the shutdown hooks, the scheduler's tick lists and the shared ECS): block data with chest
 * facings, a scheduler that keeps its tasks for the test to run, online players, and a logger that keeps warnings.
 * The mimics' own statics (lurkers and the lurk clock, haunted players, held chunk tickets, the cached tag key and
 * particle block data) start empty for each test and are restored exactly on {@link #close()}.
 */
final class MimicServer implements AutoCloseable {
    /** A task handed to the scheduler: run once after {@code delay}, or every {@code period} if a timer. */
    record Task(Runnable runnable, long delay, long period) {
    }

    private final AppTestSupport support = new AppTestSupport();
    final JavaPlugin plugin = support.plugin;
    final PluginManager plugins = support.manager;
    final List<Task> tasks = new ArrayList<>();
    final Map<UUID, Player> players = new LinkedHashMap<>();
    final List<String> warnings = new ArrayList<>();
    private final List<Disposable> hooksBefore = new ArrayList<>(shutdownHooks());
    private final Map<Field, Object> saved = new HashMap<>();

    MimicServer() {
        save(MimicHaunt.class, "haunted");
        save(MimicLurk.class, "lurkers");
        save(MimicLurk.class, "tick");
        save(nested("MimicChunkTickets"), "held");
        save(MimicChests.class, "key");
        save(Mimic.class, "blood");
        save(Mimic.class, "chest");
        // the lid and sway follow the server tick: start every test from tick 0
        save(Core.class, "currentTick");
        doAnswer(call -> warnings.add(call.getArgument(0))).when(support.logger).warning(anyString());

        when(support.scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
            tasks.add(new Task(call.getArgument(1), call.getArgument(2), -1));
            return mock(BukkitTask.class);
        });
        when(support.scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            tasks.add(new Task(call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            return mock(BukkitTask.class);
        });

        MockedStatic<Bukkit> bukkit = support.bukkit();
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class), any(Consumer.class))).thenAnswer(call -> {
            BlockData data = blockData(call.getArgument(0));
            call.<Consumer<BlockData>>getArgument(1).accept(data);
            return data;
        });
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> players.get(call.<UUID>getArgument(0)));
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> (Collection<?>) List.copyOf(players.values()));
    }

    /** Block data that remembers its material, and a facing for chests. */
    static BlockData blockData(Material material) {
        BlockData data;
        if (material == Material.CHEST) {
            org.bukkit.block.data.type.Chest chest = mock(org.bukkit.block.data.type.Chest.class);
            BlockFace[] facing = {BlockFace.NORTH};
            when(chest.getFacing()).thenAnswer(call -> facing[0]);
            doAnswer(call -> {
                facing[0] = call.getArgument(0);
                return null;
            }).when(chest).setFacing(any(BlockFace.class));
            data = chest;
        } else {
            data = mock(BlockData.class);
        }
        when(data.getMaterial()).thenReturn(material);
        return data;
    }

    /** A player who can be frightened, standing at {@code feet} and looking along {@code look}. */
    Player player(World world, Vector feet, Vector look) {
        Player player = mock(Player.class);
        UUID uuid = UUID.randomUUID();
        Location here = feet.toLocation(world).setDirection(look);
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getLocation()).thenAnswer(call -> here.clone());
        when(player.getEyeLocation()).thenAnswer(call -> here.clone().add(0, 1.62, 0));
        players.put(uuid, player);
        return player;
    }

    /** Move {@code player} (made by {@link #player}) to stand at {@code feet} looking along {@code look}. */
    static void move(Player player, Vector feet, Vector look) {
        Location there = feet.toLocation(player.getWorld()).setDirection(look);
        when(player.getLocation()).thenAnswer(call -> there.clone());
        when(player.getEyeLocation()).thenAnswer(call -> there.clone().add(0, 1.62, 0));
    }

    /** The timers scheduled so far, in order. */
    List<Runnable> timers() {
        return tasks.stream().filter(it -> it.period() >= 0).map(Task::runnable).toList();
    }

    /** Runs every one-off task scheduled so far (and any they schedule), soonest first. */
    void runLater() {
        while (true) {
            Task next = tasks.stream().filter(it -> it.period() < 0).min(Comparator.comparingLong(Task::delay)).orElse(null);
            if (next == null) return;
            tasks.remove(next);
            next.runnable().run();
        }
    }

    /** One-off tasks waiting to run. */
    List<Task> pending() {
        return tasks.stream().filter(it -> it.period() < 0).toList();
    }

    /**
     * The plugin stops: runs (and drops) the shutdown hooks registered during this test, as
     * {@link Core#shutdownCoreUtils} would, leaving the hooks other code registered before it alone.
     */
    void shutdown() {
        List<Disposable> added = new ArrayList<>(shutdownHooks());
        added.removeAll(hooksBefore);
        shutdownHooks().removeAll(added);
        for (Disposable hook : added) hook.close();
    }

    @Override
    public void close() {
        try {
            support.close();
        } finally {
            restore();
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Disposable> shutdownHooks() {
        try {
            Field field = Core.class.getDeclaredField("closeableList");
            field.setAccessible(true);
            return (List<Disposable>) field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Class<?> nested(String name) {
        for (Class<?> it : Mimic.class.getDeclaredClasses()) if (it.getSimpleName().equals(name)) return it;
        throw new IllegalStateException(name);
    }

    /** Remember a static's value (a copy, for maps) and start this test from empty. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void save(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map map) {
                saved.put(field, new LinkedHashMap<>(map));
                map.clear();
            } else {
                saved.put(field, value);
                field.set(null, field.getType() == int.class ? 0 : null);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void restore() {
        try {
            for (Map.Entry<Field, Object> entry : saved.entrySet()) {
                Field field = entry.getKey();
                if (entry.getValue() instanceof Map previous) {
                    Map map = (Map) field.get(null);
                    map.clear();
                    map.putAll(previous);
                } else {
                    field.set(null, entry.getValue());
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
