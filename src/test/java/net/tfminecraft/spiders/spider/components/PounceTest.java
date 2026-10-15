package net.tfminecraft.spiders.spider.components;

import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import java.util.stream.DoubleStream;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PounceTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    /** A wide floor at y < 0. */
    private World field() {
        return new Room((x, y, z) -> x >= -40 && x <= 40 && z >= -40 && z <= 120 && y >= -3 && y <= -1).world();
    }

    private double planar(Vector a, Vector b) {
        return new Vector(a.getX() - b.getX(), 0.0, a.getZ() - b.getZ()).length();
    }

    private record Result(
        double maxHeight, boolean leapt, int ticks, String trace,
        Pounce.Outcome outcome, Double landedDistance
    ) {
    }

    private Result pounceAt(Run run, Supplier<Vector> target) {
        return pounceAt(run, target, 1200);
    }

    /** Runs until the pounce ends; returns what happened. */
    private Result pounceAt(Run run, Supplier<Vector> target, int maxTicks) {
        Pounce.Outcome[] outcome = {null};
        Double[] landedDistance = {null};
        Pounce pounce = new Pounce(target, (result, distance) -> {
            outcome[0] = result;
            landedDistance[0] = distance;
        });
        run.entity().addComponent(pounce);
        double restingHeight = run.body().getPosition().getY();
        double maxHeight = 0.0;
        boolean leapt = false;
        StringBuilder trace = new StringBuilder();
        int tick = 0;
        while (run.entity().query(Pounce.class) != null && tick < maxTicks) {
            run.app().update();
            Vector position = run.body().getPosition();
            assertTrue(DoubleStream.of(position.getX(), position.getY(), position.getZ()).allMatch(Double::isFinite));
            if (pounce.getPhase() == Pounce.Phase.AIRBORNE) leapt = true;
            maxHeight = Math.max(maxHeight, run.body().getPosition().getY() - restingHeight);
            if (tick % 10 == 0) trace.append(run.describe(tick).strip() + " phase=" + pounce.getPhase() + " vel=" + run.body().getVelocity() + "\n");
            tick++;
        }
        return new Result(maxHeight, leapt, tick, trace.toString(), outcome[0], landedDistance[0]);
    }

    @Test
    @DisplayName("hunter gallops at a target, leaps and lands on it")
    void hunterGallopsAtATargetLeapsAndLandsOnIt() {
        Run run = spawn(field(), 0.8, true, new Vector(.5, 0.0, .5), false);
        Vector target = new Vector(.5, 0.0, 20.5);
        Result result = pounceAt(run, () -> target);
        String label = result.trace() + run.describe(-1);
        assertTrue(result.leapt(), "Never leapt:\n" + label);
        assertNull(run.entity().query(Pounce.class), "Pounce did not finish:\n" + label);
        assertTrue(result.maxHeight() > .4, "The leap barely left the ground (" + result.maxHeight() + "):\n" + label);
        assertTrue(planar(run.body().getPosition(), target) < 2.0, "Landed too far from the target:\n" + label);
        assertEquals(Pounce.Outcome.LANDED, result.outcome(), label);
        // the landing is reported where it happened; it may have settled a little since
        assertTrue(result.landedDistance() < 2.0, "Reported landing distance " + result.landedDistance());
        assertTrue(run.up().getY() > .9, "Landed tipped over:\n" + label);
        assertTrue(Run.groundedLegs(run.body()) >= 4, "Not standing after landing:\n" + label);
    }

    @Test
    @DisplayName("every size leaps at a target")
    void everySizeLeapsAtATarget() {
        for (double scale : List.of(0.25, 0.35, 1.5, 2.5)) {
            Run run = spawn(field(), scale, true, new Vector(.5, 0.0, .5), false);
            Vector target = new Vector(.5, 0.0, 6.5 + 10 * scale);
            Result result = pounceAt(run, () -> target);
            String label = "scale=" + scale + "\n" + result.trace() + run.describe(-1);
            assertTrue(result.leapt(), "Never leapt: " + label);
            assertNull(run.entity().query(Pounce.class), "Pounce did not finish: " + label);
            assertTrue(planar(run.body().getPosition(), target) < 1.0 + 2 * scale, "Landed too far from the target: " + label);
            assertTrue(run.up().getY() > .9, "Landed tipped over: " + label);
        }
    }

    @Test
    @DisplayName("follows a moving target")
    void followsAMovingTarget() {
        Run run = spawn(field(), 0.8, true, new Vector(.5, 0.0, .5), false);
        int[] tick = {0};
        // walks away sideways at 2 blocks per second
        Result result = pounceAt(run, () -> new Vector(.5 + .1 * tick[0]++, 0.0, 15.5));
        Vector target = new Vector(.5 + .1 * tick[0], 0.0, 15.5);
        assertTrue(result.leapt(), result.trace());
        assertTrue(planar(run.body().getPosition(), target) < 3.0, "Lost the moving target:\n" + result.trace() + run.describe(-1));
    }

    @Test
    @DisplayName("gallop speed setting changes how fast it runs")
    void gallopSpeedSettingChangesHowFastItRuns() {
        List<Double> speeds = new ArrayList<>();
        for (double blocksPerSecond : List.of(2.0, 4.0, 8.0, 12.0, 16.0)) {
            Run run = spawn(field(), 0.8, true, new Vector(.5, 0.0, .5), false);
            run.gait().setSpeed(blocksPerSecond / 20);
            run.entity().addComponent(new Pounce(() -> new Vector(.5, 0.0, 110.5)));
            for (int i = 0; i < 40; i++) run.app().update(); // get up to speed
            Vector from = run.body().getPosition().clone();
            for (int i = 0; i < 100; i++) run.app().update();
            double achieved = planar(run.body().getPosition(), from) / 5.0;
            assertTrue(achieved > .75 * blocksPerSecond, "Set " + blocksPerSecond + " blocks/s, ran " + achieved);
            assertTrue(achieved < 1.1 * blocksPerSecond, "Set " + blocksPerSecond + " blocks/s, ran " + achieved);
            speeds.add(achieved);
        }
        assertEquals(speeds.stream().sorted().toList(), speeds, "Faster settings must run faster: " + speeds);
    }

    private double leapHeight(double lift) {
        Run run = spawn(field(), 0.8, true, new Vector(.5, 0.0, .5), false);
        run.options().getPounce().setLeapLift(lift);
        return pounceAt(run, () -> new Vector(.5, 0.0, 20.5)).maxHeight();
    }

    @Test
    @DisplayName("leap boost and lift are adjustable")
    void leapBoostAndLiftAreAdjustable() {
        double low = leapHeight(.3);
        double high = leapHeight(.7);
        assertTrue(high > low + .5, "Higher lift should leap higher: " + low + " vs " + high);
    }

    @Test
    @DisplayName("gives up when the target is gone")
    void givesUpWhenTheTargetIsGone() {
        Run run = spawn(field(), 0.8, true, new Vector(.5, 0.0, .5), false);
        boolean[] present = {true};
        Pounce.Outcome[] outcome = {null};
        run.entity().addComponent(new Pounce(() -> present[0] ? new Vector(.5, 0.0, 60.5) : null, (result, distance) -> outcome[0] = result));
        for (int i = 0; i < 20; i++) run.app().update();
        present[0] = false;
        run.app().update();
        assertNull(run.entity().query(Pounce.class));
        assertEquals(Pounce.Outcome.TARGET_LOST, outcome[0]);
    }

    @Test
    @DisplayName("gives up after the chase time limit")
    void givesUpAfterTheChaseTimeLimit() {
        Run run = spawn(field(), 0.8, true, new Vector(.5, 0.0, .5), false);
        run.options().getPounce().setGiveUpTicks(30);
        Pounce.Outcome[] outcome = {null};
        run.entity().addComponent(new Pounce(() -> new Vector(.5, 0.0, 110.5), (result, distance) -> outcome[0] = result));
        for (int i = 0; i < 32; i++) run.app().update();
        assertNull(run.entity().query(Pounce.class));
        assertEquals(Pounce.Outcome.GAVE_UP, outcome[0]);
    }

    @Test
    @DisplayName("a spider on the ceiling drops onto a target below")
    void aSpiderOnTheCeilingDropsOntoATargetBelow() {
        World world = new Room(SimulationHarness::wallAndCeiling).world();
        Run run = spawn(world, 0.8, true, new Vector(.5, 0.0, 2.5));
        // climb up the wall and onto the ceiling first
        run.walkForward(2400, tick -> run.up().getY() < -.9 && run.body().getPosition().getZ() < 7);
        assertTrue(run.up().getY() < -.9, "Setup: never reached the ceiling: " + run.describe(-1));
        Vector target = new Vector(run.body().getPosition().getX(), 0.0, run.body().getPosition().getZ() - 3);
        Result result = pounceAt(run, () -> target);
        String label = result.trace() + run.describe(-1);
        assertTrue(result.leapt(), "Never leapt:\n" + label);
        assertTrue(run.body().getPosition().getY() < 3, "Did not drop to the floor:\n" + label);
        assertTrue(planar(run.body().getPosition(), target) < 3.0, "Dropped too far from the target:\n" + label);
    }
}
