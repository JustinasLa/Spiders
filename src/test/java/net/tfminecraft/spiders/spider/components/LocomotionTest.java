package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocomotionTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    private final Gait gait = Gait.defaultWalk();

    private static Leg leg(boolean grounded, boolean uncomfortable, boolean moving) {
        Leg leg = mock(Leg.class);
        when(leg.isGrounded()).thenReturn(grounded);
        when(leg.isUncomfortable()).thenReturn(uncomfortable);
        when(leg.isMoving()).thenReturn(moving);
        return leg;
    }

    private static SpiderBody body(Leg... legs) {
        SpiderBody body = SpiderBody.create(mock(World.class), new Vector(.5, 64.0, .5));
        body.setLegs(List.of(legs));
        return body;
    }

    @Test
    @DisplayName("the strongest walk and face orders win until cleared, and pushes add up")
    void theStrongestOrdersWinUntilClearedAndPushesAddUp() {
        Locomotion locomotion = new Locomotion();
        assertNull(locomotion.getWalkVelocity());
        Vector order = new Vector(1.0, 0.0, 0.0);
        locomotion.walk(order, Locomotion.RIDER_PRIORITY);
        order.setX(5.0);
        locomotion.walk(new Vector(0.0, 0.0, 1.0));
        assertEquals(new Vector(1.0, 0.0, 0.0), locomotion.getWalkVelocity(), "A weaker order (or a later change to the vector) overrode the rider");
        locomotion.walk(new Vector(0.0, 0.0, 2.0), Locomotion.POUNCE_PRIORITY);
        assertEquals(new Vector(0.0, 0.0, 2.0), locomotion.getWalkVelocity());

        locomotion.face(new Vector(1.0, 0.0, 0.0), Locomotion.LASER_PRIORITY);
        locomotion.face(new Vector(0.0, 0.0, 1.0));
        assertEquals(new Vector(1.0, 0.0, 0.0), locomotion.getFaceDirection());
        locomotion.face(new Vector(-1.0, 0.0, 0.0), Locomotion.LASER_PRIORITY);
        assertEquals(new Vector(-1.0, 0.0, 0.0), locomotion.getFaceDirection());

        Vector push = new Vector(1.0, 0.0, 0.0);
        locomotion.push(push);
        locomotion.push(new Vector(0.0, 0.0, 2.0));
        assertEquals(new Vector(1.0, 0.0, 0.0), push, "The caller's vector was changed");
        assertEquals(new Vector(1.0, 0.0, 2.0), locomotion.getPushVelocity());

        locomotion.clear();
        assertNull(locomotion.getWalkVelocity());
        assertNull(locomotion.getFaceDirection());
        assertNull(locomotion.getPushVelocity());
        locomotion.walk(new Vector(0.0, 0.0, 3.0), Locomotion.DEFAULT_PRIORITY);
        assertEquals(new Vector(0.0, 0.0, 3.0), locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("a push alone slides a standing spider")
    void aPushAloneSlidesAStandingSpider() {
        World world = new Room((x, y, z) -> x >= -20 && x <= 20 && z >= -20 && z <= 20 && y >= -3 && y <= -1).world();
        Run run = spawn(world, .8, false, new Vector(.5, 0.0, .5), false);
        for (int i = 0; i < 30; i++) run.app().update();
        double start = run.body().getPosition().getX();
        for (int i = 0; i < 40; i++) {
            run.locomotion().push(new Vector(.1, 0.0, 0.0));
            run.app().update();
        }
        assertTrue(run.body().getPosition().getX() > start + .5, "Not pushed: " + run.describe(-1));
        assertNull(run.locomotion().getPushVelocity(), "Orders are cleared after each tick");
    }

    @Test
    @DisplayName("a spider in mid-leap can't steer")
    void aSpiderInMidLeapCantSteer() {
        World world = new Room((x, y, z) -> x >= -20 && x <= 20 && z >= -20 && z <= 20 && y >= -3 && y <= -1).world();
        Run run = spawn(world, .8, false, new Vector(.5, 0.0, .5), false);
        for (int i = 0; i < 30; i++) run.app().update();
        run.body().leap(new Vector(0.0, .4, 0.0), 10);
        for (int i = 0; i < 5; i++) {
            run.locomotion().walk(new Vector(.5, 0.0, 0.0));
            run.app().update();
        }
        assertEquals(0.0, run.body().getVelocity().getX(), 1e-9, "Steered in the air");
    }

    @Test
    @DisplayName("with nowhere to face, a spider keeps its heading")
    void withNowhereToFaceASpiderKeepsItsHeading() {
        for (Vector target : List.of(new Vector(), new Vector(Double.NaN, 0.0, 0.0), new Vector(Double.POSITIVE_INFINITY, 0.0, 0.0))) {
            SpiderBody body = body(leg(true, false, false));
            Locomotion.rotateTowards(body, target, gait);
            assertEquals(0f, body.getRotationalVelocity().length(), 1e-6f, "Turned towards " + target);
            assertFalse(body.isRotatingYaw());
        }
    }

    @Test
    @DisplayName("a spider turns towards where it is told to face, unless a planted leg is strained")
    void aSpiderTurnsUnlessAPlantedLegIsStrained() {
        SpiderBody turning = body(leg(true, false, false), leg(false, false, false), leg(true, true, true));
        Locomotion.rotateTowards(turning, new Vector(1.0, 0.0, 0.0), gait);
        assertTrue(turning.isRotatingYaw());
        assertTrue(Math.abs(turning.getRotationalVelocity().y) > 0f, "Did not turn: " + turning.getRotationalVelocity());

        SpiderBody strained = body(leg(true, false, false), leg(true, true, false));
        Locomotion.rotateTowards(strained, new Vector(1.0, 0.0, 0.0), gait);
        assertFalse(strained.isRotatingYaw());
        assertEquals(0f, strained.getRotationalVelocity().y, 1e-6f);
    }

    @Test
    @DisplayName("a strained spider walks slower but still means to walk")
    void aStrainedSpiderWalksSlowerButStillMeansToWalk() {
        Vector target = new Vector(0.0, 0.0, 1.0);
        SpiderBody relaxed = body(leg(true, false, false));
        SpiderBody strained = body(leg(true, true, false));
        for (int i = 0; i < 200; i++) {
            Locomotion.walkAt(relaxed, target, null, gait);
            Locomotion.walkAt(strained, target, null, gait);
        }
        assertTrue(relaxed.isWalking());
        assertTrue(strained.isWalking());
        assertEquals(1.0, relaxed.getVelocity().getZ(), 1e-6);
        assertEquals(gait.getUncomfortableSpeedMultiplier(), strained.getVelocity().getZ(), 1e-6);

        Locomotion.walkAt(strained, new Vector(), null, gait);
        assertFalse(strained.isWalking(), "A strained spider told to stand still is not walking");
    }

    @Test
    @DisplayName("a stunned spider told to stand still stops walking at once")
    void aStunnedSpiderToldToStandStillStopsWalking() {
        HitDetector stunned = new HitDetector();
        stunned.setStunned(true);
        assertTrue(stunned.getStunned());
        HitDetector alert = new HitDetector();

        SpiderBody body = body(leg(true, false, false));
        body.getVelocity().setZ(1.0);
        Locomotion.walkAt(body, new Vector(), alert, gait);
        assertTrue(body.isWalking(), "Still sliding to a stop");
        Locomotion.walkAt(body, new Vector(), stunned, gait);
        assertFalse(body.isWalking());
        Locomotion.walkAt(body, new Vector(0.0, 0.0, 1.0), stunned, gait);
        assertTrue(body.isWalking(), "Told to walk, a stunned spider walks");
    }
}
