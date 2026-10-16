package net.tfminecraft.spiders.utilities.ecs;

import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Bevy-style Entity Component System
 */
public class ECS {
    private final List<ECSEntity> entities = new ArrayList<>();
    private final List<Consumer<Object>> eventListeners = new ArrayList<>();

    private final List<Consumer<ECS>> tickSystems = new ArrayList<>();
    private final List<Consumer<ECS>> postTickSystems = new ArrayList<>();
    private final List<Consumer<ECS>> renderSystems = new ArrayList<>();

    /** True while systems run: their queries skip sleeping entities. */
    private boolean runningSystems = false;

    private Logger logger = Logger.getLogger(ECS.class.getName());
    /** When each failing system last logged its error, so one broken entity can't flood the console. */
    private final Map<Consumer<ECS>, Long> lastErrorLog = new HashMap<>();
    private static final long ERROR_LOG_INTERVAL_MS = 60_000;

    public List<ECSEntity> getEntities() {
        return entities;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public void onTick(Consumer<ECS> func) { tickSystems.add(func); }
    public void onPostTick(Consumer<ECS> func) { postTickSystems.add(func); }
    public void onRender(Consumer<ECS> func) { renderSystems.add(func); }

    public <T> void onEvent(Class<T> clazz, Consumer<T> listener) {
        eventListeners.add(event -> {
            if (clazz.isInstance(event)) listener.accept(clazz.cast(event));
        });
    }

    public void emit(Object message) {
        for (Consumer<Object> listener : eventListeners) listener.accept(message);
    }

    public ECSEntity spawn(ECSComponent... components) {
        ECSEntity entity = new ECSEntity();
        for (ECSComponent component : components) {
            entity.addComponent(component);
        }
        entities.add(entity);
        return entity;
    }

    public <T extends ECSQueryable> List<T> query(Class<T> type) {
        List<T> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            if (skips(entity)) continue;
            T comp = entity.query(type);
            if (comp != null) out.add(comp);
        }
        return out;
    }

    public <A extends ECSQueryable, B extends ECSQueryable> List<Pair<A, B>> query(Class<A> a, Class<B> b) {
        List<Pair<A, B>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            if (skips(entity)) continue;
            A comp1 = entity.query(a);
            if (comp1 == null) continue;
            B comp2 = entity.query(b);
            if (comp2 == null) continue;
            out.add(new Pair<>(comp1, comp2));
        }
        return out;
    }

    public <A extends ECSQueryable, B extends ECSQueryable, C extends ECSQueryable> List<Triple<A, B, C>> query(Class<A> a, Class<B> b, Class<C> c) {
        List<Triple<A, B, C>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            if (skips(entity)) continue;
            A comp1 = entity.query(a);
            if (comp1 == null) continue;
            B comp2 = entity.query(b);
            if (comp2 == null) continue;
            C comp3 = entity.query(c);
            if (comp3 == null) continue;
            out.add(new Triple<>(comp1, comp2, comp3));
        }
        return out;
    }

    public <A extends ECSQueryable, B extends ECSQueryable, C extends ECSQueryable, D extends ECSQueryable> List<Quadruple<A, B, C, D>> query(Class<A> a, Class<B> b, Class<C> c, Class<D> d) {
        List<Quadruple<A, B, C, D>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            if (skips(entity)) continue;
            A comp1 = entity.query(a);
            if (comp1 == null) continue;
            B comp2 = entity.query(b);
            if (comp2 == null) continue;
            C comp3 = entity.query(c);
            if (comp3 == null) continue;
            D comp4 = entity.query(d);
            if (comp4 == null) continue;
            out.add(new Quadruple<>(comp1, comp2, comp3, comp4));
        }
        return out;
    }

    public <A extends ECSQueryable, B extends ECSQueryable, C extends ECSQueryable, D extends ECSQueryable, E extends ECSQueryable> List<Quintuple<A, B, C, D, E>> query(Class<A> a, Class<B> b, Class<C> c, Class<D> d, Class<E> e) {
        List<Quintuple<A, B, C, D, E>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            if (skips(entity)) continue;
            A comp1 = entity.query(a);
            if (comp1 == null) continue;
            B comp2 = entity.query(b);
            if (comp2 == null) continue;
            C comp3 = entity.query(c);
            if (comp3 == null) continue;
            D comp4 = entity.query(d);
            if (comp4 == null) continue;
            E comp5 = entity.query(e);
            if (comp5 == null) continue;
            out.add(new Quintuple<>(comp1, comp2, comp3, comp4, comp5));
        }
        return out;
    }

    public void update() {
        // Console removals must take effect before a damaged entity can tick again.
        entities.removeIf(ECSEntity::getScheduledForRemoval);
        run(tickSystems);
        run(postTickSystems);

        entities.removeIf(ECSEntity::getScheduledForRemoval);
    }

    public void render() {
        run(renderSystems);
    }

    private boolean skips(ECSEntity entity) {
        return runningSystems && entity.isAsleep();
    }

    /** Runs each system on its own, so one that throws doesn't stop the others (or the display cleanup after them). */
    private void run(List<Consumer<ECS>> systems) {
        runningSystems = true;
        try {
            for (Consumer<ECS> system : new ArrayList<>(systems)) {
                try {
                    system.accept(this);
                } catch (RuntimeException e) {
                    reportError(system, e);
                }
            }
        } finally {
            runningSystems = false;
        }
    }

    private void reportError(Consumer<ECS> system, RuntimeException error) {
        long now = System.currentTimeMillis();
        Long last = lastErrorLog.get(system);
        if (last != null && now - last < ERROR_LOG_INTERVAL_MS) return;
        lastErrorLog.put(system, now);
        logger.log(Level.SEVERE, "A spider system failed; the others carried on. Repeats are muted for a minute.", error);
    }
}
