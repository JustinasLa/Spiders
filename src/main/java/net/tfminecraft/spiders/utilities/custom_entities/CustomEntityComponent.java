package net.tfminecraft.spiders.utilities.custom_entities;

import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInteractEntityEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class CustomEntityComponent {
    private final String tag;

    private CustomEntityComponent(String tag) {
        this.tag = tag;
    }

    public static CustomEntityComponent create(NamespacedKey tag) {
        return new CustomEntityComponent(tag.getNamespace() + "_" + tag.getKey());
    }

    public static CustomEntityComponent fromString(String tag) {
        return new CustomEntityComponent(tag);
    }

    public String getTag() {
        return tag;
    }

    public List<Entity> entities() {
        List<Entity> out = new ArrayList<>();
        for (Entity entity : CustomEntities.allEntities()) {
            if (entity.getScoreboardTags().contains(tag)) out.add(entity);
        }
        return out;
    }

    public boolean isAttached(Entity entity) {
        return entity.getScoreboardTags().contains(tag);
    }

    public void onTick(Consumer<Entity> action) {
        Scheduler.onTick(it -> {
            for (Entity entity : entities()) action.accept(entity);
        });
    }

    public void onInteract(Consumer<PlayerInteractEntityEvent> action) {
        Events.onInteractEntity((PlayerInteractEntityEvent event) -> {
            if (!event.getRightClicked().getScoreboardTags().contains(tag)) return;
            action.accept(event);
        });
    }
}
