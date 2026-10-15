package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;

import java.util.Objects;

/** The hidden saddle pig and seat marker would otherwise stop projectiles in mid-air. */
public class SeatProjectileListener implements Listener {
    private final ECS app;

    public SeatProjectileListener(ECS app) {
        this.app = app;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(ProjectileHitEvent event) {
        Entity hit = event.getHitEntity();
        if (hit == null) return;
        for (Mountable it : app.query(Mountable.class)) {
            if (Objects.equals(hit, it.getCurrentPig()) || Objects.equals(hit, it.getCurrentMarker())) {
                event.setCancelled(true);
                return;
            }
        }
    }
}
