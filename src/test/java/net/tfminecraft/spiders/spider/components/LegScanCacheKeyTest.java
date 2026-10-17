package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.OrientationMode;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.presets.Presets;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/** A still leg's cached ground scan is dropped as soon as anything the scan reads changes. */
class LegScanCacheKeyTest {
    private static final int SETTLE_TICKS = 100;

    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    /** One {@code tick}; whether {@code leg} cast its main scan ray (from the start of its scan line, as long as it). */
    private static boolean scans(Runnable tick, World world, Leg leg) {
        clearInvocations(world);
        tick.run();
        Vector start = leg.getScanLine().getPoint1();
        double length = leg.getScanLine().vector().length();
        return mockingDetails(world).getInvocations().stream()
            .filter(it -> it.getMethod().getName().equals("rayTraceBlocks"))
            .anyMatch(it -> it.<Location>getArgument(0).toVector().distanceSquared(start) < 1e-12 && Math.abs(it.<Double>getArgument(2) - length) < 1e-9);
    }

    /** Waits for the first leg's periodic rescan, checks the next tick reuses it, then makes {@code change}: did the next tick rescan? */
    private static boolean rescansAfter(World world, Runnable tick, Run run, Consumer<Run> change) {
        Leg leg = run.body().getLegs().get(0);
        boolean refreshed = false;
        for (int i = 0; i < 2 * Leg.SCAN_REFRESH_TICKS && !refreshed; i++) refreshed = scans(tick, world, leg);
        assertTrue(refreshed, "Setup: no periodic rescan");
        assertFalse(scans(tick, world, leg), "Setup: the still leg did not reuse its scan");
        change.accept(run);
        return scans(tick, world, leg);
    }

    /**
     * Settles a standing spider and waits for its first leg's periodic rescan, so the next ticks reuse that scan;
     * then makes {@code change} and returns whether the leg scanned again on the very next tick.
     */
    private static boolean rescansAfter(boolean climbing, Consumer<Run> change) {
        return rescansAfter(climbing, change, run -> { });
    }

    /** As above, then runs {@code check} on the spider after that tick. */
    private static boolean rescansAfter(boolean climbing, Consumer<Run> change, Consumer<Run> check) {
        World world = new Room((x, y, z) -> y < 0 && y >= -3).world();
        Run run = spawn(world, 1.0, false, new Vector(.5, 0.0, .5), climbing);
        for (int tick = 0; tick < SETTLE_TICKS; tick++) run.app().update();
        boolean rescanned = rescansAfter(world, () -> run.app().update(), run, change);
        check.accept(run);
        return rescanned;
    }

    /**
     * A spider hanging still in a void at y = 10: no gravity, no locomotion, so only what the test changes moves.
     * Its legs find nothing, and keep reusing that empty scan.
     */
    private static boolean hangingRescansAfter(Consumer<Run> setup, Consumer<Run> change) {
        World world = new Room((x, y, z) -> false).world();
        Run run = spawn(world, 1.0, false, new Vector(.5, 0.0, .5), false);
        run.gait().setGravityAcceleration(0.0);
        setup.accept(run);
        Runnable tick = () -> run.body().update(run.app(), run.entity(), run.options());
        tick.run();
        run.body().getVelocity().zero();
        run.body().teleport(new Vector(.5, 10.0, .5));
        for (int i = 0; i < 10; i++) tick.run();
        assertTrue(run.body().getVelocity().isZero(), "Setup: still moving");
        return rescansAfter(world, tick, run, change);
    }

    @Test
    @DisplayName("a new body height rescans even when the scan still starts at the same point")
    void aNewBodyHeightRescansFromTheSameStart() {
        // the scan starts 1.6 body heights above the rest position, which sits a body height below the leg's planned rest:
        // height 1.25 with the rest at y = 0 and height 0.625 with it at y = 0.375 both start at 0.75 above the body
        assertFalse(hangingRescansAfter(run -> run.gait().getStationary().setBodyHeight(1.25), run -> { }), "Unchanged");
        Leg[] leg = {null};
        Vector[] before = {null, null};
        assertTrue(hangingRescansAfter(run -> run.gait().getStationary().setBodyHeight(1.25), run -> {
            leg[0] = run.body().getLegs().get(0);
            before[0] = leg[0].getScanLine().getPoint1().clone();
            before[1] = leg[0].getScanLine().getPoint2().clone();
            assertEquals(0.0, leg[0].getLegPlan().getRestPosition().getY());
            run.gait().getStationary().setBodyHeight(.625);
            leg[0].getLegPlan().getRestPosition().setY(.375);
        }));
        assertEquals(before[0], leg[0].getScanLine().getPoint1(), "Setup: the scan start moved");
        assertNotEquals(before[1], leg[0].getScanLine().getPoint2());
    }

