package net.tfminecraft.spiders.utilities.rendering;

import org.bukkit.Location;
import org.bukkit.entity.Entity;

import java.util.function.Consumer;

public class RenderEntity<T extends Entity> implements RenderItem {
    private final Class<T> clazz;
    private final Location location;
    private final Consumer<T> init;
    private final Consumer<T> update;

    public RenderEntity(Class<T> clazz, Location location, Consumer<T> init, Consumer<T> update) {
        this.clazz = clazz;
        this.location = location;
        this.init = init;
        this.update = update;
    }

    public RenderEntity(Class<T> clazz, Location location, Consumer<T> init) {
        this(clazz, location, init, it -> {});
    }

    public RenderEntity(Class<T> clazz, Location location) {
        this(clazz, location, it -> {}, it -> {});
    }

    public Class<T> getClazz() {
        return clazz;
    }

    public Location getLocation() {
        return location;
    }

    public Consumer<T> getInit() {
        return init;
    }

    public Consumer<T> getUpdate() {
        return update;
    }

    @Override
    public void submit(Object handle) {
        if (!location.isFinite()) {
            System.err.println("Tried to submit a RenderEntity with non-finite location. Ignored.");
            return;
        }

        T entity = RenderEntityTracker.get(handle, clazz);

        if (entity != null) {
            if (!entity.isInsideVehicle()) entity.teleport(location);
            update.accept(entity);
        } else {
            Consumer<T> initializer = it -> {
                it.setPersistent(false);
                init.accept(it);
                update.accept(it);
            };
            RenderEntityTracker.put(handle, location.getWorld().spawn(location, clazz, initializer));
        }
    }
}
