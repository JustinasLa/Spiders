package net.tfminecraft.spiders.utilities.ecs;

import java.util.ArrayList;
import java.util.List;

public class ECSEntity implements ECSQueryable {
    private final List<ECSComponent> components = new ArrayList<>();

    private boolean scheduledForRemoval = false;

    public List<ECSComponent> getComponents() {
        return components;
    }

    public boolean getScheduledForRemoval() {
        return scheduledForRemoval;
    }

    public void setScheduledForRemoval(boolean scheduledForRemoval) {
        this.scheduledForRemoval = scheduledForRemoval;
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

    public <Old extends ECSComponent> void replaceComponent(Class<Old> clazz, Old component) {
        removeComponent(clazz);
        addComponent(component);
    }

    public <T extends ECSQueryable> T query(Class<T> clazz) {
        if (clazz.isInstance(this)) return clazz.cast(this);
        for (ECSComponent component : components) {
            if (clazz.isInstance(component)) return clazz.cast(component);
        }
        return null;
    }
}
