package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderLegIK;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.PaletteEntry;
import net.tfminecraft.spiders.spider.configuration.SpiderDebugOptions;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.testing.RenderTestSupport;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiderRendererTest {
    private RenderTestSupport server;
    private World world;
    private final ECS app = new ECS();
    private final List<LogRecord> errors = new ArrayList<>();
    private final SpiderOptions options = mock(SpiderOptions.class);
    private final BodyPlan plan = mock(BodyPlan.class);
    private final DisplayModel bodyModel = new DisplayModel(new ArrayList<>());
    private final SpiderRenderer renderer = new SpiderRenderer();
    private SpiderBody spider;
    private final List<Player> debugViewers = new ArrayList<>();

    @BeforeEach
    void setup() {
        server = new RenderTestSupport();
        world = server.world;
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        logger.addHandler(new Handler() {
            @Override
            public void publish(LogRecord record) {
                errors.add(record);
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        });
        app.setLogger(logger);

        when(options.getBodyPlan()).thenReturn(plan);
        when(options.getDebug()).thenReturn(new SpiderDebugOptions());
        when(plan.getBodyModel()).thenReturn(bodyModel);
        when(plan.getLegs()).thenReturn(List.of());
        when(plan.getScale()).thenReturn(1.0);
        spider = SpiderBody.create(world, new Vector(0.0, 64.0, 0.0));
        app.spawn(spider, mock(Cloak.class), new PointDetector(), renderer, options);
        SpiderRenderer.setupRenderer(app);
    }

    @AfterEach
    void close() {
        for (Player viewer : debugViewers) AppState.toggleDebugView(viewer);
        RenderSpiderEntities.endFrame();
        RenderSpiderEntities.endFrame();
        server.close();
    }

    private Runnable interval(long period) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(server.scheduler).runTaskTimer(eq(server.plugin), task.capture(), eq(0L), eq(period));
        return task.getValue();
    }

    private static BlockDisplayModelPiece piece(String... tags) {
        return new BlockDisplayModelPiece(RenderTestSupport.blockData(Material.STONE), new org.joml.Matrix4f(), null, new ArrayList<>(List.of(tags)));
    }

    private void assertBlinks(long period, String tag, List<PaletteEntry> palette) {
        BlockDisplayModelPiece tagged = piece(tag);
        BlockDisplayModelPiece plain = piece("other");
        BlockData plainBlock = plain.getBlock();
        bodyModel.getPieces().addAll(List.of(tagged, plain));
        Runnable blink = interval(period);

        Set<BlockData> seen = new java.util.HashSet<>();
        // each run blinks or not at random; 64 runs all skipping or all blinking is a 1 in 2^63 chance
        int unchanged = 0;
        for (int run = 0; run < 64; run++) {
            BlockData before = tagged.getBlock();
            blink.run();
            if (tagged.getBlock() == before) unchanged++;
            seen.add(tagged.getBlock());
        }

        assertTrue(unchanged > 0, "some runs skip");
        for (PaletteEntry entry : palette) assertTrue(seen.contains(entry.getBlock()), "every palette entry shows up");
        int index = palette.stream().map(PaletteEntry::getBlock).toList().indexOf(tagged.getBlock());
        assertSame(palette.get(index).getBrightness(), tagged.getBrightness());
        assertSame(plainBlock, plain.getBlock());
        assertEquals(null, plain.getBrightness());
    }

    private static List<PaletteEntry> palette() {
        return List.of(
            new PaletteEntry(RenderTestSupport.blockData(Material.CYAN_CONCRETE), new Display.Brightness(15, 15)),
            new PaletteEntry(RenderTestSupport.blockData(Material.BLACK_CONCRETE), new Display.Brightness(0, 0))
        );
    }

    @Test
    @DisplayName("every 10 ticks, at random, eye pieces take a random colour from the eye palette")
    void eyesBlink() {
        List<PaletteEntry> palette = palette();
        when(plan.getEyePalette()).thenReturn(palette);
        assertBlinks(10, "eye", palette);
    }

    @Test
    @DisplayName("every 5 ticks, at random, blinking lights take a random colour from the light palette")
    void lightsBlink() {
        List<PaletteEntry> palette = palette();
        when(plan.getBlinkingPalette()).thenReturn(palette);
        assertBlinks(5, "blinking_lights", palette);
    }

    @Test
    @DisplayName("an empty palette fails the blink rather than picking nothing")
    void emptyPaletteFails() {
        when(plan.getEyePalette()).thenReturn(List.of());
        bodyModel.getPieces().add(piece("eye"));
        Runnable blink = interval(10);

        assertThrows(NoSuchElementException.class, () -> {
            for (int run = 0; run < 64; run++) blink.run();
        });
    }

    @Test
    @DisplayName("each render draws the spider's displays, and a spider no longer rendered has its tree forgotten")
    void rendersDisplays() {
        bodyModel.getPieces().add(piece());
        app.render();
        assertEquals(1, server.spawned(BlockDisplay.class).size());
        RenderSpiderEntities.Retained tree = RenderSpiderEntities.prepare(spider, mock(Cloak.class), options);

        app.getEntities().clear();
        app.render();
        app.render();
        assertFalse(tree == RenderSpiderEntities.prepare(spider, mock(Cloak.class), options));
        assertTrue(errors.isEmpty());
    }

    private Player debugViewer() {
        Player viewer = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(viewer.getUniqueId()).thenReturn(id);
        when(viewer.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        server.bukkit().when(() -> Bukkit.getPlayer(id)).thenReturn(viewer);
        AppState.toggleDebugView(viewer);
        debugViewers.add(viewer);
        return viewer;
    }

    @Test
    @DisplayName("with a debug viewer the debug overlay is drawn too, hidden from everyone else")
    void debugOverlayForViewersOnly() {
        Player viewer = debugViewer();
        Player other = mock(Player.class);
        server.online.addAll(List.of(viewer, other));

        app.render();

        List<BlockDisplay> displays = server.spawned(BlockDisplay.class);
        assertFalse(displays.isEmpty());
        for (BlockDisplay display : displays) {
            verify(display).setVisibleByDefault(false);
            verify(viewer).showEntity(server.plugin, display);
            verify(other, never()).showEntity(any(), eq(display));
        }
        assertTrue(errors.isEmpty());
    }

    private Leg leg(SpiderBody owner, List<IKChainWorldNode3D> nodes) {
        SpiderLegIK ik = mock(SpiderLegIK.class);
        when(ik.getNodes()).thenReturn(nodes);
        Leg leg = mock(Leg.class);
        when(leg.getIk()).thenReturn(ik);
        when(leg.getSpider()).thenReturn(owner);
        return leg;
    }

    private static IKChainWorldNode3D node(double x, double y, double z) {
        return new IKChainWorldNode3D(new Vector(x, y, z), new Quaternionf(), new Quaternionf());
    }

    @Test
    @DisplayName("in particle mode each leg is a trail of bubbles every 0.05 blocks, thinning towards the foot, and no displays")
    void particleMode() {
        renderer.setUseParticles(true);
        assertTrue(renderer.getUseParticles());
        spider.setLegs(List.of(leg(spider, List.of(node(0, 64, 0), node(1, 64, 0), node(1, 65, 0)))));

        List<Vector> points = new ArrayList<>();
        doAnswer(call -> points.add(((Location) call.getArgument(1)).toVector()))
            .when(world).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());

        app.render();

        assertTrue(server.spawned.isEmpty());
        verify(world, times(21)).spawnParticle(eq(Particle.BUBBLE), any(Location.class), eq(1), eq(.025), eq(.025), eq(.025), eq(0.0));
        verify(world, times(21)).spawnParticle(eq(Particle.BUBBLE), any(Location.class), eq(1), eq(0.0), eq(0.0), eq(0.0), eq(0.0));
        assertEquals(42, points.size());
        assertTrue(points.getFirst().distance(new Vector(0, 64, 0)) < 1e-9);
        assertTrue(points.get(10).distance(new Vector(.5, 64, 0)) < 1e-9);
        assertTrue(points.get(20).distance(new Vector(1, 64, 0)) < 1e-9);
        assertTrue(points.get(21).distance(new Vector(1, 64, 0)) < 1e-9);
        assertTrue(points.getLast().distance(new Vector(1, 65, 0)) < 1e-9);
        assertTrue(errors.isEmpty());
    }

    @Test
    @DisplayName("in particle mode a leg with no world draws nothing, and an unsolved leg is an error")
    void particleModeEdgeCases() {
        renderer.setUseParticles(true);
        SpiderBody nowhere = mock(SpiderBody.class);
        spider.setLegs(List.of(leg(nowhere, List.of(node(0, 0, 0), node(1, 0, 0)))));
        app.render();
        verify(world, never()).spawnParticle(any(Particle.class), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
        assertTrue(errors.isEmpty());

        spider.setLegs(List.of(leg(spider, List.of())));
        app.render();
        assertEquals(1, errors.size());
        assertInstanceOf(NoSuchElementException.class, errors.getFirst().getThrown());
    }
}
