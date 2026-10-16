package net.tfminecraft.spiders.utilities.ecs;

import java.util.ArrayList;
import java.util.List;

public class ECSEntity implements ECSQueryable {
    private final List<ECSComponent> components = new ArrayList<>();

    private boolean scheduledForRemoval = false;
    /** Asleep entities keep their state but are left out of the systems until they wake. */
    private boolean asleep = false;

    public List<ECSComponent> getComponents() {
        return components;
    }

    public boolean getScheduledForRemoval() {
        return scheduledForRemoval;
    }

    public boolean isAsleep() {
        return asleep;
    }

    public void setAsleep(boolean asleep) {
        this.asleep = asleep;
    }

    public void remove() {
        this.scheduledForRemoval = true;
    }

    public void addComponent(ECSComponent component) {
        components.add(component);
    }

    public <T extends ECSComponent> void removeComponent(Class<T> clazz) {
        components.removeIf(clazz::isInstance);
    }

    public <T extends ECSQueryable> T query(Class<T> clazz) {
        if (clazz.isInstance(this)) return clazz.cast(this);
        for (ECSComponent component : components) {
            if (clazz.isInstance(component)) return clazz.cast(component);
        }
        return null;
    }
}
