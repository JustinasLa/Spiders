package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;

/** Legs reuse their last ground scan and collision ray while nothing has moved. */
class LegScanCacheTest {
    private static final int SETTLE_TICKS = 100;

    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    /** A room's world that counts every ray cast into it. */
    private record Counted(World world, int[] rays) {
        static Counted of(Room room) {
            World world = room.world();
            int[] rays = {0};
            doAnswer(call -> {
                rays[0]++;
                return room.rayTrace(call.<Location>getArgument(0).toVector(), call.getArgument(1), call.<Double>getArgument(2));
            }).when(world).rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true));
            return new Counted(world, rays);
        }

        int take() {
            int count = rays[0];
            rays[0] = 0;
            return count;
        }
    }

    private void standingCastsFewerRays(boolean climbing) {
        Counted counted = Counted.of(new Room((x, y, z) -> y < 0));
        Run run = spawn(counted.world(), 1.0, false, new Vector(0.5, 0.0, 0.5), climbing);
        for (int tick = 0; tick < SETTLE_TICKS; tick++) run.app().update();

        counted.take();
        for (int tick = 0; tick < 40; tick++) run.app().update();
        double standing = counted.take() / 40.0;
        run.walkForward(40);
        double walking = counted.take() / 40.0;

        // before the cache a standing spider cast about as many rays as a walking one (18 vs 19 per tick)
        String label = "climbing=" + climbing + " standing=" + standing + " walking=" + walking + " rays/tick";
        assertTrue(standing * 2 < walking, label);
        assertTrue(standing / run.body().getLegs().size() < 1.0, label);
    }

    @Test
    @DisplayName("a settled standing spider casts far fewer rays than a walking one")
    void aSettledStandingSpiderCastsFarFewerRays() {
        standingCastsFewerRays(false);
        standingCastsFewerRays(true);
    }

    @Test
    @DisplayName("a walking spider covers the same ground as before the cache")
    void aWalkingSpiderCoversTheSameGround() {
        // distances measured on main before legs cached their rays
        for (boolean gallop : new boolean[]{false, true}) {
            Run run = spawn(new Room((x, y, z) -> y < 0).world(), 1.0, gallop, new Vector(0.5, 0.0, 0.5), false);
            for (int tick = 0; tick < SETTLE_TICKS + 40; tick++) run.app().update();
            Vector start = run.body().getPosition().clone();
            run.walkForward(200);
            double expected = gallop ? 46.0911 : 22.1943;
            assertEquals(expected, run.body().getPosition().distance(start), 1e-3, "gallop=" + gallop);
        }
    }

    @Test
    @DisplayName("a block appearing under an idle foot is noticed within the refresh window")
    void aBlockAppearingUnderAnIdleFootIsNoticed() {
        // every phase of the refresh window, so a scan cached the tick before the block appears is covered too
        for (int delay = 0; delay < Leg.SCAN_REFRESH_TICKS; delay++) {
            // the block's x and z once it appears
            int[][] block = {null};
            Room room = new Room((x, y, z) -> y < 0 || (block[0] != null && x == block[0][0] && y == 0 && z == block[0][1]));
            Run run = spawn(room.world(), 1.0, false, new Vector(0.5, 0.0, 0.5), false);
            for (int tick = 0; tick < SETTLE_TICKS + delay; tick++) run.app().update();

            Leg leg = run.body().getLegs().get(0);
            Vector foot = leg.getGroundTarget().getPosition().clone();
            assertEquals(0.0, foot.getY(), 1e-6, "Foot not on the floor: " + foot);
            block[0] = new int[]{(int) Math.floor(foot.getX()), (int) Math.floor(foot.getZ())};

            int noticed = -1;
            for (int tick = 1; tick <= Leg.SCAN_REFRESH_TICKS && noticed < 0; tick++) {
                run.app().update();
                if (leg.getGroundTarget() != null && leg.getGroundTarget().getPosition().getY() > 1.0 - 1e-6) noticed = tick;
            }
            assertTrue(noticed > 0, "delay=" + delay + " still targets " + leg.getGroundTarget().getPosition());
        }
    }
}
