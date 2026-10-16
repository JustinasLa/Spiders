package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.CloakOptions;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.block_colors.BlockColors;
import net.tfminecraft.spiders.utilities.colors.Colors;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Display;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloakColourTest {
    private ComponentServer server;
    /** What lies behind every piece; null when nothing is hit. */
    private Material behind = Material.SAND;
    private final World world = mock(World.class);
    private final Vector position = new Vector(.5, 65.0, .5);
    private final CloakOptions options = new CloakOptions();
    private int tick = 0;

    @BeforeEach
    void setUp() {
        server = new ComponentServer();
        ComponentServer.setTick(0);
        when(world.getPlayers()).thenReturn(List.of());
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), eq(true)))
            .thenAnswer(call -> behind == null ? null : new RayTraceResult(new Vector(), block(behind), BlockFace.UP));
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    private static Block block(Material material) {
        Block block = mock(Block.class);
        BlockData data = ComponentServer.blockData(material);
        when(block.getBlockData()).thenReturn(data);
        return block;
    }

    private void verifyRays(int count) {
        verify(world, times(count)).rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), eq(true));
    }

    /** One render of {@code id} a full look period after the last, so it always looks again. */
    private Pair<BlockData, Display.Brightness> render(Cloak cloak, Object id, BlockData original, Display.Brightness brightness) {
        tick += Cloak.LOOK_TICKS;
        ComponentServer.setTick(tick);
        return cloak.getPiece(id, world, position, original, brightness, options);
    }

    private static float distance(BlockData block, Material to) {
        return Colors.distanceTo(BlockColors.getBlockColor(block), BlockColors.getBlockColor(ComponentServer.blockData(to)));
    }

    @Test
    @DisplayName("an idle cloak shows the spider's own blocks and light")
    void anIdleCloakShowsTheSpidersOwnBlocks() {
        // the real random source: an idle cloak shows its own blocks whatever the shimmer
        Cloak cloak = new Cloak();
        BlockData stone = ComponentServer.blockData(Material.STONE);
        Display.Brightness light = new Display.Brightness(3, 7);
        Pair<BlockData, Display.Brightness> piece = render(cloak, "piece", stone, light);
        assertSame(stone, piece.first());
        assertSame(light, piece.second());
        assertNull(render(cloak, "piece", stone, null).second());
        verifyRays(0);
    }

    @Test
    @DisplayName("an active cloak fades into what lies behind it, and back when switched off")
    void anActiveCloakFadesIntoWhatLiesBehindIt() {
        Cloak cloak = new Cloak(new SplittableRandom(7));
        cloak.setActive(true);
        assertTrue(cloak.getActive());
        BlockData stone = ComponentServer.blockData(Material.STONE);
        Display.Brightness light = new Display.Brightness(0, 15);
        Object id = "piece";

        Pair<BlockData, Display.Brightness> first = render(cloak, id, stone, light);
        float start = distance(first.first(), Material.SAND);
        Pair<BlockData, Display.Brightness> piece = first;
        for (int i = 0; i < 300; i++) piece = render(cloak, id, stone, light);
        assertTrue(distance(piece.first(), Material.SAND) < start, "Never moved towards sand: " + piece.first().getMaterial());
        assertTrue(distance(piece.first(), Material.SAND) < 15f, "Ended far from sand: " + piece.first().getMaterial());
        // a full-colour block is lit by the world
        assertNull(piece.second());

        options.setAllowCustomBrightness(true);
        Display.Brightness dimmed = render(cloak, id, stone, light).second();
        assertNotNull(dimmed);
        assertEquals(0, dimmed.getBlockLight());

        cloak.setActive(false);
        for (int i = 0; i < 300; i++) piece = render(cloak, id, stone, light);
        assertSame(stone, piece.first(), "The cloak should fade back to the spider's own block");
        assertSame(light, piece.second());
    }

    @Test
    @DisplayName("with nothing (or nothing colourful) behind it the cloak holds its colour")
    void withNothingBehindItTheCloakHoldsItsColour() {
        Cloak cloak = new Cloak(new SplittableRandom(7));
        cloak.setActive(true);
        BlockData stone = ComponentServer.blockData(Material.STONE);
        behind = null;
        assertSame(stone, render(cloak, "fresh", stone, null).first());
        behind = Material.BARRIER;
        assertSame(stone, render(cloak, "fresh", stone, null).first());

        behind = Material.SAND;
        Material held = null;
        for (int i = 0; i < 4; i++) held = render(cloak, "piece", stone, null).first().getMaterial();
        behind = null;
        for (int i = 0; i < 20; i++) assertEquals(held, render(cloak, "piece", stone, null).first().getMaterial());
        behind = Material.BARRIER;
        for (int i = 0; i < 20; i++) assertEquals(held, render(cloak, "piece", stone, null).first().getMaterial());
    }

    @Test
    @DisplayName("blocks without a known colour are never disguised")
    void blocksWithoutAKnownColourAreNeverDisguised() {
        Cloak cloak = new Cloak(new SplittableRandom(7));
        cloak.setActive(true);
        BlockData barrier = ComponentServer.blockData(Material.BARRIER);
        for (int i = 0; i < 10; i++) assertSame(barrier, render(cloak, "piece", barrier, null).first());
        verifyRays(0);
    }

    @Test
    @DisplayName("each piece looks behind it only every few ticks")
    void eachPieceLooksBehindItOnlyEveryFewTicks() {
        Cloak cloak = new Cloak(new SplittableRandom(7));
        cloak.setActive(true);
        BlockData stone = ComponentServer.blockData(Material.STONE);
        Object id = "piece";
        ComponentServer.setTick(100);
        cloak.getPiece(id, world, position, stone, null, options);
        verifyRays(1);
        cloak.getPiece(id, world, position, stone, null, options);
        verifyRays(1);
        ComponentServer.setTick(100 + Cloak.LOOK_TICKS);
        cloak.getPiece(id, world, position, stone, null, options);
        verifyRays(2);
        cloak.getPiece(id, world, position, stone, null, options);
        verifyRays(2);
        // a clock that went backwards (e.g. after a reload) looks again
        ComponentServer.setTick(50);
        cloak.getPiece(id, world, position, stone, null, options);
        verifyRays(3);
    }

    @Test
    @DisplayName("toggling the cloak flips it and announces it")
    void togglingTheCloakFlipsItAndAnnouncesIt() {
        ECS app = new ECS();
        List<CloakToggleEvent> toggles = new ArrayList<>();
        app.onEvent(CloakToggleEvent.class, toggles::add);
        Cloak cloak = new Cloak(new SplittableRandom(7));

        ECSEntity bodiless = app.spawn(cloak);
        cloak.toggleCloak(app, bodiless);
        assertFalse(cloak.getActive());
        assertTrue(toggles.isEmpty());

        SpiderBody spider = SpiderBody.create(world, position);
        ECSEntity entity = app.spawn(spider, cloak);
        cloak.toggleCloak(app, entity);
        assertTrue(cloak.getActive());
        assertEquals(1, toggles.size());
        assertSame(entity, toggles.get(0).getEntity());
        assertSame(spider, toggles.get(0).getSpider());
        cloak.toggleCloak(app, entity);
        assertFalse(cloak.getActive());
    }

    private record Hit(ECS app, ECSEntity entity, SpiderBody spider, Cloak cloak, List<CloakDamageEvent> damage) {
        void hit(boolean forceful) {
            app.emit(new SpiderHitEvent(entity, spider, mock(Arrow.class), new Vector(), forceful));
        }
    }

    private Hit hittable(SpiderOptions options) {
        ECS app = new ECS();
        Cloak.setupCloak(app);
        List<CloakDamageEvent> damage = new ArrayList<>();
        app.onEvent(CloakDamageEvent.class, damage::add);
        SpiderBody spider = SpiderBody.create(world, position);
        Cloak cloak = new Cloak(new SplittableRandom(7));
        cloak.setActive(true);
        ECSEntity entity = options == null ? app.spawn(spider, cloak) : app.spawn(spider, cloak, options);
        return new Hit(app, entity, spider, cloak, damage);
    }

    @Test
    @DisplayName("a forceful hit breaks an active cloak and switches it off")
    void aForcefulHitBreaksAnActiveCloak() {
        SpiderOptions spiderOptions = mock(SpiderOptions.class);
        when(spiderOptions.getCloak()).thenReturn(options);
        Hit hit = hittable(spiderOptions);

        hit.hit(false);
        assertTrue(hit.cloak().getActive(), "A glancing hit should not break the cloak");
        assertTrue(hit.damage().isEmpty());

        hit.hit(true);
        assertFalse(hit.cloak().getActive());
        assertEquals(1, hit.damage().size());
        CloakDamageEvent event = hit.damage().get(0);
        assertSame(hit.entity(), event.getEntity());
        assertSame(hit.spider(), event.getSpider());
        assertSame(hit.cloak(), event.getCloak());
        // the glitch is over once its tasks have run
        server.advance(200);
        assertEquals(0, server.pending());

        // a hit on a cloak that is already off changes nothing
        hit.hit(true);
        assertFalse(hit.cloak().getActive());
        assertEquals(1, hit.damage().size());
    }

    @Test
    @DisplayName("a hit leaves the cloak alone without cloak options, and ignores uncloaked spiders")
    void aHitLeavesTheCloakAloneWithoutCloakOptions() {
        Hit noOptions = hittable(null);
        noOptions.hit(true);
        assertTrue(noOptions.cloak().getActive());
        assertTrue(noOptions.damage().isEmpty());

        Hit noCloakOptions = hittable(mock(SpiderOptions.class));
        noCloakOptions.hit(true);
        assertTrue(noCloakOptions.cloak().getActive());
        assertTrue(noCloakOptions.damage().isEmpty());

        ECS app = new ECS();
        Cloak.setupCloak(app);
        SpiderBody spider = SpiderBody.create(world, position);
        ECSEntity uncloaked = app.spawn(spider);
        app.emit(new SpiderHitEvent(uncloaked, spider, null, new Vector(), true));
        assertNull(uncloaked.query(Cloak.class));
    }

    private static final Set<Material> GLAZED = EnumSet.of(Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA,
        Material.WHITE_GLAZED_TERRACOTTA, Material.GRAY_GLAZED_TERRACOTTA);

    @Test
    @DisplayName("a broken cloak glitches and flashes, then settles")
    void aBrokenCloakGlitchesAndFlashesThenSettles() {
        Cloak cloak = new Cloak(new SplittableRandom(7));
        cloak.setActive(true);
        BlockData stone = ComponentServer.blockData(Material.STONE);
        Display.Brightness light = new Display.Brightness(3, 7);
        List<Object> ids = new ArrayList<>();
        for (int i = 0; i < 200; i++) ids.add("piece " + i);
        for (Object id : ids) cloak.getPiece(id, world, position, stone, light, options);

        clearInvocations(world);
        cloak.breakCloak(options);
        boolean glazed = false;
        boolean flashed = false;
        boolean plain = false;
        for (int t = 0; t < 60; t++) {
            server.advance(1);
            for (Object id : ids) {
                Pair<BlockData, Display.Brightness> piece = cloak.getPiece(id, world, position, stone, light, options);
                boolean override = piece.second() != null && piece.second() != light && piece.second().getSkyLight() == 15;
                if (override && GLAZED.contains(piece.first().getMaterial())) glazed = true;
                else if (override) flashed = true;
                if (piece.first() == stone) plain = true;
            }
        }
        assertTrue(glazed, "No piece glitched");
        assertTrue(flashed, "No piece flashed its old colour");
        assertTrue(plain, "No piece dropped its disguise");
        // while it glitches, the pieces don't look around
        verify(world, never()).rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), eq(true));

        assertEquals(0, server.pending());
        ComponentServer.setTick(1000);
        for (Object id : ids) {
            Pair<BlockData, Display.Brightness> piece = cloak.getPiece(id, world, position, stone, light, options);
            assertNotEquals(15, piece.second() == null ? -1 : piece.second().getSkyLight(), "Override left on " + id);
        }
        verifyRays(ids.size());
    }
}
