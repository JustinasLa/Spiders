package net.tfminecraft.spiders.spider;

import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiderActivityTest {
    private final World world = mock(World.class);
    private final List<Player> players = new ArrayList<>();

    SpiderActivityTest() {
        when(world.getPlayers()).thenReturn(players);
        when(world.getName()).thenReturn("world");
    }

    private void playerAt(double x, double y, double z) {
        Player player = mock(Player.class);
        when(player.getLocation()).thenReturn(new Location(world, x, y, z));
        players.add(player);
    }

    private ECSEntity spider(ECS app) {
        return app.spawn(SpiderBody.create(world, new Vector(0.5, 64.0, 0.5)));
    }

    @Test
    @DisplayName("a spider sleeps in an unloaded chunk even with a player beside it")
    void sleepsInAnUnloadedChunk() {
        ECS app = new ECS();
        ECSEntity spider = spider(app);
        playerAt(2.0, 64.0, 2.0);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);

        SpiderActivity.updateSleep(app);

        assertTrue(spider.isAsleep());
    }

    @Test
    @DisplayName("a spider sleeps while nobody is near and wakes when someone comes")
    void sleepsWhileNobodyIsNear() {
        ECS app = new ECS();
        ECSEntity spider = spider(app);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        playerAt(SpiderActivity.ACTIVE_RANGE + 10.0, 64.0, 0.5);

        SpiderActivity.updateSleep(app);
        assertTrue(spider.isAsleep());

        playerAt(10.0, 64.0, 0.5);
        SpiderActivity.updateSleep(app);
        assertFalse(spider.isAsleep());
    }

    @Test
    @DisplayName("a spider whose simulation went non-finite is removed")
    void aBrokenSpiderIsRemoved() {
        ECS app = new ECS();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        SpiderActivity.setupRemoveBroken(app, logger);
        ECSEntity healthy = spider(app);
        ECSEntity broken = spider(app);
        broken.query(SpiderBody.class).getVelocity().setX(Double.NaN);

        app.update();

        assertTrue(app.getEntities().contains(healthy));
        assertFalse(app.getEntities().contains(broken));
    }

    @Test
    @DisplayName("a spider whose position went non-finite stays awake so it can be cleared")
    void aBrokenSpiderStaysAwake() {
        ECS app = new ECS();
        ECSEntity spider = spider(app);
        spider.query(SpiderBody.class).getPosition().setX(Double.NaN);

        SpiderActivity.updateSleep(app);

        assertFalse(spider.isAsleep());
    }

    @Test
    @DisplayName("an awake mimic keeps simulating with nobody near")
    void anAwakeMimicKeepsSimulating() {
        ECS app = new ECS();
        ECSEntity spider = spider(app);
        spider.addComponent(mock(Mimic.class));
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);

        SpiderActivity.updateSleep(app);

        assertFalse(spider.isAsleep());
    }

    @Test
    @DisplayName("a spider is removed when any part of its position, motion or orientation breaks")
    void anyNonFiniteStateRemovesTheSpider() {
        ECS app = new ECS();
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        SpiderActivity.setupRemoveBroken(app, logger);
        ECSEntity position = spider(app);
        position.query(SpiderBody.class).getPosition().setY(Double.POSITIVE_INFINITY);
        ECSEntity motion = spider(app);
        motion.query(SpiderBody.class).getVelocity().setZ(Double.NaN);
        ECSEntity orientation = spider(app);
        orientation.query(SpiderBody.class).getOrientation().x = Float.NaN;
        ECSEntity healthy = spider(app);

        app.update();

        assertEquals(List.of(healthy), app.getEntities());
    }
}
