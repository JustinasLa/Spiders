package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Pig;
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
            if (event.getHand() != EquipmentSlot.HAND) return;
            Pig pig = mountable.getCurrentPig();
            if (pig == null) return;
            if (event.getPlayer().isSneaking() && event.getPlayer().getInventory().getItemInMainHand().getType().isAir() && mountable.getRider() == null) {
                pig.setSaddle(false);
            }
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
