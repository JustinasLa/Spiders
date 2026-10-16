package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LightningStrike;
import org.bukkit.entity.Pig;
import org.bukkit.entity.PigZombie;
import org.bukkit.event.entity.PigZapEvent;
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
    @DisplayName("staff right-clicking the seat marker of the second spider are seated on it")
    void staffRightClickingTheSeatMarkerOfTheSecondSpider() {
        ECS twoSpiders = new ECS();
        twoSpiders.spawn(new Mountable());
        twoSpiders.spawn(mountable);
        Player player = staff();
        PlayerInteractEntityEvent event = new PlayerInteractEntityEvent(player, marker, EquipmentSlot.HAND);
        new StaffMountListener(twoSpiders).onInteract(event);
        assertTrue(event.isCancelled());
        verify(marker).addPassenger(player);

        PlayerInteractEntityEvent other = new PlayerInteractEntityEvent(player, mock(Pig.class), EquipmentSlot.HAND);
        new StaffMountListener(twoSpiders).onInteract(other);
        assertFalse(other.isCancelled());
    }

    @Test
    @DisplayName("a spider without a seat marker yet cannot be ridden")
    void aSpiderWithoutASeatMarkerYetCannotBeRidden() {
        ECS app = new ECS();
        Pig lonePig = mock(Pig.class);
        Mountable unseated = new Mountable();
        unseated.setCurrentPig(lonePig);
        app.spawn(unseated);
        StaffMountListener lone = new StaffMountListener(app);
        Player player = staff();

        PlayerInteractEntityEvent interact = new PlayerInteractEntityEvent(player, lonePig, EquipmentSlot.HAND);
        lone.onInteract(interact);
        assertTrue(interact.isCancelled());
        EntityMountEvent mount = new EntityMountEvent(player, lonePig);
        lone.onMount(mount);
        assertTrue(mount.isCancelled());
        verify(player, never()).leaveVehicle();
    }

    @Test
    @DisplayName("staff may sit on the seat marker itself, but not take an occupied seat through the pig")
    void staffMaySitOnTheMarkerButNotTakeAnOccupiedSeat() {
        Player player = staff();
        EntityMountEvent direct = new EntityMountEvent(player, marker);
        listener.onMount(direct);
        assertFalse(direct.isCancelled());

        Player sitting = staff();
        when(marker.getPassengers()).thenReturn(List.of(sitting));
        EntityMountEvent throughPig = new EntityMountEvent(player, pig);
        listener.onMount(throughPig);
        assertTrue(throughPig.isCancelled());
        verify(marker, never()).addPassenger(any());
    }

    @Test
    @DisplayName("lightning can't turn the seat pig into a piglin")
    void lightningCantTurnTheSeatPigIntoAPiglin() {
        PigZapEvent zap = new PigZapEvent(pig, mock(LightningStrike.class), mock(PigZombie.class));
        listener.onZap(zap);
        assertTrue(zap.isCancelled());

        PigZapEvent farm = new PigZapEvent(mock(Pig.class), mock(LightningStrike.class), mock(PigZombie.class));
        listener.onZap(farm);
        assertFalse(farm.isCancelled());
    }

    @Test
    @DisplayName("nonplayers cannot become spider passengers")
    void nonplayersCannotBecomeSpiderPassengers() {
        EntityMountEvent event = new EntityMountEvent(mock(Pig.class), marker);
        listener.onMount(event);
        assertTrue(event.isCancelled());
    }
}
