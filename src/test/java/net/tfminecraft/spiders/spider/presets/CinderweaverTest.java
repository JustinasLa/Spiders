package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderLegIK;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Bukkit;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CinderweaverTest {
    private static MockedStatic<Bukkit> bukkit;

    @BeforeAll
    static void installBlockDataFactory() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createBlockData(anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            int bracket = id.indexOf('[');
            return blockData(Material.matchMaterial(bracket < 0 ? id : id.substring(0, bracket)));
        });
    }

    @AfterAll
    static void restoreBukkit() {
        if (bukkit != null) bukkit.close();
    }

    private static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.clone()).thenAnswer(call -> blockData(material));
        return data;
    }

    private static long eyeCount(List<BlockDisplayModelPiece> pieces) {
        return pieces.stream().filter(piece -> piece.getTags().contains("eye")).count();
    }

    @Test
    @DisplayName("torso has eight steady eyes and only organic matte materials")
    void torsoHasEightSteadyEyesAndOnlyOrganicMatteMaterials() {
        List<BlockDisplayModelPiece> pieces = Cinderweaver.cinderweaverTorso().getPieces();
        assertTrue(pieces.size() >= 60 && pieces.size() <= 95);
        assertTrue(pieces.stream().allMatch(piece -> piece.getTransform().isFinite()));
        assertEquals(8, eyeCount(pieces));
        Set<Material> materials = pieces.stream().map(piece -> piece.getBlock().getMaterial()).collect(Collectors.toSet());
        assertEquals(EnumSet.of(Material.BLACK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.BLACK_CONCRETE, Material.RED_TERRACOTTA), materials);
        assertTrue(pieces.stream().filter(piece -> piece.getTags().contains("eye"))
            .allMatch(piece -> piece.getBrightness() != null && piece.getBrightness().getBlockLight() == 5));
    }

    @Test
    @DisplayName("spawn sizes accept the named presets or any scale in range")
    void spawnSizesAcceptNamedPresetsOrAnyScaleInRange() {
        assertEquals(0.35, CinderweaverSize.parseScale("hatchling"));
        assertEquals(1.5, CinderweaverSize.parseScale("BroodMother"));
        assertEquals(1.2, CinderweaverSize.parseScale("1.2"));
        assertEquals(0.25, CinderweaverSize.parseScale("0.25"));
        assertEquals(2.5, CinderweaverSize.parseScale("2.5"));
        for (String invalid : List.of("0.2", "2.6", "0", "-1", "NaN", "Infinity", "huge", "")) {
            assertNull(CinderweaverSize.parseScale(invalid), invalid);
        }
        assertEquals(1.2, Cinderweaver.cinderweaverBodyPlan(1.2).getScale(), 1e-9);
    }

    @Test
    @DisplayName("separately spawned spiders do not share mutable geometry")
    void separatelySpawnedSpidersDoNotShareMutableGeometry() {
        BodyPlan first = Cinderweaver.cinderweaverBodyPlan(CinderweaverSize.HUNTER);
        BodyPlan second = Cinderweaver.cinderweaverBodyPlan(CinderweaverSize.HUNTER);
        float torso = first.getBodyModel().getPieces().get(0).getTransform().m00();
        float leg = first.getLegs().get(0).getSegments().get(0).getModel().getPieces().get(0).getTransform().m00();
        second.scale(2.0);
        assertEquals(torso, first.getBodyModel().getPieces().get(0).getTransform().m00());
        assertEquals(leg, first.getLegs().get(0).getSegments().get(0).getModel().getPieces().get(0).getTransform().m00());
        assertEquals(torso * 2, second.getBodyModel().getPieces().get(0).getTransform().m00());
        assertEquals(leg * 2, second.getLegs().get(0).getSegments().get(0).getModel().getPieces().get(0).getTransform().m00());
    }

    @Test
    @DisplayName("legacy seals receive organic geometry while retaining their scale and edited rig")
    void legacySealsReceiveOrganicGeometryWhileRetainingTheirRig() {
        BodyPlan plan = Cinderweaver.cinderweaverBodyPlan(CinderweaverSize.BROODMOTHER);
        plan.getBodyModel().getPieces().get(0).setBlock(blockData(Material.BLAST_FURNACE));
        Vector restPosition = plan.getLegs().get(0).getRestPosition();
        restPosition.setX(restPosition.getX() + .2);
        SegmentPlan segment = plan.getLegs().get(0).getSegments().get(0);
        segment.setLength(segment.getLength() * .9);
        double length = plan.getLegs().get(0).getSegments().get(0).getLength();
        Vector rest = plan.getLegs().get(0).getRestPosition().clone();
        assertTrue(Cinderweaver.upgradeClockworkAppearance(plan));
        assertEquals(1.5, plan.getScale());
        assertEquals(rest, plan.getLegs().get(0).getRestPosition());
        assertEquals(length, plan.getLegs().get(0).getSegments().get(0).getLength());
        assertEquals(8, eyeCount(plan.getBodyModel().getPieces()));
        assertTrue(plan.getBodyModel().getPieces().stream().noneMatch(piece -> piece.getBlock().getMaterial() == Material.BLAST_FURNACE));
        assertFalse(Cinderweaver.upgradeClockworkAppearance(plan), "Current organic plans should be left alone");
    }

    @Test
    @DisplayName("spawning each size in open air remains finite and falls without phantom ground support")
    void spawningEachSizeInOpenAirRemainsFiniteAndFalls() {
        World world = mock(World.class);
        Block air = mock(Block.class);
        when(air.isPassable()).thenReturn(true);
        when(world.getBlockAt(any(Location.class))).thenReturn(air);
        when(world.getBlockAt(anyInt(), anyInt(), anyInt())).thenReturn(air);
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
            eq(FluidCollisionMode.NEVER), eq(true))).thenAnswer(call -> {
            Location start = call.getArgument(0);
            Vector direction = call.getArgument(1);
            assertTrue(start.isFinite(), "Raycast start must remain finite");
            assertTrue(Double.isFinite(direction.getX()) && Double.isFinite(direction.getY()) && Double.isFinite(direction.getZ()),
                "Raycast direction must remain finite");
            return null;
        });
        for (CinderweaverSize size : CinderweaverSize.values()) {
            Gait gait = Gait.defaultWalk();
            gait.scale(size.getScale());
            SpiderOptions options = mock(SpiderOptions.class);
            when(options.getGait()).thenReturn(gait);
            BodyPlan plan = Cinderweaver.cinderweaverBodyPlan(size);
            when(options.getBodyPlan()).thenReturn(plan);
            SpiderBody body = SpiderBody.create(world, new Vector(900.0, 500.0, 1500.0));
            ECS app = new ECS();
            ECSEntity entity = new ECSEntity();
            for (int i = 0; i < 20; i++) body.update(app, entity, options);
            Vector position = body.getPosition();
            assertTrue(Double.isFinite(position.getX()) && Double.isFinite(position.getY()) && Double.isFinite(position.getZ()));
            assertTrue(position.getY() < 500.0);
            assertTrue(body.getLegs().stream().noneMatch(leg -> leg.isGrounded()));
        }
    }

    @Test
    @DisplayName("console removal prevents an entity from entering the next simulation tick")
    void consoleRemovalPreventsEntityFromEnteringNextTick() {
        ECS app = new ECS();
        ECSEntity entity = app.spawn();
        app.onTick(ecs -> assertFalse(app.getEntities().contains(entity)));
        entity.remove();
        app.update();
        assertTrue(app.getEntities().isEmpty());
    }

    @Test
    @DisplayName("all sizes produce bounded finite geometry through the actual leg solver")
    void allSizesProduceBoundedFiniteGeometryThroughTheLegSolver() throws Exception {
        for (CinderweaverSize size : CinderweaverSize.values()) {
            BodyPlan plan = Cinderweaver.cinderweaverBodyPlan(size);
            assertEquals(8, plan.getLegs().size());
            assertTrue(plan.getLegs().stream().allMatch(leg -> leg.getSegments().size() == 3));
            assertEquals(1, plan.getEyePalette().size());
            List<Pair<BlockDisplayModelPiece, Matrix4f>> posed = new ArrayList<>();
            Vector position = new Vector(0.0, 1.1 * size.getScale(), 0.0);
            for (BlockDisplayModelPiece piece : plan.getBodyModel().getPieces()) {
                posed.add(new Pair<>(piece, new Matrix4f().translation(0f, (float) position.getY(), 0f).mul(piece.getTransform())));
            }
            for (LegPlan leg : plan.getLegs()) {
                SpiderLegIK ik = SpiderLegIK.createSpiderLegIK(leg);
                for (int i = 0; i < 5; i++) ik.solve(position, new Quaternionf(), leg.getRestPosition());
                List<SegmentPlan> segments = leg.getSegments();
                for (int index = 0; index < segments.size(); index++) {
                    Vector parent = ik.getNodes().get(index).getPosition();
                    Matrix4f transform = new Matrix4f().translation((float) parent.getX(), (float) parent.getY(), (float) parent.getZ())
                        .rotate(ik.getSegmentModelRotations().get(index));
                    for (BlockDisplayModelPiece piece : segments.get(index).getModel().getPieces()) {
                        posed.add(new Pair<>(piece, new Matrix4f(transform).mul(piece.getTransform())));
                    }
                }
            }
            assertTrue(posed.size() <= 175, "Display budget exceeded: " + posed.size());
            assertTrue(posed.stream().allMatch(pair -> pair.second().isFinite() && pair.second().determinant() > 0f));
            // Diagnostic mesh uses the same transforms as the in-game renderer for visual review.
            if (size == CinderweaverSize.HUNTER) {
                List<String> cubes = new ArrayList<>();
                for (Pair<BlockDisplayModelPiece, Matrix4f> pair : posed) {
                    List<String> vertices = new ArrayList<>();
                    for (int x = 0; x <= 1; x++) for (int y = 0; y <= 1; y++) for (int z = 0; z <= 1; z++) {
                        Vector3f v = pair.second().transformPosition(new Vector3f(x, y, z));
                        assertTrue(v.isFinite());
                        vertices.add("[" + v.x + "," + v.y + "," + v.z + "]");
                    }
                    cubes.add("{\"material\":\"" + pair.first().getBlock().getMaterial() + "\",\"vertices\":[" + String.join(",", vertices) + "]}");
                }
                Path output = Path.of("build", "organic-spider-preview.json");
                Files.createDirectories(output.getParent());
                Files.writeString(output, "[" + String.join(",", cubes) + "]");
            }
        }
    }
}
