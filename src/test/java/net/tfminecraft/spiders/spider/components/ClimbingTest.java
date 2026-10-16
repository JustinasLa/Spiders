package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.OrientationMode;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real body, leg and locomotion code against a small voxel room. */
class ClimbingTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    private void climbRoom(double scale, boolean gallop) {
        climbRoom(scale, gallop, 0);
    }

    /** The wall-and-ceiling room turned so that the wall lies {@code degrees} clockwise from +z. */
    private void climbRoom(double scale, boolean gallop, int degrees) {
        double angle = Math.toRadians((double) degrees);
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        World world = new Room((x, y, z) -> {
            double px = x + .5;
            double pz = z + .5;
            double lx = px * cos - pz * sin;
            double lz = px * sin + pz * cos;
            return SimulationHarness.wallAndCeiling((int) Math.floor(lx), y, (int) Math.floor(lz));
        }).world();
        Vector facing = new Vector(sin, 0.0, cos);
        Vector start = new Vector(.5 * cos + 2.5 * sin, 0.0, -.5 * sin + 2.5 * cos);
        Run run = spawn(world, scale, gallop, start, true, new Quaternionf().rotationY((float) angle));

        boolean[] reachedWall = {false};
        Vector[] ceilingStart = {null};
        boolean[] fell = {false};
        StringBuilder trace = new StringBuilder();
        run.walkForward(2400, tick -> {
            Vector up = run.up();
            if (tick % 40 == 0) trace.append(run.describe(tick));
            if (up.dot(facing) < -.9) reachedWall[0] = true;
            if (reachedWall[0] && up.getY() < -.9 && ceilingStart[0] == null) ceilingStart[0] = run.body().getPosition().clone();
            Vector from = ceilingStart[0];
            if (from != null && up.getY() > -.5) fell[0] = true;
            // done once it has hung from the ceiling for five blocks
            return from != null && progress(run, facing, from) > 5;
        });
        String label = "scale=" + scale + " gallop=" + gallop + " degrees=" + degrees + "\n" + trace + run.describe(-1);
        assertTrue(reachedWall[0], "Never stood on the wall: " + label);
        assertTrue(ceilingStart[0] != null, "Never reached the ceiling: " + label);
        assertTrue(!fell[0], "Lost the ceiling: " + label);
        assertTrue(progress(run, facing, ceilingStart[0]) > 5, "Did not walk along the ceiling: " + label);
        assertTrue(run.body().getPosition().getY() > 8 - 2.5 * scale, "Hung too far below the ceiling: " + label);
        assertTrue(Run.groundedLegs(run.body()) >= 3, "Legs lost the ceiling: " + label);
    }

    private static double progress(Run run, Vector facing, Vector from) {
        return from.clone().subtract(run.body().getPosition()).dot(facing);
    }

    @Test
    @DisplayName("hunter walks up a wall and along the ceiling")
    void hunterWalksUpAWallAndAlongTheCeiling() {
        climbRoom(0.8, false);
    }

    @Test
    @DisplayName("hatchling walks up a wall and along the ceiling")
    void hatchlingWalksUpAWallAndAlongTheCeiling() {
        climbRoom(0.35, false);
    }

    @Test
    @DisplayName("broodmother walks up a wall and along the ceiling")
    void broodmotherWalksUpAWallAndAlongTheCeiling() {
        climbRoom(1.5, false);
    }

    @Test
    @DisplayName("smallest spider climbs")
    void smallestSpiderClimbs() {
        climbRoom(0.25, false);
    }

    @Test
    @DisplayName("largest spider climbs")
    void largestSpiderClimbs() {
        climbRoom(2.5, false);
    }

    @Test
    @DisplayName("galloping spiders climb too")
    void gallopingSpidersClimbToo() {
        for (double scale : List.of(0.35, 0.8, 1.5)) climbRoom(scale, true);
    }

    @Test
    @DisplayName("walls facing every direction are climbable")
    void wallsFacingEveryDirectionAreClimbable() {
        for (int degrees : List.of(90, 180, 270)) climbRoom(0.8, false, degrees);
    }

    /** Same placement as /spider spawn aimed at a face: oriented to the face, raised along its normal. */
    private Run spawnOnFace(World world, Vector hit, Vector normal, Vector look) {
        Quaternionf orientation = SpiderBody.orientationFrom(normal, look, new Vector(0.0, 0.0, 1.0));
        Run run = spawn(world, 0.8, false, new Vector(), true, orientation);
        run.body().teleport(
            hit.clone().add(normal.clone().multiply(run.gait().getStationary().getBodyHeight())));
        return run;
    }

    @Test
    @DisplayName("a spider spawned on a wall clings and climbs to the ceiling")
    void aSpiderSpawnedOnAWallClingsAndClimbsToTheCeiling() {
        // aimed horizontally at the wall's face (z = 10, facing -z)
        Run run = spawnOnFace(new Room(SimulationHarness::wallAndCeiling).world(), new Vector(.5, 3.0, 10.0), new Vector(0.0, 0.0, -1.0), new Vector(0.0, 0.0, 1.0));
        assertTrue(run.up().getZ() < -.99, "Not facing out of the wall: " + run.up());
        StringBuilder trace = new StringBuilder();
        boolean[] onCeiling = {false};
        run.walkForward(1200, tick -> {
            if (tick % 40 == 0) trace.append(run.describe(tick));
            if (run.up().getY() < -.9) onCeiling[0] = true;
            return onCeiling[0];
        });
        assertTrue(onCeiling[0], "Never reached the ceiling from the wall:\n" + trace + run.describe(-1));
    }

    @Test
    @DisplayName("a spider spawned on the ceiling hangs and walks along it")
    void aSpiderSpawnedOnTheCeilingHangsAndWalksAlongIt() {
        Run run = spawnOnFace(new Room(SimulationHarness::wallAndCeiling).world(), new Vector(.5, 8.0, 0.5), new Vector(0.0, -1.0, 0.0), new Vector(0.0, .3, -1.0));
        StringBuilder trace = new StringBuilder();
        run.walkForward(300, tick -> {
            if (tick % 40 == 0) trace.append(run.describe(tick));
            return false;
        });
        assertTrue(run.up().getY() < -.9, "Fell off the ceiling:\n" + trace + run.describe(-1));
        assertTrue(run.body().getPosition().getY() > 6.5, "Hung too low:\n" + trace + run.describe(-1));
        assertTrue(run.body().getPosition().getZ() < -5, "Did not walk along the ceiling:\n" + trace + run.describe(-1));
    }

    @Test
    @DisplayName("turning climbing off on the ceiling drops the spider back onto its feet")
    void turningClimbingOffOnTheCeilingDropsTheSpiderBackOntoItsFeet() {
        Run run = spawn(new Room(SimulationHarness::wallAndCeiling).world(), 0.8, false, new Vector(.5, 0.0, 2.5));
        Vector[] ceilingStart = {null};
        run.walkForward(2400, tick -> {
            if (ceilingStart[0] == null && run.up().getY() < -.9 && run.body().getPosition().getY() > 6) ceilingStart[0] = run.body().getPosition().clone();
            Vector it = ceilingStart[0];
            return it != null && it.getZ() - run.body().getPosition().getZ() > 3;
        });
        assertTrue(ceilingStart[0] != null, "Never reached the ceiling: " + run.describe(-1));

        run.gait().setGrip(false);
        run.gait().setScanOrientation(OrientationMode.YAxis);
        StringBuilder trace = new StringBuilder();
        for (int tick = 0; tick < 400; tick++) {
            run.app().update();
            if (tick % 20 == 0) trace.append(run.describe(tick));
        }
        assertTrue(run.body().getPosition().getY() < 2.0, "Still on the ceiling:\n" + trace + run.describe(-1));
        assertTrue(run.up().getY() > .9, "Did not land upright:\n" + trace + run.describe(-1));
        assertTrue(Run.groundedLegs(run.body()) >= 4, "Not standing on the floor:\n" + trace + run.describe(-1));
    }

    @Test
    @DisplayName("without climbing the spider stays upright at the wall")
    void withoutClimbingTheSpiderStaysUprightAtTheWall() {
        Run run = spawn(new Room(SimulationHarness::wallAndCeiling).world(), 0.8, false, new Vector(.5, 0.0, 2.5), false);
        run.walkForward(600, tick -> false);
        assertTrue(run.up().getY() > .9, "Tipped over: " + run.up());
    }

    @Test
    @DisplayName("climbing spider walks over a ledge and down the far face")
    void climbingSpiderWalksOverALedgeAndDownTheFarFace() {
        // a raised platform (top at y = 4) ending at z = 0, above the floor at y = -4
        World world = new Room((x, y, z) -> x >= -12 && x <= 12 && z >= -30 && z <= 30 && y >= -6 && y <= 10 && (y < -4 || (z < 0 && y < 4))).world();
        Run run = spawn(world, 0.8, false, new Vector(.5, 4.0, -6.5));
        boolean[] wentOverEdge = {false};
        double[] lowest = {Double.MAX_VALUE};
        StringBuilder trace = new StringBuilder();
        run.walkForward(1600, tick -> {
            if (tick % 40 == 0) trace.append(run.describe(tick));
            if (run.up().getZ() > .9) wentOverEdge[0] = true;
            lowest[0] = Math.min(lowest[0], run.body().getPosition().getY());
            return wentOverEdge[0] && run.body().getPosition().getY() < 1.0;
        });
        assertTrue(wentOverEdge[0], "Never wrapped onto the vertical face:\n" + trace);
        assertTrue(run.body().getPosition().getY() < 1.0, "Did not descend the face:\n" + trace);
        assertTrue(lowest[0] > -4.0, "Fell to the floor:\n" + trace);
        assertTrue(run.up().getZ() > .9, "Not clinging to the face:\n" + trace + run.describe(-1));
        double z = run.body().getPosition().getZ();
        assertTrue(z >= 0.0 && z <= 2.0, "Drifted away from the face:\n" + trace + run.describe(-1));
    }
}
