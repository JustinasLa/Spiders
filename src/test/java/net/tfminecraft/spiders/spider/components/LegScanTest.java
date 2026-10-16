package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;

import java.util.List;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mockingDetails;

/**
 * Legs that aren't stepping (mid-leap or disabled) only rescan the ground every few ticks.
 * A still leg may also reuse its last scan (see {@code LegScanCacheTest}), so the spider walks where a scan must cast rays.
 */
class LegScanTest {
    /** Mirrors {@code Leg.IDLE_SCAN_TICKS}. */
    private static final int IDLE_SCAN_TICKS = 3;
    /** A ground scan is the main ray plus up to 8 alternatives. */
    private static final int RAYS_PER_SCAN = 9;

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
        return new Room((x, y, z) -> x >= -40 && x <= 40 && z >= -40 && z <= 40 && y >= -3 && y <= -1).world();
    }

    /** One tick; returns the {@code rayTraceBlocks} calls it made. */
    private List<Invocation> tick(Run run, World world) {
        clearInvocations(world);
        run.app().update();
        return mockingDetails(world).getInvocations().stream()
            .filter(it -> it.getMethod().getName().equals("rayTraceBlocks"))
            .toList();
    }

    /** One tick walking forward, so every leg's scan inputs change and a scan always casts its rays. */
    private List<Invocation> walkTick(Run run, World world) {
        run.locomotion().walk(run.body().forwardDirection().multiply(run.gait().getMaxSpeed()));
        return tick(run, world);
    }

    /** Whether {@code leg} cast its main scan ray (from the start of its scan line) this tick. */
    private boolean scanned(Leg leg, List<Invocation> rays) {
        Vector start = leg.getScanLine().getPoint1();
        for (Invocation ray : rays) {
            Location from = ray.getArgument(0);
            if (from.toVector().distanceSquared(start) < 1e-12) return true;
        }
        return false;
    }

    /** Rays as long as a leg scan: the main and alternative ground rays of every leg. */
    private long scanRays(Run run, List<Invocation> rays) {
        double length = run.body().getLegs().get(0).getScanLine().vector().length();
        return rays.stream().filter(it -> Math.abs(it.<Double>getArgument(2) - length) < 1e-9).count();
    }

    @Test
    @DisplayName("mid-leap legs scan every few ticks and all rescan once landed")
    void midLeapLegsScanEveryFewTicksAndAllRescanOnceLanded() {
        World world = field();
        Run run = spawn(world, 0.8, true, new Vector(.5, 0.0, .5), false);
        for (int i = 0; i < 40; i++) run.app().update(); // settle
        List<Leg> legs = run.body().getLegs();
        walkTick(run, world); // the body only starts moving the tick after it is told to walk
        List<Invocation> walking = walkTick(run, world);
        for (Leg leg : legs) assertTrue(scanned(leg, walking), "A walking leg must scan every tick");

        run.body().leap(new Vector(0.0, .5, .2), 40);
        int[] scans = new int[legs.size()];
        int leapTicks = 0;
        long leapScanRays = 0;
        List<Invocation> rays = tick(run, world);
        while (run.body().getLeapTicks() > 0 && leapTicks < 200) {
            // legs updated with the leap still on
            for (int i = 0; i < legs.size(); i++) if (scanned(legs.get(i), rays)) scans[i]++;
            leapScanRays += scanRays(run, rays);
            leapTicks++;
            rays = tick(run, world);
        }
        assertTrue(leapTicks >= 6, "Setup: the leap was too short (" + leapTicks + " ticks)");

        int maxScans = (leapTicks + IDLE_SCAN_TICKS - 1) / IDLE_SCAN_TICKS;
        for (int i = 0; i < legs.size(); i++) {
            assertTrue(scans[i] <= maxScans, "Leg " + i + " scanned " + scans[i] + " of " + leapTicks + " leap ticks");
            assertTrue(scans[i] >= leapTicks / IDLE_SCAN_TICKS, "Leg " + i + " stopped refreshing mid-leap: " + scans[i]);
        }
        // scanning every tick cast up to RAYS_PER_SCAN rays per leg per tick; now at most every third tick
        long bound = (long) legs.size() * maxScans * RAYS_PER_SCAN;
        assertTrue(leapScanRays <= bound, "Scan rays mid-leap: " + leapScanRays + " > " + bound + " over " + leapTicks + " ticks");

        // the first tick the legs see the leap over, every one of them steps from a fresh scan
        List<Invocation> landed = tick(run, world);
        for (int i = 0; i < legs.size(); i++) assertTrue(scanned(legs.get(i), landed), "Leg " + i + " did not rescan after landing");
    }

    @Test
    @DisplayName("a disabled leg scans every few ticks and keeps its foot raised")
    void aDisabledLegScansEveryFewTicksAndKeepsItsFootRaised() {
        World world = field();
        Run run = spawn(world, 0.8, true, new Vector(.5, 0.0, .5), false);
        for (int i = 0; i < 40; i++) run.app().update(); // settle
        Leg disabled = run.body().getLegs().get(0);
        Leg enabled = run.body().getLegs().get(1);
        disabled.setDisabled(true);

        int ticks = 60;
        int disabledScans = 0;
        int enabledScans = 0;
        for (int i = 0; i < ticks; i++) {
            List<Invocation> rays = walkTick(run, world);
            if (scanned(disabled, rays)) disabledScans++;
            if (scanned(enabled, rays)) enabledScans++;
        }
        assertEquals(ticks, enabledScans, "An enabled leg must scan every tick");
        assertEquals(ticks / IDLE_SCAN_TICKS, disabledScans, "Disabled leg scans");

        // the raised foot still clears the floor (top at y = 0) and has a ground target
        assertTrue(disabled.getGroundTarget() != null, "Disabled leg lost its ground target");
        assertTrue(disabled.getEndEffector().getY() > .05, "Raised foot dropped to " + disabled.getEndEffector());

        // re-enabled, it scans straight away
        disabled.setDisabled(false);
        assertTrue(scanned(disabled, walkTick(run, world)), "Re-enabled leg did not rescan");
    }
}
