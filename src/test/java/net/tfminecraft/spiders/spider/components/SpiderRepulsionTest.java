package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiderRepulsionTest {
    private final World world = mock(World.class);
    private final ECS app = new ECS();

    SpiderRepulsionTest() {
        SpiderRepulsion.setupSpiderRepulsion(app);
    }

    /** A spider whose feet rest {@code footprint} blocks out from its body. */
    private Locomotion spider(World world, Vector position, UUID uuid, double footprint) {
        BodyPlan plan = new BodyPlan();
        if (footprint > 0) {
            plan.setLegs(List.of(
                new LegPlan(new Vector(), new Vector(footprint * .5, -1.0, 0.0), List.of(), new Quaternionf()),
                new LegPlan(new Vector(), new Vector(0.0, -1.0, footprint), List.of(), new Quaternionf())));
        }
        Gait gait = Gait.defaultWalk();
        gait.setMaxSpeed(.2);
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getBodyPlan()).thenReturn(plan);
        when(options.getGait()).thenReturn(gait);
        Locomotion locomotion = new Locomotion();
        app.spawn(SpiderBody.create(world, position, new Quaternionf(), uuid), options, locomotion);
        return locomotion;
    }

    private Locomotion spider(Vector position, UUID uuid) {
        return spider(world, position, uuid, 2.0);
    }

    @Test
    @DisplayName("overlapping spiders push each other apart, harder the more they overlap")
    void overlappingSpidersPushEachOtherApart() {
        Locomotion a = spider(new Vector(0.0, 64.0, 0.0), UUID.randomUUID());
        Locomotion b = spider(new Vector(1.0, 64.0, 0.0), UUID.randomUUID());
        app.update();
        // separation 2 * 2 * 1.2 = 4.8, distance 1: overlap 1 - 1 / 4.8
        double expected = .2 * (1 - 1 / 4.8) * 3.0;
        assertEquals(-expected, a.getPushVelocity().getX(), 1e-9);
        assertEquals(expected, b.getPushVelocity().getX(), 1e-9);
        assertEquals(0.0, a.getPushVelocity().getY(), 1e-12);
    }

    @Test
    @DisplayName("spiders stacked in one column split along x, by their ids")
    void spidersStackedInOneColumnSplitAlongX() {
        UUID low = new UUID(0L, 1L);
        UUID high = new UUID(0L, 2L);
        Locomotion first = spider(new Vector(0.0, 64.0, 0.0), low);
        Locomotion second = spider(new Vector(0.0, 66.0, 0.0), high);
        app.update();
        assertTrue(first.getPushVelocity().getX() < 0 && second.getPushVelocity().getX() > 0);

        ECS reversed = new ECS();
        SpiderRepulsion.setupSpiderRepulsion(reversed);
        Locomotion third = new Locomotion();
        Locomotion fourth = new Locomotion();
        reversed.spawn(SpiderBody.create(world, new Vector(0.0, 64.0, 0.0), new Quaternionf(), high), options(), third);
        reversed.spawn(SpiderBody.create(world, new Vector(0.0, 66.0, 0.0), new Quaternionf(), low), options(), fourth);
        reversed.update();
        assertTrue(third.getPushVelocity().getX() > 0 && fourth.getPushVelocity().getX() < 0);
    }

    private static SpiderOptions options() {
        BodyPlan plan = new BodyPlan();
        plan.setLegs(List.of(new LegPlan(new Vector(), new Vector(2.0, -1.0, 0.0), List.of(), new Quaternionf())));
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getBodyPlan()).thenReturn(plan);
        when(options.getGait()).thenReturn(Gait.defaultWalk());
        return options;
    }

    @Test
    @DisplayName("spiders far apart, in different worlds or without legs leave each other be")
    void spidersFarApartOrElsewhereLeaveEachOtherBe() {
        Locomotion near = spider(new Vector(0.0, 64.0, 0.0), UUID.randomUUID());
        Locomotion far = spider(new Vector(10.0, 64.0, 0.0), UUID.randomUUID());
        Locomotion elsewhere = spider(mock(World.class), new Vector(0.0, 64.0, 0.0), UUID.randomUUID(), 2.0);
        app.update();
        assertNull(near.getPushVelocity());
        assertNull(far.getPushVelocity());
        assertNull(elsewhere.getPushVelocity());

        ECS legless = new ECS();
        SpiderRepulsion.setupSpiderRepulsion(legless);
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getBodyPlan()).thenReturn(new BodyPlan());
        Locomotion one = new Locomotion();
        Locomotion two = new Locomotion();
        legless.spawn(SpiderBody.create(world, new Vector(0.0, 64.0, 0.0)), options, one);
        legless.spawn(SpiderBody.create(world, new Vector(0.0, 64.0, 0.0)), options, two);
        legless.update();
        assertNull(one.getPushVelocity());
        assertNull(two.getPushVelocity());
    }
}
