package net.tfminecraft.spiders.utilities.events;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@link Scheduler}'s tick handlers by hand, and isolates a test from the handlers and error throttle other
 * tests left behind. The first use starts the tick timer through {@code Core}'s current plugin, so that plugin's
 * server needs a scheduler.
 */
public final class SchedulerTestSupport {
    private SchedulerTestSupport() {
    }

    /** One server tick: every {@code onTick} handler, then every {@code onTickEnd} handler. */
    public static void tick() {
        Scheduler.TickSchedule.tick(null);
    }

    /**
     * Empties the tick handler lists and lets the next failing handler log. Returns what puts the handlers and the
     * throttle back exactly as they were.
     */
    public static Runnable isolate() {
        List<Runnable> main = new ArrayList<>(Scheduler.TickSchedule.main);
        List<Runnable> end = new ArrayList<>(Scheduler.TickSchedule.end);
        long lastErrorLog = (long) read();
        Scheduler.TickSchedule.main.clear();
        Scheduler.TickSchedule.end.clear();
        write(Long.MIN_VALUE / 2);
        return () -> {
            Scheduler.TickSchedule.main.clear();
            Scheduler.TickSchedule.main.addAll(main);
            Scheduler.TickSchedule.end.clear();
            Scheduler.TickSchedule.end.addAll(end);
            write(lastErrorLog);
        };
    }

    private static Field lastErrorLog() throws NoSuchFieldException {
        Field field = Scheduler.TickSchedule.class.getDeclaredField("lastErrorLog");
        field.setAccessible(true);
        return field;
    }

    private static Object read() {
        try {
            return lastErrorLog().get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(long value) {
        try {
            lastErrorLog().set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