    @Test
    @DisplayName("a walking leg whose rest moves under an unchanged look-ahead rescans")
    void aWalkingLegWhoseRestMovesRescans() {
        // the look-ahead sits the trigger radius (times a look-ahead fraction of 1) ahead of the rest position:
        // rest 1.5 ahead with radius 0.5, then rest 1.25 ahead with radius 0.75, both put it 2 blocks ahead of the body
        Consumer<Run> setup = run -> {
            run.body().setWalking(true);
            run.gait().setLegLookAheadFraction(1.0);
            run.gait().getStationary().setTriggerZoneRadius(.5);
            for (LegPlan plan : run.options().getBodyPlan().getLegs()) plan.getRestPosition().setZ(1.5);
        };
        assertFalse(hangingRescansAfter(setup, run -> { }), "Unchanged");
        Leg[] leg = {null};
        Vector[] before = {null, null};
        assertTrue(hangingRescansAfter(setup, run -> {
            leg[0] = run.body().getLegs().get(0);
            before[0] = leg[0].getLookAheadPosition().clone();
            before[1] = leg[0].getRestPosition().clone();
            run.gait().getStationary().setTriggerZoneRadius(.75);
            leg[0].getLegPlan().getRestPosition().setZ(1.25);
        }));
        assertEquals(before[0], leg[0].getLookAheadPosition(), "Setup: the look-ahead moved");
        assertNotEquals(before[1], leg[0].getRestPosition());
    }

    /** Runs {@code change} on a hanging spider and checks its first leg rescans and that {@code read} of that leg changed. */
    private static void rescansWhenChanged(Consumer<Run> setup, Consumer<Run> change, Function<Leg, Vector> read) {
        Leg[] leg = {null};
        Vector[] before = {null};
        assertTrue(hangingRescansAfter(setup, run -> {
            leg[0] = run.body().getLegs().get(0);
            before[0] = read.apply(leg[0]).clone();
            change.accept(run);
        }));
        assertNotEquals(before[0], read.apply(leg[0]), "Setup: nothing changed");
    }

    @Test
    @DisplayName("starting to walk moves the look-ahead and rescans")
    void startingToWalkRescans() {
        rescansWhenChanged(run -> { }, run -> run.body().setWalking(true), Leg::getLookAheadPosition);
    }

    @Test
    @DisplayName("a longer look-ahead while walking rescans")
    void aLongerLookAheadRescans() {
        rescansWhenChanged(run -> run.body().setWalking(true), run -> run.gait().setLegLookAheadFraction(1.0), Leg::getLookAheadPosition);
    }

    @Test
    @DisplayName("switching the scan orientation of a tilted body turns the surface up and rescans")
    void switchingTheScanOrientationRescans() {
        rescansWhenChanged(run -> run.body().getOrientation().rotationX(.3f),
            run -> run.gait().setScanOrientation(OrientationMode.SpiderOrientation), Leg::getSurfaceUp);
    }

    @Test
    @DisplayName("an unchanged standing leg keeps reusing its scan")
    void anUnchangedLegKeepsReusingItsScan() {
        assertFalse(rescansAfter(false, run -> { }));
    }

    @Test
    @DisplayName("turning alternative-ground scans off rescans")
    void turningAlternativeGroundOffRescans() {
        assertTrue(rescansAfter(false, run -> run.gait().setLegScanAlternativeGround(false)));
    }

    @Test
    @DisplayName("a new scan height bias rescans")
    void aNewHeightBiasRescans() {
        assertTrue(rescansAfter(false, run -> run.gait().setLegScanHeightBias(.7)));
    }

    @Test
    @DisplayName("gaining grip rescans")
    void gainingGripRescans() {
        assertTrue(rescansAfter(false, run -> run.gait().setGrip(true)));
    }

    @Test
    @DisplayName("turning the body on the spot rescans")
    void turningOnTheSpotRescans() {
        for (boolean climbing : new boolean[]{false, true}) {
            assertTrue(rescansAfter(climbing, run -> run.body().getOrientation().rotateY(.3f)), "climbing=" + climbing);
        }
    }

    @Test
    @DisplayName("moving a leg's rest position rescans, and the foot follows it")
    void movingTheRestPositionRescans() {
        double[] footZ = {0.0};
        assertTrue(rescansAfter(false, run -> {
            Leg leg = run.body().getLegs().get(0);
            footZ[0] = leg.getGroundTarget().getPosition().getZ();
            leg.getLegPlan().getRestPosition().add(new Vector(0.0, 0.0, .6));
        }, run -> {
            double moved = run.body().getLegs().get(0).getGroundTarget().getPosition().getZ() - footZ[0];
            assertTrue(moved > .3, "Foothold moved " + moved);
        }));
    }

