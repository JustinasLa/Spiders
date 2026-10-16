package net.tfminecraft.spiders.utilities.rendering;

import io.papermc.paper.entity.TeleportFlag;
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
            // a seat carries its rider along; unmoved entities aren't sent again
            if (!entity.isInsideVehicle() && !isAt(entity, location)) entity.teleport(location, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            update.accept(entity);
            RenderVisibility.apply(entity);
        } else {
            Consumer<T> initializer = it -> {
                it.setPersistent(false);
                RenderVisibility.onSpawn(it);
                init.accept(it);
                update.accept(it);
            };
            T spawned = location.getWorld().spawn(location, clazz, initializer);
            RenderEntityTracker.put(handle, spawned);
            RenderVisibility.apply(spawned);
        }
    }

    private static boolean isAt(Entity entity, Location location) {
        Location current = entity.getLocation();
        return current.getWorld() == location.getWorld()
            && current.getX() == location.getX() && current.getY() == location.getY() && current.getZ() == location.getZ()
            && current.getYaw() == location.getYaw() && current.getPitch() == location.getPitch();
    }
}
