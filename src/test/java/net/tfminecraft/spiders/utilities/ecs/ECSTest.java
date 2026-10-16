package net.tfminecraft.spiders.utilities.ecs;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ECSTest {
    private static final class Thing implements ECSComponent {
    }

    private static List<LogRecord> capture(ECS app) {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        app.setLogger(logger);
        return records;
    }

    @Test
    @DisplayName("a failing system doesn't stop the others, and its repeats are muted")
    void aFailingSystemDoesNotStopTheOthers() {
        ECS app = new ECS();
        List<LogRecord> logged = capture(app);
        int[] ran = {0};
        app.onTick(ecs -> {
            throw new IllegalStateException("broken spider");
        });
        app.onTick(ecs -> ran[0]++);
        app.onRender(ecs -> ran[0]++);

        for (int tick = 0; tick < 3; tick++) {
            app.update();
            app.render();
        }

        assertEquals(6, ran[0]);
        assertEquals(1, logged.size());
    }

    @Test
    @DisplayName("sleeping entities are left out of the systems but still found outside them")
    void sleepingEntitiesAreLeftOutOfTheSystemsOnly() {
        ECS app = new ECS();
        app.spawn(new Thing());
        app.spawn(new Thing()).setAsleep(true);
        List<Integer> seen = new ArrayList<>();
        app.onTick(ecs -> seen.add(ecs.query(Thing.class).size()));
        app.onRender(ecs -> seen.add(ecs.query(ECSEntity.class, Thing.class).size()));

        app.update();
        app.render();

        assertEquals(List.of(1, 1), seen);
        assertEquals(2, app.query(Thing.class).size());
    }
}
