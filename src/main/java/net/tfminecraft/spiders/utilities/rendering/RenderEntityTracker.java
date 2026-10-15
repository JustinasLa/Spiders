package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.custom_entities.CustomEntities;
import net.tfminecraft.spiders.utilities.custom_entities.CustomEntityComponent;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import org.bukkit.entity.Entity;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class RenderEntityTracker {
    private RenderEntityTracker() {
    }

    private static final Map<Object, Entity> rendered = new LinkedHashMap<>();
    private static final Set<Object> used = new LinkedHashSet<>();

    private static final CustomEntityComponent component = CustomEntityComponent.fromString("tfmc_spider_render");

    static {
        Scheduler.onTickEnd(it -> {
            removeUnused();
        });

        Core.onPluginShutdown(() -> {
            removeAll();
        });

        // remove dangling entities from previous crashes / failed shutdowns
        removeAllByTag();
    }

    public static void detach(Object id) {
        Entity entity = rendered.get(id);
        if (entity != null) CustomEntities.detach(entity, component);
        rendered.remove(id);
        used.remove(id);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Entity> T get(Object handle, Class<T> clazz) {
        Entity existing = rendered.get(handle);
        boolean isValid = existing != null && existing.getType().getEntityClass() == clazz && existing.isValid();
        if (!isValid) {
            remove(handle);
            return null;
        }
        used.add(handle);
        return (T) existing;
    }

    public static List<Pair<Object, Entity>> getAll() {
        List<Pair<Object, Entity>> out = new ArrayList<>(rendered.size());
        for (Map.Entry<Object, Entity> entry : rendered.entrySet()) out.add(new Pair<>(entry.getKey(), entry.getValue()));
        return out;
    }

    public static <T extends Entity> T put(Object handle, T entity) {
        rendered.putIfAbsent(handle, entity);
        CustomEntities.attach(entity, component);
        used.add(handle);
        return entity;
    }

    public static void remove(Object handle) {
        Entity entity = rendered.get(handle);
        if (entity == null) return;
        entity.remove();
        rendered.remove(handle);
    }

    private static void removeUnused() {
        Set<Object> toRemove = new LinkedHashSet<>(rendered.keySet());
        toRemove.removeAll(used);
        for (Object key : toRemove) {
            Entity entity = Objects.requireNonNull(rendered.get(key));
            entity.remove();
            rendered.remove(key);
        }
        used.clear();
    }

    public static void removeAll() {
        for (Entity entity : rendered.values()) {
            if (!component.isAttached(entity)) continue;
            entity.remove();
        }
        rendered.clear();
    }

    public static void removeAllByTag() {
        for (Entity entity : component.entities()) {
            entity.remove();
        }
    }
}
