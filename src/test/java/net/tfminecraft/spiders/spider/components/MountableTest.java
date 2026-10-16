package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.rendering.RenderEntityTracker;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Pig;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MountableTest {
    private ComponentServer server;
    private final World world = mock(World.class);
    private final ECS app = new ECS();
    private final Gait gait = Gait.defaultWalk();
    private final SpiderOptions options = mock(SpiderOptions.class);
    private final SpiderBody spider = SpiderBody.create(world, new Vector(.5, 64.0, .5));
    private final Mountable mountable = new Mountable();
    private final Locomotion locomotion = new Locomotion();
    private final ArmorStand marker = mock(ArmorStand.class);
    private final List<Entity> spawned = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = new ComponentServer();
        gait.setMaxSpeed(.2);
        when(options.getGait()).thenReturn(gait);
        mountable.setCurrentMarker(marker);
    }

    @AfterEach
    void tearDown() {
        RenderEntityTracker.detach(new Pair<>(spider, "mountable.pig"));
        RenderEntityTracker.detach(new Pair<>(spider, "mountable.marker"));
        server.close();
    }

    private Player rider(boolean staff, float yaw, float pitch) {
        Player player = mock(Player.class);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(staff);
        when(player.getLocation()).thenAnswer(call -> new Location(world, .5, 65.0, .5, yaw, pitch));
        when(marker.getPassengers()).thenReturn(List.of(player));
        return player;
    }

    private static Input input(boolean forward, boolean back, boolean left, boolean right, boolean sprint) {
        Input input = mock(Input.class);
        when(input.isForward()).thenReturn(forward);
        when(input.isBackward()).thenReturn(back);
        when(input.isLeft()).thenReturn(left);
        when(input.isRight()).thenReturn(right);
        when(input.isSprint()).thenReturn(sprint);
        return input;
    }

    private static void ride(Player player, boolean forward, boolean back, boolean left, boolean right, boolean sprint) {
        Input keys = input(forward, back, left, right, sprint);
        when(player.getCurrentInput()).thenReturn(keys);
    }

    private void tick() {
        locomotion.clear();
        app.update();
    }

    @Test
    @DisplayName("the rider is the player on the seat marker, if any")
    void theRiderIsThePlayerOnTheSeatMarker() {
        Mountable empty = new Mountable();
        assertNull(empty.getRider());
        ArmorStand seat = mock(ArmorStand.class);
        empty.setCurrentMarker(seat);
        assertSame(seat, empty.getCurrentMarker());
        when(seat.getPassengers()).thenReturn(null);
        assertNull(empty.getRider());
        when(seat.getPassengers()).thenReturn(List.of());
        assertNull(empty.getRider());
        when(seat.getPassengers()).thenReturn(List.of(mock(Pig.class)));
        assertNull(empty.getRider());
        Player player = mock(Player.class);
        when(seat.getPassengers()).thenReturn(List.of(player));
        assertSame(player, empty.getRider());
    }

    @Test
    @DisplayName("setting up mounts listens for staff mounting")
    void settingUpMountsListensForStaffMounting() {
        Mountable.setupMountable(app);
        HitDetector.setupHitDetector(app);
        ArgumentCaptor<Listener> listeners = ArgumentCaptor.forClass(Listener.class);
        verify(server.pluginManager, atLeastOnce()).registerEvents(listeners.capture(), eq(server.plugin));
        List<Class<?>> types = listeners.getAllValues().stream().<Class<?>>map(Object::getClass).toList();
        assertEquals(List.of(StaffMountListener.class, SeatProjectileListener.class, MeleeListener.class), types);
    }

    @Test
    @DisplayName("the rider steers where they look and walks with their keys")
    void theRiderSteersWhereTheyLookAndWalksWithTheirKeys() {
        Mountable.setupMountable(app);
        Cloak cloak = new Cloak();
        app.spawn(mountable, spider, options, locomotion, cloak);
        Player player = rider(true, 0f, 0f);

        ride(player, false, false, false, false, false);
        tick();
        assertEquals(new Vector(), locomotion.getWalkVelocity(), "No keys, no walking");
        assertTrue(locomotion.getFaceDirection().getZ() > .99, "Faces where the rider looks: " + locomotion.getFaceDirection());

        ride(player, true, false, false, false, false);
        tick();
        assertEquals(.2, locomotion.getWalkVelocity().getZ(), 1e-6);

        ride(player, false, true, true, false, false);
        tick();
        Vector backLeft = locomotion.getWalkVelocity();
        assertEquals(.2, backLeft.length(), 1e-6);
        assertTrue(backLeft.getZ() < 0 && Math.abs(backLeft.getX()) > .1, "Back and to the side: " + backLeft);

        ride(player, false, false, true, true, false);
        tick();
        assertEquals(new Vector(), locomotion.getWalkVelocity(), "Left and right cancel out");
    }

    @Test
    @DisplayName("holding sprint while moving gallops until the rider lets go or gets off")
    void holdingSprintGallopsUntilTheRiderLetsGo() {
        Mountable.setupMountable(app);
        app.spawn(mountable, spider, options, locomotion);
        Player player = rider(true, 0f, 0f);

        ride(player, false, false, false, false, true);
        tick();
        verify(options, never()).setGallop(anyBoolean());

        ride(player, true, false, false, false, true);
        tick();
        verify(options).setGallop(true);
        assertTrue(mountable.getSprintGallop());

        ride(player, true, false, false, false, false);
        tick();
        verify(options).setGallop(false);
        assertFalse(mountable.getSprintGallop());

        // gallop the staff switched on stays on
        when(options.getGallop()).thenReturn(true);
        ride(player, true, false, false, false, true);
        tick();
        assertFalse(mountable.getSprintGallop());
        when(options.getGallop()).thenReturn(false);

        ride(player, true, false, false, false, true);
        tick();
        assertTrue(mountable.getSprintGallop());
        when(marker.getPassengers()).thenReturn(List.of());
        tick();
        assertFalse(mountable.getSprintGallop(), "Getting off ends the sprint gallop");
        tick();
        verify(options, org.mockito.Mockito.times(2)).setGallop(false);
    }

    @Test
    @DisplayName("non-staff riders are thrown off, and a spider that can't walk ignores its rider")
    void nonStaffRidersAreThrownOff() {
        Mountable.setupMountable(app);
        ECSEntity entity = app.spawn(mountable, spider, options, locomotion);
        Player intruder = rider(false, 0f, 0f);
        tick();
        verify(intruder).leaveVehicle();

        entity.removeComponent(Locomotion.class);
        Player staff = rider(true, 0f, 0f);
        tick();
        verify(staff, never()).leaveVehicle();
        verify(staff, never()).getCurrentInput();
    }

    @Test
    @DisplayName("on a wall the rider steers along it, and looking straight into it follows the body")
    void onAWallTheRiderSteersAlongIt() {
        Mountable.setupMountable(app);
        when(options.getClimbing()).thenReturn(true);
        // clinging to a wall that faces -z: its back points out of the wall, its head up the wall
        SpiderBody climber = SpiderBody.create(world, new Vector(.5, 64.0, 9.0), new Quaternionf().rotateX((float) (-Math.PI / 2)));
        Vector up = Maths.rotate(Maths.upVector(), climber.getPreferredOrientation());
        assertTrue(up.distance(new Vector(0.0, 0.0, -1.0)) < 1e-6, "Setup: back points out of the wall: " + up);
        assertTrue(climber.forwardDirection().distance(new Vector(0.0, 1.0, 0.0)) < 1e-6, "Setup: head points up the wall");
        app.spawn(mountable, climber, options, locomotion);

        // looking up, to the side and into the wall: forward is that look laid flat on the wall
        Player player = rider(true, -45f, -30f);
        Vector look = new Location(world, 0.0, 0.0, 0.0, -45f, -30f).getDirection();
        Vector along = look.clone().subtract(up.clone().multiply(look.dot(up))).normalize();
        assertTrue(along.getX() > .5 && along.getY() > .5, "Setup: the look has parts up and along the wall: " + along);
        ride(player, true, false, false, false, false);
        tick();
        assertTrue(locomotion.getFaceDirection().distance(along) < 1e-6, "Faces " + locomotion.getFaceDirection() + ", not " + along);
        assertTrue(locomotion.getWalkVelocity().distance(along.clone().multiply(.2)) < 1e-6, "Walks " + locomotion.getWalkVelocity());

        // strafing stays on the wall too
        ride(player, false, false, true, false, false);
        tick();
        Vector strafe = locomotion.getWalkVelocity();
        assertEquals(.2, strafe.length(), 1e-6);
        assertEquals(0.0, strafe.dot(up), 1e-6, "Strafed off the wall: " + strafe);
        assertEquals(0.0, strafe.dot(along), 1e-6, "Strafe should be square to forward: " + strafe);

        // looking straight into the wall says nothing about which way is forward: keep the body's heading
        Player intoWall = rider(true, 0f, 0f);
        ride(intoWall, false, false, false, false, false);
        tick();
        assertTrue(locomotion.getFaceDirection().distance(climber.forwardDirection()) < 1e-6, "Faces " + locomotion.getFaceDirection());
        assertEquals(new Vector(), locomotion.getWalkVelocity());
    }

    private <T extends Entity> T spawnedMock(Class<T> type, Location location, Consumer<T> init) {
        T entity = mock(type);
        when(entity.getLocation()).thenReturn(location);
        when(entity.isValid()).thenReturn(true);
        when(entity.getType()).thenReturn(type == Pig.class ? EntityType.PIG : EntityType.ARMOR_STAND);
        if (entity instanceof Pig pig) when(pig.hasSaddle()).thenReturn(true);
        init.accept(entity);
        spawned.add(entity);
        return entity;
    }

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("a hidden saddle pig and seat marker follow the spider, the seat below a ceiling-walker")
    void aHiddenSaddlePigAndSeatMarkerFollowTheSpider() {
        Mountable.setupMountable(app);
        Mountable fresh = new Mountable();
        app.spawn(fresh, spider, options);
        when(world.spawn(any(Location.class), any(Class.class), any(Consumer.class))).thenAnswer(call ->
            spawnedMock(call.<Class<Entity>>getArgument(1), call.getArgument(0), call.getArgument(2)));

        app.render();
        assertEquals(2, spawned.size());
        Pig pig = assertInstanceOf(Pig.class, spawned.get(0));
        ArmorStand seat = assertInstanceOf(ArmorStand.class, spawned.get(1));
        assertSame(pig, fresh.getCurrentPig());
        assertSame(seat, fresh.getCurrentMarker());
        verify(pig).setInvisible(true);
        verify(pig).setAI(false);
        verify(pig).setSaddle(false);
        verify(seat).setMarker(true);
        assertTrue(pig.getLocation().getY() < spider.getPosition().getY());
        assertTrue(seat.getLocation().getY() > spider.getPosition().getY());

        // upside down on a ceiling the seat hangs below the belly
        when(pig.hasSaddle()).thenReturn(false);
        when(options.getClimbing()).thenReturn(true);
        spider.getOrientation().set(new Quaternionf().rotateX((float) Math.PI));
        app.render();
        assertEquals(2, spawned.size(), "The seat entities are reused");
        ArgumentCaptor<Location> moved = ArgumentCaptor.forClass(Location.class);
        verify(seat).teleport(moved.capture(), any(io.papermc.paper.entity.TeleportFlag[].class));
        assertEquals(spider.getPosition().getY() - .3 - 1.8, moved.getValue().getY(), 1e-6);
        verify(pig).setSaddle(false);
        assertTrue(Collections.frequency(spawned, pig) == 1);
    }
}
