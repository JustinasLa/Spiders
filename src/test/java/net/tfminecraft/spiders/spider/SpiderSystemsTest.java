package net.tfminecraft.spiders.spider;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.HitDetector;
import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.Mountable;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.SoundsAndParticles;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiderSystemsTest {
    @Test
    @DisplayName("with the spider systems set up, a summoned spider grows its legs and falls through open air")
    void aSpiderGrowsLegsAndFalls() {
        try (AppTestSupport ignored = new AppTestSupport()) {
            World world = mock(World.class);
            Block air = mock(Block.class);
            when(air.isPassable()).thenReturn(true);
            when(world.getBlockAt(any(Location.class))).thenReturn(air);
            when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
            when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(null);
            when(world.getPlayers()).thenReturn(List.of());

            ECS app = new ECS();
            app.setLogger(mock(Logger.class));
            SpiderSystems.setupSpider(app);
            SpiderOptions options = Cinderweaver.cinderweaver();
            SpiderBody body = SpiderBody.create(world, new Vector(0.5, 200.0, 0.5));
            app.spawn(body, options, new Locomotion(), new HitDetector(), new Cloak(), new SoundsAndParticles(),
                new Mountable(), new PointDetector());

            for (int tick = 0; tick < 5; tick++) app.update();

            assertEquals(options.getBodyPlan().getLegs().size(), body.getLegs().size());
            assertTrue(body.getPosition().getY() < 200.0, "falls");
            assertTrue(body.getLegs().stream().noneMatch(leg -> leg.isGrounded()), "nothing to stand on");
        }
    }
}
