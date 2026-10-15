package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityMountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class StaffMountTest {
    private final ECS app = new ECS();
    private final Pig pig = mock(Pig.class);
    private final ArmorStand marker = mock(ArmorStand.class);
    private final Mountable mountable = new Mountable();
    private final StaffMountListener listener = new StaffMountListener(app);

    StaffMountTest() {
        mountable.setCurrentPig(pig);
        mountable.setCurrentMarker(marker);
        app.spawn(mountable);
    }

    @Test
    @DisplayName("nonstaff cannot mount either the pig or hidden seat")
    void nonstaffCannotMountEitherSeat() {
        Player player = mock(Player.class);
        for (Entity seat : List.<Entity>of(pig, marker)) {
            EntityMountEvent event = new EntityMountEvent(player, seat);
            listener.onMount(event);
            assertTrue(event.isCancelled());
        }
        verify(marker, never()).addPassenger(any());
    }

    @Test
    @DisplayName("nonstaff interaction with a spider is cancelled before saddle access")
    void nonstaffInteractionIsCancelledBeforeSaddleAccess() {
        Player player = mock(Player.class);
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, pig, EquipmentSlot.HAND);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        verify(player, never()).getInventory();
    }

    @Test
    @DisplayName("staff right-clicking the spider are seated without a saddle")
    void staffRightClickSeatsWithoutSaddle() {
        Player player = staff();
        when(marker.getPassengers()).thenReturn(List.of());
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, pig, EquipmentSlot.HAND);
        listener.onInteract(event);
        assertTrue(event.isCancelled());
        verify(marker).addPassenger(player);
        verify(pig, never()).setSaddle(true);
    }

    @Test
    @DisplayName("sneaking, off-hand or an occupied seat does not seat staff, and no saddle goes on")
    void noSeatWhenSneakingOffHandOrOccupied() {
        Player sneaking = staff();
        when(sneaking.isSneaking()).thenReturn(true);
        PlayerInteractEntityEvent sneak = new PlayerInteractEntityEvent(sneaking, pig, EquipmentSlot.HAND);
        listener.onInteract(sneak);
        assertTrue(sneak.isCancelled());

        PlayerInteractEntityEvent offHand = new PlayerInteractEntityEvent(staff(), pig, EquipmentSlot.OFF_HAND);
        listener.onInteract(offHand);
        assertTrue(offHand.isCancelled());

        Player rider = staff();
        when(marker.getPassengers()).thenReturn(List.of(rider));
        PlayerInteractEntityEvent occupied = new PlayerInteractEntityEvent(staff(), pig, EquipmentSlot.HAND);
        listener.onInteract(occupied);
        assertTrue(occupied.isCancelled());

        verify(marker, never()).addPassenger(any());
    }

    private static Player staff() {
        Player player = mock(Player.class);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        return player;
    }

    @Test
    @DisplayName("staff mounting the pig is redirected to its seat")
    void staffMountingThePigIsRedirectedToItsSeat() {
        Player player = mock(Player.class);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        when(marker.getPassengers()).thenReturn(List.of());
        EntityMountEvent event = new EntityMountEvent(player, pig);
        listener.onMount(event);
        assertTrue(event.isCancelled());
        verify(marker).addPassenger(player);
    }

    @Test
    @DisplayName("ordinary animals are unaffected")
    void ordinaryAnimalsAreUnaffected() {
        EntityMountEvent event = new EntityMountEvent(mock(Player.class), mock(Pig.class));
        listener.onMount(event);
        assertFalse(event.isCancelled());
    }

    @Test
    @DisplayName("nonplayers cannot become spider passengers")
    void nonplayersCannotBecomeSpiderPassengers() {
        EntityMountEvent event = new EntityMountEvent(mock(Pig.class), marker);
        listener.onMount(event);
        assertTrue(event.isCancelled());
    }
}
