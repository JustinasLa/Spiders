package net.tfminecraft.spiders.utilities.events;

/**
 * Runs {@link Scheduler}'s tick handlers by hand and clears them between tests. The first use starts
 * the tick timer through {@code Core}'s current plugin, so that plugin's server needs a scheduler.
 */
public final class SchedulerTestSupport {
    private SchedulerTestSupport() {
    }

    /** One server tick: every {@code onTick} handler, then every {@code onTickEnd} handler. */
    public static void tick() {
        Scheduler.TickSchedule.tick(null);
    }

    /** Forgets every tick handler, including ones other tests registered. */
    public static void clearTickHandlers() {
        Scheduler.TickSchedule.main.clear();
        Scheduler.TickSchedule.end.clear();
    }
}