    @Test
    @DisplayName("a new comfort zone radius rescans")
    void aNewComfortRadiusRescans() {
        assertTrue(rescansAfter(false, run -> run.gait().setComfortZoneRadius(run.gait().getComfortZoneRadius() * 1.5)));
    }

    // Vector equality is within 1e-6 per component, so a cache key can drift past that while the keys
    // compared before it (each within 1e-6) don't. The tests below drive each key's "differs" case that way.

    /** A spider hanging still in {@code world}, updated without locomotion, and the tick that updates it. */
    private record Hanging(Run run, World world, Runnable tick) {
        Leg leg() {
            return run.body().getLegs().get(0);
        }

        boolean rescansAfter(Consumer<Run> change) {
            return LegScanCacheKeyTest.rescansAfter(world, tick, run, change);
        }
    }

    /** A walk-gait spider with one leg resting at {@code legRest} (body space), body height {@code height}, hanging at {@code at}. */
    private static Hanging hang(World world, Vector legRest, double height, Vector at, Consumer<Run> setup) {
        List<SegmentPlan> segments = new ArrayList<>(List.of(new SegmentPlan(1.0, Presets.hipJoint()), new SegmentPlan(1.0, Presets.kneeJoint())));
        BodyPlan plan = new BodyPlan();
        plan.setLegs(new ArrayList<>(List.of(new LegPlan(new Vector(), legRest, segments, new Quaternionf()))));
        Run run = spawn(world, 1.0, false, new Vector(.5, 0.0, .5), false);
        when(run.options().getBodyPlan()).thenReturn(plan);
        run.gait().setGravityAcceleration(0.0);
        run.gait().setAirDragCoefficient(0.0);
        setHeight(run, height);
        Runnable tick = () -> run.body().update(run.app(), run.entity(), run.options());
        tick.run();
        run.body().getVelocity().zero();
        run.body().teleport(at);
        setup.accept(run);
        for (int i = 0; i < 10; i++) tick.run();
        return new Hanging(run, world, tick);
    }

    /** The body height, standing or moving. */
    private static void setHeight(Run run, double height) {
        run.gait().getStationary().setBodyHeight(height);
        run.gait().getMoving().setBodyHeight(height);
    }

    @Test
    @DisplayName("a tiny turn across 45 degrees switches the alternative scan axes and finds a foothold the old scan missed")
    void aTinyTurnAcrossFortyFiveDegreesRescans() {
        // a column of blocks at x = 1 topped at y = 10; the body at (0.7, 10.8) is tilted 45 degrees about z, so its
        // scan runs down and to the right (+x, -y) from the body centre and passes over the column
        World world = new Room((x, y, z) -> x == 1 && y >= 7 && y <= 9 && z >= -5 && z <= 5).world();
        // a leg rest 0.6 heights below the body: the scan then starts at the body centre, so turning barely moves it
        Hanging hanging = hang(world, new Vector(0.0, -.15, 0.0), .25, new Vector(.7, 10.8, .5), run -> {
            run.gait().setScanOrientation(OrientationMode.SpiderOrientation);
            run.body().getOrientation().rotationZ((float) (Math.PI / 4 - 9e-7));
        });
        Leg leg = hanging.leg();
        // just short of 45 degrees the alternative rays shift along x and z and miss as well
        assertNull(leg.getGroundTarget(), "Setup: a foothold before the turn");

        Vector[] before = new Vector[3];
        // turning just past 45 degrees shifts them along y instead: the one starting lower reaches the column's side
        assertTrue(hanging.rescansAfter(run -> {
            before[0] = leg.getLookAheadPosition().clone();
            before[1] = leg.getSurfaceUp().clone();
            before[2] = leg.getScanLine().getPoint1().clone();
            run.body().getOrientation().rotationZ((float) (Math.PI / 4 + 9e-7));
        }));
        assertEquals(before[0], leg.getLookAheadPosition(), "Setup: the look-ahead moved");
        assertNotEquals(before[1], leg.getSurfaceUp(), "Setup: the surface up did not move");
        assertEquals(before[2], leg.getScanLine().getPoint1(), "Setup: the scan start moved");
        assertNotNull(leg.getGroundTarget(), "The new scan missed the column");
        assertEquals(1.0, leg.getGroundTarget().getPosition().getX(), 1e-9, "Not on the column's side: " + leg.getGroundTarget().getPosition());
    }

