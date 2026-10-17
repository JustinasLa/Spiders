package net.tfminecraft.spiders.utilities.events;

import net.tfminecraft.spiders.utilities.Disposable;
import net.tfminecraft.spiders.utilities.UtilitiesTestSupport;
import org.bukkit.scheduler.BukkitTask;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SchedulerTest {
    private UtilitiesTestSupport support;

    @BeforeEach
    void setup() throws ReflectiveOperationException {
        support = UtilitiesTestSupport.install();
        SchedulerTestSupport.clearTickHandlers();
        // let the next failing handler log, whatever earlier tests did
        Field lastErrorLog = Scheduler.TickSchedule.class.getDeclaredField("lastErrorLog");
        lastErrorLog.setAccessible(true);
        lastErrorLog.set(null, Long.MIN_VALUE / 2);
    }

    @AfterEach
    void clear() {
        SchedulerTestSupport.clearTickHandlers();
    }

    @Test
    @DisplayName("runLater schedules the task once, and disposing cancels it")
    void runLaterSchedulesOnce() {
        Runnable task = () -> {
        };
        BukkitTask handle = mock(BukkitTask.class);
        when(support.scheduler.runTaskLater(support.plugin, task, 20L)).thenReturn(handle);

        Disposable disposable = Scheduler.runLater(20L, task);
        verify(handle, never()).cancel();
        disposable.close();
        verify(handle).cancel();
    }

    @Test
    @DisplayName("an interval passes the task a handle that cancels it")
    void intervalTasksCanCancelThemselves() {
        BukkitTask handle = mock(BukkitTask.class);
        ArgumentCaptor<Runnable> timer = ArgumentCaptor.forClass(Runnable.class);
        when(support.scheduler.runTaskTimer(eq(support.plugin), timer.capture(), eq(5L), eq(10L))).thenReturn(handle);
        List<Disposable> handed = new ArrayList<>();

        Disposable disposable = Scheduler.interval(5L, 10L, handed::add);
        timer.getValue().run();
        assertSame(disposable, handed.getFirst());
        handed.getFirst().close();
        verify(handle).cancel();
    }

    @Test
    @DisplayName("each tick runs the tick handlers, then the tick-end handlers, until they are disposed")
    void ticksRunHandlersInOrder() {
        List<String> ran = new ArrayList<>();
        Scheduler.onTickEnd(it -> ran.add("end"));
        Disposable main = Scheduler.onTick(it -> ran.add("main"));
        Scheduler.onTick(it -> {
            ran.add("once");
            it.close();
        });

        SchedulerTestSupport.tick();
        assertEquals(List.of("main", "once", "end"), ran);
        main.close();
        SchedulerTestSupport.tick();
        assertEquals(List.of("main", "once", "end", "end"), ran);
    }

    @Test
    @DisplayName("a failing tick handler is logged once a minute and the others carry on")
    void failingHandlersAreLoggedAndSkipped() {
        RuntimeException failure = new IllegalStateException("broken");
        int[] ran = {0};
        Scheduler.onTick(it -> {
            throw failure;
        });
        Scheduler.onTickEnd(it -> ran[0]++);

        SchedulerTestSupport.tick();
        SchedulerTestSupport.tick();

        assertEquals(2, ran[0]);
        verify(support.logger, times(1)).log(eq(Level.SEVERE), anyString(), eq(failure));
    }

    @Test
    @DisplayName("a series runs each task after the sleeps before it")
    void seriesRunsAfterTheSleeps() {
        SeriesScheduler series = new SeriesScheduler();
        Runnable first = () -> {
        };
        Runnable second = () -> {
        };
        series.run(first);
        series.sleep(5);
        series.sleep(3);
        series.run(second);

        assertEquals(8L, series.getTime());
        verify(support.scheduler).runTaskLater(support.plugin, first, 0L);
        verify(support.scheduler).runTaskLater(support.plugin, second, 8L);
    }
}
