package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PointDetectorTest {
    private final World world = mock(World.class);

    private static Leg leg(Vector foot) {
        Leg leg = mock(Leg.class);
        when(leg.getEndEffector()).thenReturn(foot);
        return leg;
    }

    private Player player(World world, Vector eye, Vector look) {
        Player player = mock(Player.class);
        when(player.getWorld()).thenReturn(world);
        when(player.getEyeLocation()).thenAnswer(call -> eye.toLocation(world));
        when(player.getLocation()).thenAnswer(call -> eye.toLocation(world).setDirection(look));
        return player;
    }

    @Test
    @DisplayName("players pointing at a foot select that leg, each tick afresh")
    void playersPointingAtAFootSelectThatLeg() {
        ECS app = new ECS();
        PointDetector.setupPointDetector(app);
        SpiderBody spider = SpiderBody.create(world, new Vector(0.0, 64.0, 0.0));
        Leg left = leg(new Vector(-2.0, 63.0, 5.0));
        Leg right = leg(new Vector(2.0, 63.0, 5.0));
        spider.setLegs(List.of(left, right));
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getWalkGait()).thenReturn(Gait.defaultWalk());
        PointDetector detector = new PointDetector();
        app.spawn(spider, detector, options);

        Vector eye = new Vector(0.0, 64.0, 0.0);
        Player atRight = player(world, eye, new Vector(2.0, -1.0, 5.0));
        Player atNothing = player(world, eye, new Vector(0.0, 1.0, 0.0));
        Player elsewhere = player(mock(World.class), eye, new Vector(2.0, -1.0, 5.0));
        detector.setCheckPlayers(Set.of(atRight, atNothing, elsewhere));
        assertEquals(3, detector.getCheckPlayers().size());

        app.update();
        assertEquals(Map.of(atRight, right), detector.getSelectedLeg());

        when(atRight.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0).setDirection(new Vector(-2.0, -1.0, 5.0)));
        app.update();
        assertEquals(Map.of(atRight, left), detector.getSelectedLeg());

        detector.setCheckPlayers(Set.of());
        app.update();
        assertTrue(detector.getSelectedLeg().isEmpty());
    }
}