    @Test
    @DisplayName("a new body height over an unchanged rest position rescans")
    void aNewBodyHeightOverTheSameRestRescans() {
        // height 1.25 with the leg's planned rest at y = 0, then 1.5 with it at y = 0.25: the rest stays 1.25 below the body
        Hanging hanging = hang(new Room((x, y, z) -> false).world(), new Vector(1.0, 0.0, 1.0), 1.25, new Vector(.5, 10.0, .5), run -> { });
        Leg leg = hanging.leg();
        Vector[] before = new Vector[2];
        assertTrue(hanging.rescansAfter(run -> {
            before[0] = leg.getLookAheadPosition().clone();
            before[1] = leg.getScanLine().getPoint1().clone();
            setHeight(run, 1.5);
            leg.getLegPlan().getRestPosition().setY(.25);
        }));
        assertEquals(before[0], leg.getLookAheadPosition(), "Setup: the look-ahead moved");
        assertNotEquals(before[1], leg.getScanLine().getPoint1());
    }

    @Test
    @DisplayName("a scan end drifting more than its start and look-ahead rescans")
    void aScanEndDriftingFurtherRescans() {
        // the scan line runs from 1.6 heights above the rest to 0.9 below it: lowering the height by 1.125e-6 while the
        // planned rest drops by only 0.225e-6 raises the rest 0.9e-6, lowers the start 0.9e-6 and raises the end 1.9e-6
        Hanging hanging = hang(new Room((x, y, z) -> false).world(), new Vector(0.0, .3, 0.0), .3, new Vector(.5, 10.0, .5), run -> { });
        Leg leg = hanging.leg();
        Vector[] before = new Vector[3];
        assertTrue(hanging.rescansAfter(run -> {
            before[0] = leg.getLookAheadPosition().clone();
            before[1] = leg.getScanLine().getPoint1().clone();
            before[2] = leg.getScanLine().getPoint2().clone();
            setHeight(run, .3 - 1.125e-6);
            leg.getLegPlan().getRestPosition().setY(.3 - .225e-6);
        }));
        assertEquals(before[0], leg.getLookAheadPosition(), "Setup: the look-ahead moved");
        assertEquals(before[1], leg.getScanLine().getPoint1(), "Setup: the scan start moved");
        assertNotEquals(before[2], leg.getScanLine().getPoint2());
    }

    @Test
    @DisplayName("a comfort zone end drifting more than the scan line rescans")
    void aComfortEndDriftingFurtherRescans() {
        // walking along a velocity pointing down and forward, the look-ahead sits the trigger radius along it from the rest
        // position; nudging the height (-0.64e-6), trigger radius (+0.42e-6) and planned rest (-0.116e-6) together moves
        // every scan key by under 1e-6 but the comfort zone's end by 1.1e-6
        Hanging hanging = hang(new Room((x, y, z) -> false).world(), new Vector(0.0, .3, 0.0), .3, new Vector(.5, 10.0, .5), run -> {
            run.gait().setLegLookAheadFraction(1.0);
            run.gait().getStationary().setTriggerZoneRadius(.5);
            run.gait().getMoving().setTriggerZoneRadius(.5);
            run.body().setWalking(true);
            run.body().getVelocity().copy(new Vector(0.0, -1e-9, 1e-9));
        });
        Leg leg = hanging.leg();
        Vector[] before = new Vector[5];
        assertTrue(hanging.rescansAfter(run -> {
            before[0] = leg.getLookAheadPosition().clone();
            before[1] = leg.getScanLine().getPoint1().clone();
            before[2] = leg.getScanLine().getPoint2().clone();
            before[3] = leg.getComfortZone().getPoint1().clone();
            before[4] = leg.getComfortZone().getPoint2().clone();
            setHeight(run, .3 - .64e-6);
            run.gait().getStationary().setTriggerZoneRadius(.5 + .3e-6 * Math.sqrt(2.0));
            run.gait().getMoving().setTriggerZoneRadius(.5 + .3e-6 * Math.sqrt(2.0));
            leg.getLegPlan().getRestPosition().setY(.3 - .116e-6);
        }));
        assertEquals(before[0], leg.getLookAheadPosition(), "Setup: the look-ahead moved");
        assertEquals(before[1], leg.getScanLine().getPoint1(), "Setup: the scan start moved");
        assertEquals(before[2], leg.getScanLine().getPoint2(), "Setup: the scan end moved");
        assertEquals(before[3], leg.getComfortZone().getPoint1(), "Setup: the comfort zone start moved");
        assertNotEquals(before[4], leg.getComfortZone().getPoint2());
    }
}
