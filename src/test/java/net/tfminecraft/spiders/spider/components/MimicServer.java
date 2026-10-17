package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.utilities.Core;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Just enough of a server for the mimic code: block data, a scheduler that keeps its tasks for the test
 * to run, online players, and a plugin. Closing it also wipes the mimics' static state, so no test sees
 * another's lurkers, haunted players, chunk tickets or spiders.
 */
final class MimicServer implements AutoCloseable {
    /** A task handed to the scheduler: run once after {@code delay}, or every {@code period} if a timer. */
    record Task(Runnable runnable, long delay, long period) {
    }

    final JavaPlugin plugin = mock(JavaPlugin.class);
    final Server server = mock(Server.class);
    final PluginManager plugins = mock(PluginManager.class);
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final List<Task> tasks = new ArrayList<>();
    final Map<UUID, Player> players = new LinkedHashMap<>();
    final List<String> warnings = new ArrayList<>();
    private final MockedStatic<Bukkit> bukkit;

    MimicServer() {
        resetStatics();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                warnings.add(record.getMessage());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        when(plugin.getName()).thenReturn("TFMCSpiders");
        when(plugin.namespace()).thenReturn("tfmcspiders");
        when(plugin.getLogger()).thenReturn(logger);
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(plugins);

        when(scheduler.runTaskLater(any(Plugin.class), any(Runnable.class), anyLong())).thenAnswer(call -> {
            tasks.add(new Task(call.getArgument(1), call.getArgument(2), -1));
            return mock(BukkitTask.class);
        });
        when(scheduler.runTaskTimer(any(Plugin.class), any(Runnable.class), anyLong(), anyLong())).thenAnswer(call -> {
            tasks.add(new Task(call.getArgument(1), call.getArgument(2), call.getArgument(3)));
            return mock(BukkitTask.class);
        });

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class), any(Consumer.class))).thenAnswer(call -> {
            BlockData data = blockData(call.getArgument(0));
            call.<Consumer<BlockData>>getArgument(1).accept(data);
            return data;
        });
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> players.get(call.<UUID>getArgument(0)));
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> (Collection<?>) List.copyOf(players.values()));
        Core.setCurrentPlugin(plugin);
    }

    /** Block data that remembers its material, and a facing for chests. */
    static BlockData blockData(Material material) {
        BlockData data;
        if (material == Material.CHEST) {
            org.bukkit.block.data.type.Chest chest = mock(org.bukkit.block.data.type.Chest.class);
            BlockFace[] facing = {BlockFace.NORTH};
            when(chest.getFacing()).thenAnswer(call -> facing[0]);
            org.mockito.Mockito.doAnswer(call -> {
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
        Location[] here = {feet.toLocation(world).setDirection(look)};
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(player.getHealth()).thenReturn(20.0);
        when(player.getLocation()).thenAnswer(call -> here[0].clone());
        when(player.getEyeLocation()).thenAnswer(call -> here[0].clone().add(0, 1.62, 0));
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

    @Override
    public void close() {
        bukkit.close();
        Core.setCurrentPlugin(null);
        resetStatics();
    }

    /** Lurkers, haunted players, held chunk tickets, live spiders and shutdown hooks all live in statics. */
    static void resetStatics() {
        ((Map<?, ?>) read(MimicHaunt.class, "haunted")).clear();
        ((Map<?, ?>) read(MimicLurk.class, "lurkers")).clear();
        write(MimicLurk.class, "tick", 0);
        ((Map<?, ?>) read(nested("MimicChunkTickets"), "held")).clear();
        ((List<?>) read(Core.class, "closeableList")).clear();
        AppState.getEcs().getEntities().clear();
    }

    private static Class<?> nested(String name) {
        for (Class<?> it : Mimic.class.getDeclaredClasses()) if (it.getSimpleName().equals(name)) return it;
        throw new IllegalStateException(name);
    }

    private static Object read(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Class<?> owner, String name, Object value) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
