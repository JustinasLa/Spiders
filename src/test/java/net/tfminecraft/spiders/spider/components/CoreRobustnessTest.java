package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CoreRobustnessTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    /** Collects what the systems log, so a swallowed failure still fails the test. */
    private static List<LogRecord> capture(Run run) {
        List<LogRecord> records = new ArrayList<>();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                records.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        run.app().setLogger(logger);
        return records;
    }

    @Test
    @DisplayName("a waterlogged block counts as water")
    void aWaterloggedBlockCountsAsWater() {
        World world = mock(World.class);
        Block block = mock(Block.class);
        Waterlogged data = mock(Waterlogged.class);
        when(data.isWaterlogged()).thenReturn(true);
        when(block.getBlockData()).thenReturn(data);
        when(world.getBlockAt(any(Location.class))).thenReturn(block);
        assertTrue(SoundsAndParticles.isInWater(world, new Vector(0.5, 64.0, 0.5)));

        when(data.isWaterlogged()).thenReturn(false);
        assertFalse(SoundsAndParticles.isInWater(world, new Vector(0.5, 64.0, 0.5)));
        when(block.getBlockData()).thenReturn(mock(BlockData.class));
        assertFalse(SoundsAndParticles.isInWater(world, new Vector(0.5, 64.0, 0.5)));
    }

    @Test
    @DisplayName("facing no direction at all keeps the heading instead of breaking the spider")
    void facingNoDirectionKeepsTheHeading() {
        Run run = spawn(new Room((x, y, z) -> y < 0).world(), 1.0, false, new Vector(0.5, 0.0, 0.5), false);
        for (int tick = 0; tick < 20; tick++) run.app().update();
        Locomotion.rotateTowards(run.body(), new Vector(), run.gait());
        assertTrue(run.body().getRotationalVelocity().isFinite(), "Rotation: " + run.body().getRotationalVelocity());
    }

    @Test
    @DisplayName("a spider with an unpaired leg can still gallop")
    void aSpiderWithAnUnpairedLegCanStillGallop() {
        Run run = spawn(new Room((x, y, z) -> y < 0).world(), 1.0, true, new Vector(0.5, 0.0, 0.5), false);
        List<LegPlan> legs = run.options().getBodyPlan().getLegs();
        legs.remove(legs.size() - 1);
        List<LogRecord> errors = capture(run);
        Vector start = run.body().getPosition().clone();
        run.walkForward(200, tick -> false);
        assertTrue(errors.isEmpty(), "Systems failed: " + errors.stream().map(r -> String.valueOf(r.getThrown())).toList());
        assertTrue(run.body().getPosition().distance(start) > 3.0, "Did not move: " + run.body().getPosition());
        for (Leg leg : run.body().getLegs()) assertTrue(leg.getEndEffector().isZero() || Double.isFinite(leg.getEndEffector().length()));
    }

    @Test
    @DisplayName("a shove stops the spider at a wall instead of pushing it through")
    void aShoveStopsTheSpiderAtAWall() {
        // floor below y = 0, a wall from z = 4 on
        Run run = spawn(new Room((x, y, z) -> y < 0 || z >= 4).world(), 1.0, false, new Vector(0.5, 0.0, 1.5), false);
        for (int tick = 0; tick < 40; tick++) run.app().update();
        for (int hit = 0; hit < 10; hit++) {
            Vector point = run.body().getPosition().clone().add(new Vector(0.0, 0.0, -0.5));
            HitDetector.knockBack(run.body(), run.options(), new Vector(0.0, 0.0, 1.0), point, 4.0);
            for (int tick = 0; tick < 5; tick++) run.app().update();
        }
        for (int tick = 0; tick < 40; tick++) run.app().update();
        assertTrue(run.body().getPosition().getZ() < 4.0, "Pushed into the wall: " + run.body().getPosition());
    }
}
