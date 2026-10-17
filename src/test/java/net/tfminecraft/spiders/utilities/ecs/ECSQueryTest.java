package net.tfminecraft.spiders.utilities.ecs;

import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ECSQueryTest {
    private record A() implements ECSComponent {
    }

    private record B() implements ECSComponent {
    }

    private record C() implements ECSComponent {
    }

    private record D() implements ECSComponent {
    }

    private record E() implements ECSComponent {
    }

    @Test
    @DisplayName("multi-component queries only match entities that have every component")
    void queriesNeedEveryComponent() {
        ECS ecs = new ECS();
        A a = new A();
        B b = new B();
        C c = new C();
        D d = new D();
        E e = new E();
        ECSEntity full = ecs.spawn(a, b, c, d, e);
        // one entity missing each component in turn, so every query stops at every step
        ecs.spawn(new B(), new C(), new D(), new E());
        ecs.spawn(new A(), new C(), new D(), new E());
        ecs.spawn(new A(), new B(), new D(), new E());
        ecs.spawn(new A(), new B(), new C(), new E());
        ecs.spawn(new A(), new B(), new C(), new D());

        assertEquals(List.of(new Pair<>(a, b)), ecs.query(A.class, B.class).stream().filter(it -> it.first() == a).toList());
        assertEquals(5, ecs.query(C.class).size(), "one entity has no C");
        assertEquals(4, ecs.query(A.class, B.class).size());
        assertEquals(3, ecs.query(A.class, B.class, C.class).size());
        assertEquals(new Triple<>(a, b, c), ecs.query(A.class, B.class, C.class).getFirst());
        assertEquals(2, ecs.query(A.class, B.class, C.class, D.class).size());
        assertSame(d, ecs.query(A.class, B.class, C.class, D.class).getFirst().fourth());
        assertEquals(List.of(new Quintuple<>(a, b, c, d, e)), ecs.query(A.class, B.class, C.class, D.class, E.class));
        assertEquals(List.of(new Quintuple<>(full, a, b, c, d)), ecs.query(ECSEntity.class, A.class, B.class, C.class, D.class).subList(0, 1));
    }

    @Test
    @DisplayName("sleeping entities are skipped by every query shape while systems run")
    void sleepingEntitiesAreSkippedByEveryQuery() {
        ECS ecs = new ECS();
        ecs.spawn(new A(), new B(), new C(), new D(), new E()).setAsleep(true);
        List<Integer> sizes = new ArrayList<>();
        ecs.onPostTick(it -> {
            sizes.add(it.query(A.class, B.class).size());
            sizes.add(it.query(A.class, B.class, C.class).size());
            sizes.add(it.query(A.class, B.class, C.class, D.class).size());
            sizes.add(it.query(A.class, B.class, C.class, D.class, E.class).size());
        });
        ecs.update();
        assertEquals(List.of(0, 0, 0, 0), sizes);
        assertEquals(1, ecs.query(A.class, B.class, C.class, D.class, E.class).size());
    }

    @Test
    @DisplayName("events reach only the listeners for their type")
    void eventsReachListenersForTheirType() {
        ECS ecs = new ECS();
        List<String> strings = new ArrayList<>();
        List<Integer> numbers = new ArrayList<>();
        ecs.onEvent(String.class, strings::add);
        ecs.onEvent(Integer.class, numbers::add);
        ecs.emit("hello");
        ecs.emit(4);
        assertEquals(List.of("hello"), strings);
        assertEquals(List.of(4), numbers);
    }

    @Test
    @DisplayName("components can be removed, and removed entities leave on the next update")
    void componentsAndEntitiesCanBeRemoved() {
        ECS ecs = new ECS();
        ECSEntity entity = ecs.spawn(new A(), new B());
        entity.removeComponent(A.class);
        assertEquals(List.of(new B()), entity.getComponents());
        assertSame(entity, entity.query(ECSEntity.class));

        entity.remove();
        assertTrue(entity.getScheduledForRemoval());
        ecs.update();
        assertTrue(ecs.getEntities().isEmpty());
    }

    @Test
    @DisplayName("a failing system logs again once its minute of silence is over")
    void failingSystemsLogAgainAfterAMinute() throws ReflectiveOperationException {
        ECS ecs = new ECS();
        Logger logger = mock(Logger.class);
        ecs.setLogger(logger);
        RuntimeException failure = new IllegalStateException("broken");
        Consumer<ECS> system = it -> {
            throw failure;
        };
        ecs.onTick(system);

        ecs.update();
        ecs.update();
        verify(logger, times(1)).log(eq(Level.SEVERE), anyString(), eq(failure));

        Field field = ECS.class.getDeclaredField("lastErrorLog");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<Consumer<ECS>, Long> lastErrorLog = (Map<Consumer<ECS>, Long>) field.get(ecs);
        lastErrorLog.put(system, System.currentTimeMillis() - 61_000);
        ecs.update();
        verify(logger, times(2)).log(eq(Level.SEVERE), anyString(), any(Throwable.class));
    }
}
