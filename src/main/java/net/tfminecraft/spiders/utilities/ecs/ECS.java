package net.tfminecraft.spiders.utilities.ecs;

import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Bevy-style Entity Component System
 */
public class ECS {
    private List<ECSEntity> entities = new ArrayList<>();
    private final List<Consumer<Object>> eventListeners = new ArrayList<>();

    private final List<Consumer<ECS>> startSystems = new ArrayList<>();
    private final List<Consumer<ECS>> tickSystems = new ArrayList<>();
    private final List<Consumer<ECS>> postTickSystems = new ArrayList<>();
    private final List<Consumer<ECS>> renderSystems = new ArrayList<>();

    public List<ECSEntity> getEntities() {
        return entities;
    }

    public void setEntities(List<ECSEntity> entities) {
        this.entities = entities;
    }

    public List<Consumer<Object>> getEventListeners() {
        return eventListeners;
    }

    public void onStart(Consumer<ECS> func) { startSystems.add(func); }
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
//        if (!inSystem) throw Error("Cannot query outside of a system")
        List<T> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            T comp = entity.query(type);
            if (comp != null) out.add(comp);
        }
        return out;
    }

    public <A extends ECSQueryable, B extends ECSQueryable> List<Pair<A, B>> query(Class<A> a, Class<B> b) {
//        if (!inSystem) throw Error("Cannot query outside of a system")
        List<Pair<A, B>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
            A comp1 = entity.query(a);
            if (comp1 == null) continue;
            B comp2 = entity.query(b);
            if (comp2 == null) continue;
            out.add(new Pair<>(comp1, comp2));
        }
        return out;
    }

    public <A extends ECSQueryable, B extends ECSQueryable, C extends ECSQueryable> List<Triple<A, B, C>> query(Class<A> a, Class<B> b, Class<C> c) {
//        if (!inSystem) throw Error("Cannot query outside of a system")
        List<Triple<A, B, C>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
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
//        if (!inSystem) throw Error("Cannot query outside of a system")
        List<Quadruple<A, B, C, D>> out = new ArrayList<>();
        for (ECSEntity entity : entities) {
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

    public void start() {
        for (Consumer<ECS> system : startSystems) system.accept(this);
    }

    public void update() {
        // Console removals must take effect before a damaged entity can tick again.
        entities.removeIf(ECSEntity::getScheduledForRemoval);
        for (Consumer<ECS> system : tickSystems) system.accept(this);
        for (Consumer<ECS> system : postTickSystems) system.accept(this);

        entities.removeIf(ECSEntity::getScheduledForRemoval);
    }

    public void render() {
        for (Consumer<ECS> system : renderSystems) system.accept(this);
    }
}
