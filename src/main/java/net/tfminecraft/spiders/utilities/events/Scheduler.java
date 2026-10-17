package net.tfminecraft.spiders.utilities.events;

import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Disposable;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Level;

public final class Scheduler {
    private Scheduler() {
    }

    public static Disposable runLater(long delay, Runnable task) {
        JavaPlugin plugin = Core.getCurrentPlugin();
        BukkitTask handler = plugin.getServer().getScheduler().runTaskLater(plugin, task, delay);
        return () -> handler.cancel();
    }

    public static Disposable interval(long delay, long period, Consumer<Disposable> task) {
        JavaPlugin plugin = Core.getCurrentPlugin();
        BukkitTask[] handler = new BukkitTask[1];
        Disposable closeable = () -> handler[0].cancel();
        Runnable runnable = () -> task.accept(closeable);
        handler[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, runnable, delay, period);
        return closeable;
    }

    public static Disposable onTick(Consumer<Disposable> task) {
        return TickSchedule.schedule(TickSchedule.main, task);
    }

    public static Disposable onTickEnd(Consumer<Disposable> task) {
        return TickSchedule.schedule(TickSchedule.end, task);
    }




    /** Package-private so tests can run a tick and clear the handlers between tests. */
    static final class TickSchedule {
        private TickSchedule() {
        }

        static final List<Runnable> main = new ArrayList<>();
        static final List<Runnable> end = new ArrayList<>();

        static Disposable schedule(List<Runnable> list, Consumer<Disposable> task) {
            Disposable[] closeable = new Disposable[1];

            Runnable handler = () -> task.accept(closeable[0]);
            closeable[0] = () -> list.remove(handler);

            list.add(handler);

            return closeable[0];
        }


        static {
            interval(0, 1, TickSchedule::tick);
        }

        static void tick(Disposable timer) {
            runAll(main);
            runAll(end);
        }

        /** A throwing handler is logged and skipped; the rest of the tick (e.g. display cleanup) still runs. */
        private static void runAll(List<Runnable> handlers) {
            for (Runnable handler : new ArrayList<>(handlers)) {
                try {
                    handler.run();
                } catch (RuntimeException e) {
                    long now = System.currentTimeMillis();
                    if (now - lastErrorLog < ERROR_LOG_INTERVAL_MS) continue;
                    lastErrorLog = now;
                    Core.getCurrentPlugin().getLogger().log(Level.SEVERE, "A tick handler failed; the others carried on. Repeats are muted for a minute.", e);
                }
            }
        }

        private static long lastErrorLog = Long.MIN_VALUE / 2;
        private static final long ERROR_LOG_INTERVAL_MS = 60_000;
    }
}
