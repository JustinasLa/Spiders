package net.tfminecraft.spiders;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.HitDetector;
import net.tfminecraft.spiders.spider.components.Locomotion;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.Mountable;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.SoundsAndParticles;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.rendering.SpiderRenderer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.CinderweaverSize;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AppStateTest {
    private final World world = mock(World.class);
    private AppTestSupport app;
    private CommandSender staff;

    @BeforeEach
    void setup() {
        app = new AppTestSupport();
        staff = mock(CommandSender.class);
        when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
    }

    @AfterEach
    void restore() {
        app.close();
    }

    private ECSEntity spiderAt(World world, double x) {
        return AppState.getEcs().spawn(SpiderBody.create(world, new Vector(x, 64.0, 0.5)));
    }

    @Test
    @DisplayName("the Brood Guide marker is hidden until staff turn it on")
    void theGuideMarkerIsHiddenByDefault() {
        MiscellaneousOptions options = new MiscellaneousOptions();
        assertFalse(options.getShowLaser());
        options.setShowLaser(true);
        assertTrue(options.getShowLaser());
        assertNotNull(AppState.getMiscOptions());
    }

    @Test
    @DisplayName("the debug view toggles per player and is only shown to online staff")
    void theDebugViewTogglesPerPlayer() {
        Player watching = app.staff(new Location(world, 0.0, 64.0, 0.0));
        Player demoted = app.staff(new Location(world, 0.0, 64.0, 0.0));
        Player gone = app.staff(new Location(world, 0.0, 64.0, 0.0));
        app.staff(new Location(world, 0.0, 64.0, 0.0));

        assertTrue(AppState.toggleDebugView(watching));
        assertTrue(AppState.toggleDebugView(demoted));
        assertTrue(AppState.toggleDebugView(gone));
        when(demoted.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
        app.online.remove(gone);

        assertEquals(List.of(watching), AppState.getDebugViewers());

        assertFalse(AppState.toggleDebugView(watching));
        assertTrue(AppState.getDebugViewers().isEmpty());
    }

    @Test
    @DisplayName("staff summon a spider standing on the spot, with every part it needs")
    void staffSummonASpiderWithEveryPart() {
        SpiderOptions options = new SpiderOptions();
        Vector floor = new Vector(3.5, 64.0, -2.5);

        ECSEntity entity = AppState.createSpider(staff, world, floor, options);

        assertNotNull(entity);
        SpiderBody body = entity.query(SpiderBody.class);
        double height = options.getWalkGait().getStationary().getBodyHeight();
        assertEquals(floor.getX(), body.getPosition().getX(), 1e-9);
        assertEquals(floor.getY() + height, body.getPosition().getY(), 1e-9);
        assertEquals(world, body.getWorld());
        assertEquals(options, entity.query(SpiderOptions.class));
        for (Class<?> part : List.of(Locomotion.class, HitDetector.class, Cloak.class, SoundsAndParticles.class,
            Mountable.class, PointDetector.class, SpiderRenderer.class)) {
            assertTrue(entity.getComponents().stream().anyMatch(part::isInstance), part.getSimpleName());
        }
        assertNull(entity.query(Mimic.class));
        assertEquals(1, AppState.spiderCount());
    }

    @Test
    @DisplayName("a spider summoned onto a ceiling hangs below it")
    void aSpiderOnACeilingHangsBelowIt() {
        SpiderOptions options = new SpiderOptions();
        Quaternionf upsideDown = new Quaternionf().rotationX((float) Math.PI);

        ECSEntity entity = AppState.createSpider(staff, world, new Vector(0.5, 70.0, 0.5), options, upsideDown);

        double height = options.getWalkGait().getStationary().getBodyHeight();
        assertEquals(70.0 - height, entity.query(SpiderBody.class).getPosition().getY(), 1e-6);
    }

    @Test
    @DisplayName("a scale outside the allowed range is refused with the range")
    void aScaleOutsideTheRangeIsRefused() {
        for (double scale : new double[]{Double.NaN, Double.POSITIVE_INFINITY, CinderweaverSize.MIN_SCALE - .01, CinderweaverSize.MAX_SCALE + .01}) {
            SpiderOptions options = new SpiderOptions();
            options.getBodyPlan().setScale(scale);
            assertNull(AppState.createSpider(staff, world, new Vector(), options), String.valueOf(scale));
        }
        verify(staff, org.mockito.Mockito.times(4)).sendPlainMessage(contains("between " + CinderweaverSize.MIN_SCALE + " and " + CinderweaverSize.MAX_SCALE));
        assertEquals(0, AppState.spiderCount());
    }

    @Test
    @DisplayName("no more than the limit can be summoned; removing one frees a place")
    void noMoreThanTheLimitCanBeSummoned() {
        for (int i = 0; i < AppState.MAX_SPIDERS; i++) {
            assertNotNull(AppState.createSpider(staff, world, new Vector(i, 64.0, 0.0), new SpiderOptions()));
        }
        verify(staff, never()).sendPlainMessage(anyString());

        assertNull(AppState.createSpider(staff, world, new Vector(), new SpiderOptions()));
        verify(staff).sendPlainMessage(contains("limit is " + AppState.MAX_SPIDERS));
        assertNull(AppState.spawnMimic(world, new Vector(), new Vector(0.0, 0.0, 1.0), UUID.randomUUID()), "mimics count too");

        AppState.liveSpiders().getFirst().first().remove();
        assertEquals(AppState.MAX_SPIDERS - 1, AppState.spiderCount());
        assertNotNull(AppState.createSpider(staff, world, new Vector(), new SpiderOptions()));
    }

    @Test
    @DisplayName("a woken mimic chest rises as a spider facing the way the chest faced")
    void aWokenMimicFacesTheChestsWay() {
        UUID waker = UUID.randomUUID();
        Vector floor = new Vector(10.5, 64.0, 10.5);

        ECSEntity entity = AppState.spawnMimic(world, floor, new Vector(1.0, 0.0, 0.0), waker);

        assertNotNull(entity);
        assertNotNull(entity.query(Mimic.class));
        SpiderBody body = entity.query(SpiderBody.class);
        assertEquals(floor.getX(), body.getPosition().getX(), 1e-9);
        assertTrue(body.getPosition().getY() >= floor.getY());
        Vector forward = new Vector(0.0, 0.0, 1.0).rotateAroundY(0.0);
        Vector facing = net.tfminecraft.spiders.utilities.maths.Maths.rotate(forward, body.getOrientation());
        assertEquals(1.0, facing.getX(), 1e-6);
        assertEquals(0.0, facing.getZ(), 1e-6);
    }

    @Test
    @DisplayName("spiders being removed are no longer found")
    void spidersBeingRemovedAreNoLongerFound() {
        ECSEntity kept = spiderAt(world, 0.5);
        ECSEntity removed = spiderAt(world, 1.5);
        UUID keptId = kept.query(SpiderBody.class).getUuid();
        UUID removedId = removed.query(SpiderBody.class).getUuid();
        removed.remove();

        assertEquals(1, AppState.liveSpiders().size());
        assertEquals(kept, AppState.findSpiderByUUID(keptId));
        assertNull(AppState.findSpiderByUUID(removedId));
        assertNull(AppState.findSpiderByUUID(UUID.randomUUID()));
    }

    @Test
    @DisplayName("the nearest spider is found in the player's world, within reach")
    void theNearestSpiderIsFoundInTheWorldWithinReach() {
        Player player = app.staff(new Location(world, 0.5, 64.0, 0.5));
        assertNull(AppState.findNearestSpider(player), "no spiders yet");

        spiderAt(mock(World.class), 0.5);
        ECSEntity near = spiderAt(world, 10.5);
        spiderAt(world, 30.5);
        ECSEntity nearest = spiderAt(world, 5.5);
        spiderAt(world, 20.5);

        assertEquals(nearest, AppState.findNearestSpider(player));
        assertEquals(nearest, AppState.findNearestSpider(new Location(world, 0.5, 64.0, 0.5), 5.0));
        assertNull(AppState.findNearestSpider(new Location(world, 0.5, 64.0, 0.5), 4.9));
        assertEquals(near, AppState.findNearestSpider(new Location(world, 11.5, 64.0, 0.5)));
    }
}
