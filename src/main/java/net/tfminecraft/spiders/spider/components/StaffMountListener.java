package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.Objects;

/** Rechecks both interaction and actual mounting, including direct /ride attempts. */
public class StaffMountListener implements Listener {
    private final ECS app;

    public StaffMountListener(ECS app) {
        this.app = app;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEntityEvent event) {
        for (Mountable mountable : app.query(Mountable.class)) {
            if (!Objects.equals(event.getRightClicked(), mountable.getCurrentPig()) && !Objects.equals(event.getRightClicked(), mountable.getCurrentMarker())) continue;
            if (!StaffAccess.require(event.getPlayer())) {
                event.setCancelled(true);
                return;
            }
            // no saddle: it would show on the invisible pig, so staff are seated directly (sneak to skip)
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND || event.getPlayer().isSneaking()) return;
            ArmorStand marker = mountable.getCurrentMarker();
            if (marker == null || mountable.getRider() != null) return;
            marker.addPassenger(event.getPlayer());
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMount(EntityMountEvent event) {
        for (Mountable mountable : app.query(Mountable.class)) {
            if (!Objects.equals(event.getMount(), mountable.getCurrentPig()) && !Objects.equals(event.getMount(), mountable.getCurrentMarker())) continue;
            Player player = event.getEntity() instanceof Player p ? p : null;
            if (player == null || !StaffAccess.require(player)) {
                event.setCancelled(true);
                return;
            }
            if (Objects.equals(event.getMount(), mountable.getCurrentPig())) {
                event.setCancelled(true);
                ArmorStand marker = mountable.getCurrentMarker();
                if (marker == null) return;
                if (marker.getPassengers().isEmpty()) marker.addPassenger(player);
            }
        }
    }
}
