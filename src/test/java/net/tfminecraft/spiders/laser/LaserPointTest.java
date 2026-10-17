package net.tfminecraft.spiders.laser;

import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.OrientationMode;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.rendering.RenderEntityTracker;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LaserPointTest {
    private final World world = mock(World.class);
    private final ECS app = new ECS();
    private final Gait gait = Gait.defaultWalk();

    LaserPointTest() {
        LaserPoint.setupLaserPointer(app);
    }

    /** A spider at {@code position} whose options always use {@link #gait}; returns its locomotion. */
    private Locomotion spider(Vector position) {
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getGait()).thenReturn(gait);
        when(options.getWalkGait()).thenReturn(gait);
        Locomotion locomotion = new Locomotion();
        app.spawn(SpiderBody.create(world, position), options, locomotion);
        return locomotion;
    }

    private LaserPoint laser(World world, Vector position, Vector holder) {
        LaserPoint laser = new LaserPoint(world, position, holder, false);
        app.spawn(laser);
        return laser;
    }

    private static void assertVector(Vector expected, Vector actual) {
        assertEquals(expected.getX(), actual.getX(), 1e-9);
        assertEquals(expected.getY(), actual.getY(), 1e-9);
        assertEquals(expected.getZ(), actual.getZ(), 1e-9);
    }

    @Test
    @DisplayName("a laser point keeps where it is, who holds it and whether it is drawn")
    void aLaserPointKeepsItsState() {
        LaserPoint laser = new LaserPoint(world, new Vector(1, 2, 3), new Vector(4, 5, 6), true);
        World other = mock(World.class);
        laser.setWorld(other);
        laser.setPosition(new Vector(7, 8, 9));
        laser.setHolder(new Vector(0, 1, 0));
        laser.setVisible(false);
        assertSame(other, laser.getWorld());
        assertEquals(new Vector(7, 8, 9), laser.getPosition());
        assertEquals(new Vector(0, 1, 0), laser.getHolder());
        assertEquals(false, laser.isVisible());
    }

    @Test
    @DisplayName("a spider walks at full speed towards the nearest guide held near it in its world")
    void aSpiderWalksTowardsTheNearestGuide() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        Locomotion locomotion = spider(start);
        Vector near = start.clone();
        laser(mock(World.class), new Vector(1.5, 64.0, 0.5), near);
        laser(world, new Vector(-2.5, 64.0, 0.5), new Vector(LaserPoint.FOLLOW_RANGE + 10.0, 64.0, 0.5));
        laser(world, new Vector(-20.5, 64.0, 0.5), near);
        laser(world, new Vector(10.5, 64.0, 0.5), near);
        laser(world, new Vector(15.5, 64.0, 0.5), near);

        app.update();

        assertVector(new Vector(1, 0, 0), locomotion.getFaceDirection());
        assertVector(new Vector(gait.getMaxSpeed(), 0.0, 0.0), locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("close to the point, a spider only turns to face it")
    void closeToThePointASpiderOnlyTurns() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        Locomotion locomotion = spider(start);
        laser(world, new Vector(0.5, 64.0, 2.0), start);

        app.update();

        assertVector(new Vector(0, 0, 1), locomotion.getFaceDirection());
        assertVector(new Vector(), locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("a fast spider stops early enough to halt at the point")
    void aFastSpiderStopsEarly() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        Locomotion locomotion = spider(start);
        app.getEntities().getFirst().query(SpiderBody.class).getVelocity().setX(1.0);
        laser(world, new Vector(10.5, 64.0, 0.5), start);

        app.update();

        assertVector(new Vector(), locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("standing on the point, a spider neither turns nor walks")
    void standingOnThePointASpiderStays() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        Locomotion locomotion = spider(start);
        laser(world, start.clone(), start);

        app.update();

        assertNull(locomotion.getFaceDirection());
        assertNull(locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("a spider ignores guides held too far away or in other worlds")
    void aSpiderIgnoresFarGuides() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        Locomotion locomotion = spider(start);
        laser(mock(World.class), new Vector(5.5, 64.0, 0.5), start);
        laser(world, new Vector(5.5, 64.0, 0.5), new Vector(0.5, 64.0, LaserPoint.FOLLOW_RANGE + 1.0));

        app.update();

        assertNull(locomotion.getFaceDirection());
        assertNull(locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("a spider that cannot move is skipped and the others still follow")
    void aSpiderThatCannotMoveIsSkipped() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        SpiderOptions options = mock(SpiderOptions.class);
        app.spawn(SpiderBody.create(world, start), options);
        Locomotion locomotion = spider(start);
        laser(world, new Vector(10.5, 64.0, 0.5), start);

        app.update();

        assertVector(new Vector(1, 0, 0), locomotion.getFaceDirection());
    }

    @Test
    @DisplayName("on the ground a point overhead counts as reached; climbing, its height counts too")
    void climbingSpidersCountHeight() {
        Vector start = new Vector(0.5, 64.0, 0.5);
        Locomotion walker = spider(start);
        laser(world, new Vector(1.5, 74.0, 0.5), start);
        app.update();
        assertVector(new Vector(), walker.getWalkVelocity());

        gait.setScanOrientation(OrientationMode.GroundOrientation);
        walker.clear();
        app.update();
        Vector offset = new Vector(1.0, 10.0, 0.0).normalize();
        assertVector(offset.multiply(gait.getMaxSpeed()), walker.getWalkVelocity());
    }

    @Test
    @DisplayName("only a visible point is drawn, as a small glowing redstone block")
    @SuppressWarnings("unchecked")
    void onlyAVisiblePointIsDrawn() {
        try (AppTestSupport ignored = new AppTestSupport()) {
            BlockDisplay display = mock(BlockDisplay.class);
            when(world.spawn(any(Location.class), eq(BlockDisplay.class), any(Consumer.class))).thenAnswer(call -> {
                call.<Consumer<BlockDisplay>>getArgument(2).accept(display);
                return display;
            });
            LaserPoint hidden = laser(world, new Vector(3.5, 64.0, 0.5), new Vector());
            LaserPoint shown = laser(world, new Vector(1.5, 64.0, 0.5), new Vector());
            shown.setVisible(true);

            app.render();

            ArgumentCaptor<Location> at = ArgumentCaptor.forClass(Location.class);
            verify(world).spawn(at.capture(), eq(BlockDisplay.class), any(Consumer.class));
            assertEquals(new Vector(1.5, 64.0, 0.5), at.getValue().toVector());
            ArgumentCaptor<BlockData> block = ArgumentCaptor.forClass(BlockData.class);
            verify(display).setBlock(block.capture());
            assertEquals(Material.REDSTONE_BLOCK, block.getValue().getMaterial());
            verify(display).setTeleportDuration(1);
            verify(display).setBrightness(new Display.Brightness(15, 15));
            RenderEntityTracker.detach(shown);
            RenderEntityTracker.detach(hidden);
        }
    }
}
