package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.Core;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link AppTestSupport} (a plugin, server and Bukkit statics, all static state put back on {@link #close()}) with a
 * scheduler the test steps by hand through {@link #advance}.
 */
final class ComponentServer implements AutoCloseable {
    private final AppTestSupport support = new AppTestSupport();
    final JavaPlugin plugin = support.plugin;
    final PluginManager pluginManager = support.manager;
    private final List<Task> tasks = new ArrayList<>();
    private long now = 0;
    private long order = 0;

    private static final class Task {
        final Runnable runnable;
        final long period;
        final long order;
        long due;
        boolean cancelled = false;

        Task(Runnable runnable, long due, long period, long order) {
            this.runnable = runnable;
            this.due = due;
            this.period = period;
            this.order = order;
        }
    }

    ComponentServer() {
        when(support.scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong()))
            .thenAnswer(call -> schedule(call.getArgument(1), call.<Long>getArgument(2), 0L));
        when(support.scheduler.runTaskTimer(eq(plugin), any(Runnable.class), anyLong(), anyLong()))
            .thenAnswer(call -> schedule(call.getArgument(1), call.<Long>getArgument(2), Math.max(1L, call.<Long>getArgument(3))));
    }

    private BukkitTask schedule(Runnable runnable, long delay, long period) {
        Task task = new Task(runnable, now + Math.max(1L, delay), period, order++);
        tasks.add(task);
        BukkitTask handle = mock(BukkitTask.class);
        doAnswer(call -> {
            task.cancelled = true;
            return null;
        }).when(handle).cancel();
        return handle;
    }

    /** Runs {@code ticks} server ticks: every task that comes due, in order, repeating timers as they go. */
    void advance(long ticks) {
        for (long tick = 0; tick < ticks; tick++) {
            now++;
            while (true) {
                Task next = tasks.stream()
                    .filter(it -> !it.cancelled && it.due <= now)
                    .min(Comparator.<Task>comparingLong(it -> it.due).thenComparingLong(it -> it.order))
                    .orElse(null);
                if (next == null) break;
                if (next.period > 0) next.due = now + next.period;
                else next.cancelled = true;
                next.runnable.run();
            }
            tasks.removeIf(it -> it.cancelled);
        }
    }

    /** Tasks still waiting to run. */
    int pending() {
        return (int) tasks.stream().filter(it -> !it.cancelled).count();
    }

    /** Sets the plugin's tick counter, which only a running server would advance. Callers put the old value back. */
    static void setTick(int tick) {
        try {
            Field field = Core.class.getDeclaredField("currentTick");
            field.setAccessible(true);
            field.setInt(null, tick);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    static BlockData blockData(Material material) {
        return AppTestSupport.blockData(material);
    }

    @Override
    public void close() {
        support.close();
    }
}
