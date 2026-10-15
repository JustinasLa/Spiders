package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.PounceOptions;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.MimicPose;
import net.tfminecraft.spiders.spider.presets.MimicPreset;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MimicTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    private final float px = MimicShape.PX;

    private static List<Vector3f> corners(BlockDisplayModelPiece piece) {
        List<Vector3f> out = new ArrayList<>();
        for (int it = 0; it <= 7; it++) {
            out.add(piece.getTransform().transformPosition(new Vector3f((float) (it & 1), (float) (it >> 1 & 1), (float) (it >> 2 & 1))));
        }
        return out;
    }

    private static List<Vector3f> corners(List<BlockDisplayModelPiece> pieces) {
        List<Vector3f> out = new ArrayList<>();
        for (BlockDisplayModelPiece piece : pieces) out.addAll(corners(piece));
        return out;
    }

    /** Kotlin's `minOf` over floats: throws on an empty list. */
    private static float minOf(List<Vector3f> points, ToDoubleFunction<Vector3f> selector) {
        if (points.isEmpty()) throw new NoSuchElementException();
        float min = (float) selector.applyAsDouble(points.get(0));
        for (int i = 1; i < points.size(); i++) min = Math.min(min, (float) selector.applyAsDouble(points.get(i)));
        return min;
    }

    /** Kotlin's `maxOf` over floats: throws on an empty list. */
    private static float maxOf(List<Vector3f> points, ToDoubleFunction<Vector3f> selector) {
        if (points.isEmpty()) throw new NoSuchElementException();
        float max = (float) selector.applyAsDouble(points.get(0));
        for (int i = 1; i < points.size(); i++) max = Math.max(max, (float) selector.applyAsDouble(points.get(i)));
        return max;
    }

    /** Kotlin's `single`: exactly one match, otherwise throws. */
    private static <T> T single(List<T> list, Predicate<T> predicate) {
        T found = null;
        boolean seen = false;
        for (T element : list) {
            if (predicate.test(element)) {
                if (seen) throw new IllegalArgumentException("Collection contains more than one matching element.");
                found = element;
                seen = true;
            }
        }
        if (!seen) throw new NoSuchElementException("Collection contains no element matching the predicate.");
        return found;
    }

    @Test
    @DisplayName("shut mimic is the vanilla chest with its mouth hidden inside")
    void shutMimicIsTheVanillaChestWithItsMouthHiddenInside() {
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        new MimicPose(plan.getBodyModel()).apply(0f, 0f, 0f);

        BlockDisplayModelPiece chest = single(plan.getBodyModel().getPieces(), it -> it.getTags().contains(MimicShape.CHEST));
        assertEquals(MimicShape.getCHEST_MODEL(), chest.getItemModel());
        assertEquals(0f, chest.getItemModelData());
        // the item model's cube stands on the chest's floor
        assertEquals(-MimicShape.HALF, minOf(corners(chest), it -> it.y), 1e-6f);
        double sumX = 0.0;
        List<Vector3f> chestCorners = corners(chest);
        for (Vector3f corner : chestCorners) sumX += corner.x;
        assertEquals(0f, (float) (sumX / chestCorners.size()), 1e-6f);

        List<BlockDisplayModelPiece> others = new ArrayList<>(plan.getBodyModel().getPieces());
        others.remove(chest);
        for (BlockDisplayModelPiece piece : others) {
            List<Vector3f> points = corners(piece);
            if (piece.getTags().contains(MimicShape.TONGUE)) {
                // collapsed into the floor of the mouth
                for (Vector3f point : points) assertTrue(point.distance(MimicShape.getTONGUE_ROOT()) < .01f, "tongue shows at " + point);
                continue;
            }
            // teeth, gullet and palate all sit inside the solid shut chest
            for (Vector3f point : points) {
                assertTrue(Math.abs(point.x) < MimicShape.HALF && Math.abs(point.z) < MimicShape.HALF,
                    piece.getBlock().getMaterial() + " pokes out at " + point);
                assertTrue(point.y >= -MimicShape.HALF && point.y <= MimicShape.HALF, piece.getBlock().getMaterial() + " y=" + point.y);
            }
        }
    }

    @Test
    @DisplayName("opening lifts the chest lid with its teeth and pushes the tongue over the lip")
    void openingLiftsTheChestLidWithItsTeethAndPushesTheTongueOverTheLip() {
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        BlockDisplayModelPiece chest = single(plan.getBodyModel().getPieces(), it -> it.getTags().contains(MimicShape.CHEST));
        // the front row, furthest from the hinge
        List<BlockDisplayModelPiece> upperTeeth = plan.getBodyModel().getPieces().stream().filter(it ->
            it.getTags().contains(MimicShape.LID) && it.getBlock().getMaterial() == Material.BONE_BLOCK && minOf(corners(it), c -> c.z) > 4 * px
        ).toList();
        List<BlockDisplayModelPiece> tongue = plan.getBodyModel().getPieces().stream().filter(it -> it.getTags().contains(MimicShape.TONGUE)).toList();
        MimicPose pose = new MimicPose(plan.getBodyModel());

        pose.apply(0f, 0f, 0f);
        float shut = minOf(corners(upperTeeth), it -> it.y);
        // the five front fangs, and the long canines at the corners
        assertTrue(upperTeeth.size() >= 5, "only " + upperTeeth.size() + " front fangs");
        pose.apply(Maths.toRadians(60f), 1f, 0f);
        // 60° of the vanilla lid's 90°, to the nearest of the pack's steps
        assertEquals(60f / 90f, chest.getItemModelData(), .5f / MimicShape.OPENNESS_STEPS);
        assertTrue(minOf(corners(upperTeeth), it -> it.y) > shut + .3f, "teeth did not lift with the lid");
        assertTrue(maxOf(corners(tongue), it -> it.z) > MimicShape.HALF + px, "tongue stays in the mouth");

        // shutting again restores the exact chest pose
        pose.apply(0f, 0f, 0f);
        assertEquals(0f, chest.getItemModelData());
        assertEquals(shut, minOf(corners(upperTeeth), it -> it.y), 1e-5f);
    }

    @Test
    @DisplayName("legs are cut from the chest's wood")
    void legsAreCutFromTheChestsWood() {
        List<BlockDisplayModelPiece> wood = new ArrayList<>();
        for (LegPlan leg : MimicPreset.mimicBodyPlan().getLegs()) {
            for (SegmentPlan segment : leg.getSegments()) {
                for (BlockDisplayModelPiece piece : segment.getModel().getPieces()) {
                    if (piece.getItemModel() != null) wood.add(piece);
                }
            }
        }
        assertTrue(!wood.isEmpty() && wood.stream().allMatch(it -> MimicShape.getWOOD_MODEL().equals(it.getItemModel())));
    }

    @Test
    @DisplayName("four legs with the front pair ahead")
    void fourLegsWithTheFrontPairAhead() {
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        assertEquals(4, plan.getLegs().size());
        List<LegPlan> legs = plan.getLegs();
        assertTrue(legs.subList(0, Math.min(2, legs.size())).stream().allMatch(it -> it.getRestPosition().getZ() > 0)
            && legs.subList(Math.min(2, legs.size()), legs.size()).stream().allMatch(it -> it.getRestPosition().getZ() < 0));
        assertTrue(legs.stream().allMatch(it -> it.getSegments().size() == 3));
    }

    @Test
    @DisplayName("woken mimic springs at the player, hunts and bites them, stares, then gallops straight away")
    void wokenMimicHuntsThenGallopsStraightAwayFromThePlayer() {
        World world = new Room((x, y, z) -> y >= -3 && y <= -1).world();
        when(world.isChunkGenerated(anyInt(), anyInt())).thenReturn(true);
        Gait walk = MimicPreset.mimicWalkGait();
        Gait gallop = MimicPreset.mimicGallopGait();
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        SpiderOptions options = mock(SpiderOptions.class);
        // the mimic asks for its gallop; the mock always answers with it
        when(options.getGait()).thenReturn(gallop);
        when(options.getWalkGait()).thenReturn(walk);
        when(options.getGallopGait()).thenReturn(gallop);
        when(options.getPounce()).thenReturn(new PounceOptions());
        when(options.getBodyPlan()).thenReturn(plan);
        Mimic.applyMimicRise(options, 0.0);

        ECS app = new ECS();
        SpiderBody.setupSpiderBody(app);
        Locomotion.setupLocomotion(app);
        Pounce.setupPounce(app);
        SpiderBody body = SpiderBody.create(world, new Vector(.5, MimicShape.RESTING_HEIGHT, .5));
        Locomotion locomotion = new Locomotion();
        // the player stands just behind the chest, to the north-west
        Vector player = new Vector(-1.5, 0.0, -2.5);
        Mimic mimic = new Mimic(new MimicPose(plan.getBodyModel()), null, spider -> player);
        ECSEntity entity = app.spawn(body, options, locomotion, mimic);
        assertEquals(MimicState.AMBUSH, mimic.getState());

        List<MimicState> seen = new ArrayList<>();
        double closest = Double.MAX_VALUE;
        Vector fleeMid = null;
        double maxTilt = 0.0;
        for (int tick = 0; tick < 1000; tick++) {
            mimic.update(entity, body, options, locomotion);
            app.update();
            assertTrue(DoubleStream.of(body.getPosition().getX(), body.getPosition().getY(), body.getPosition().getZ()).allMatch(Double::isFinite), "t=" + tick);
            if (seen.isEmpty() || seen.get(seen.size() - 1) != mimic.getState()) seen.add(mimic.getState());
            if (mimic.getState() == MimicState.HUNTING) closest = Math.min(closest, body.getPosition().clone().subtract(player).setY(0).length());
            if (mimic.getState() == MimicState.FLEEING && mimic.getStateTicks() == 200) fleeMid = body.getPosition().clone();
            if (mimic.getState() == MimicState.FLEEING && mimic.getStateTicks() > 30) maxTilt = Math.max(maxTilt, 1 - Maths.rotate(Maths.upVector(), body.getOrientation()).getY());
        }

        assertEquals(List.of(MimicState.AMBUSH, MimicState.LUNGE, MimicState.WAKING, MimicState.HUNTING, MimicState.STARING, MimicState.FLEEING), seen);
        assertTrue(closest < 1.5, "never caught up: " + closest);
        assertTrue(mimic.getBites() > 0, "never bit");
        assertEquals(MimicState.FLEEING, mimic.getState());
        verify(options, atLeastOnce()).setGallop(true);
        Vector away = body.getPosition().clone().subtract(player).setY(0);
        assertTrue(away.length() > 150, "only " + away.length() + " blocks away after 50 s");
        // once clear of the player it runs dead straight away from them
        Vector line = fleeMid.clone().subtract(player).setY(0).normalize();
        assertTrue(body.getPosition().clone().subtract(fleeMid).setY(0).normalize().dot(line) > .97, "veered off: " + away);
        assertTrue(maxTilt < .1, "wobbled: " + maxTilt);
        assertEquals(MimicShape.GALLOP_HEIGHT, body.getPosition().getY(), .35);
    }

    @Test
    @DisplayName("it stands up in four snaps")
    void standsUpInFourSnaps() {
        assertEquals(0.0, Mimic.stutter(0.0), 1e-9);
        assertEquals(1.0, Mimic.stutter(1.0), 1e-9);
        double previous = 0.0;
        for (int i = 1; i <= 100; i++) {
            double rise = Mimic.stutter(i / 100.0);
            assertTrue(rise >= previous - 1e-9, "sank at " + i);
            previous = rise;
        }
        // it holds still between the snaps
        assertEquals(Mimic.stutter(.6), Mimic.stutter(.7), 1e-9);
        assertEquals(.75, Mimic.stutter(.7), 1e-9);
    }

    @Test
    @DisplayName("glowing slit eyes on the palate hide in the shut chest")
    void glowingEyesOnThePalate() {
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        List<BlockDisplayModelPiece> eyes = plan.getBodyModel().getPieces().stream().filter(it -> it.getBrightness() != null).toList();
        assertEquals(7, eyes.size());
        assertTrue(eyes.stream().allMatch(it -> it.getTags().contains(MimicShape.LID) && it.getBrightness().getBlockLight() == 15));

        MimicPose pose = new MimicPose(plan.getBodyModel());
        pose.apply(0f, 0f, 0f);
        for (Vector3f point : corners(eyes)) assertTrue(point.y < MimicShape.SEAM && point.y > -MimicShape.HALF, "eye shows at " + point);
        // gaping, the palate stands up behind the mouth and the eyes look out of it
        pose.apply(Maths.toRadians(85f), 1f, 0f);
        assertTrue(minOf(corners(eyes), it -> it.y) > MimicShape.MOUTH, "eyes stay low");
    }

    @Test
    @DisplayName("the whole chest shakes and tilts without coming apart")
    void bodyPoseMovesEveryPieceTogether() {
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        MimicPose pose = new MimicPose(plan.getBodyModel());
        pose.apply(Maths.toRadians(40f), 1f, .2f);
        List<Vector3f> still = corners(plan.getBodyModel().getPieces());
        Matrix4f tilt = new Matrix4f().rotateZ(Maths.toRadians(30f));
        pose.apply(Maths.toRadians(40f), 1f, .2f, tilt);
        List<Vector3f> tilted = corners(plan.getBodyModel().getPieces());
        for (int i = 0; i < still.size(); i++) {
            assertEquals(0f, tilt.transformPosition(new Vector3f(still.get(i))).distance(tilted.get(i)), 1e-5f);
        }
    }

    @Test
    @DisplayName("the hunted's heart races as it closes in")
    void heartbeatQuickensUpClose() {
        assertEquals(26, MimicTerror.heartbeatInterval(40));
        assertEquals(9, MimicTerror.heartbeatInterval(1));
        assertTrue(MimicTerror.heartbeatInterval(8) < MimicTerror.heartbeatInterval(16));
    }

    @Test
    @DisplayName("a bite hurts but always leaves the last heart")
    void biteNeverKills() {
        assertEquals(Mimic.BITE_DAMAGE, MimicTerror.biteDamage(20.0, 0.0, Mimic.BITE_DAMAGE));
        assertEquals(1.0, MimicTerror.biteDamage(3.0, 0.0, Mimic.BITE_DAMAGE), 1e-9);
        // absorption hearts soak it first
        assertEquals(Mimic.BITE_DAMAGE, MimicTerror.biteDamage(2.0, 4.0, Mimic.BITE_DAMAGE));
        assertEquals(0.0, MimicTerror.biteDamage(2.0, 0.0, Mimic.BITE_DAMAGE));
        assertEquals(0.0, MimicTerror.biteDamage(1.0, 0.0, Mimic.BITE_DAMAGE));
    }

    @Test
    @DisplayName("stands up from chest height, walks, and sits back down")
    void standsUpFromChestHeightWalksAndSitsBackDown() {
        World world = new Room((x, y, z) -> x >= -20 && x <= 20 && z >= -20 && z <= 40 && y >= -3 && y <= -1).world();
        Gait walk = MimicPreset.mimicWalkGait();
        Gait gallop = MimicPreset.mimicGallopGait();
        BodyPlan plan = MimicPreset.mimicBodyPlan();
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getGait()).thenReturn(walk);
        when(options.getWalkGait()).thenReturn(walk);
        when(options.getGallopGait()).thenReturn(gallop);
        when(options.getPounce()).thenReturn(new PounceOptions());
        when(options.getBodyPlan()).thenReturn(plan);
        Mimic.applyMimicRise(options, 0.0);

        ECS app = new ECS();
        SpiderBody.setupSpiderBody(app);
        Locomotion.setupLocomotion(app);
        Pounce.setupPounce(app);
        SpiderBody body = SpiderBody.create(world, new Vector(.5, MimicShape.RESTING_HEIGHT, .5));
        Locomotion locomotion = new Locomotion();
        app.spawn(body, options, locomotion);

        Ticker ticker = new Ticker(app, body);

        ticker.tick(10);
        assertEquals(MimicShape.RESTING_HEIGHT, body.getPosition().getY(), .1, "sank or jumped as a chest");
        assertTrue(Math.abs(body.getPosition().getX() - .5) < .05 && Math.abs(body.getPosition().getZ() - .5) < .05, "shuffled while asleep");

        ticker.tick(Mimic.WAKE_TICKS, it -> Mimic.applyMimicRise(options, Mimic.smoothstep((it + 1.0) / Mimic.WAKE_TICKS)));
        ticker.tick(30);
        assertEquals(MimicShape.STANDING_HEIGHT, body.getPosition().getY(), .12, "did not stand up");
        assertEquals(4, Run.groundedLegs(body));
        assertTrue(up(body).getY() > .97);

        ticker.tick(80, it -> locomotion.walk(body.forwardDirection().multiply(walk.getMaxSpeed())));
        assertTrue(body.getPosition().getZ() > 3.5, "barely moved: " + body.getPosition());
        assertTrue(up(body).getY() > .95);
        assertEquals(MimicShape.STANDING_HEIGHT, body.getPosition().getY(), .25);

        ticker.tick(20);
        ticker.tick(Mimic.CROUCH_TICKS, it -> Mimic.applyMimicRise(options, 1 - Mimic.smoothstep((it + 1.0) / Mimic.CROUCH_TICKS)));
        ticker.tick(20);
        assertEquals(MimicShape.RESTING_HEIGHT, body.getPosition().getY(), .1, "did not sit down");
        assertTrue(up(body).getY() > .97);
    }

    private static Vector up(SpiderBody body) {
        return Maths.rotate(Maths.upVector(), body.getOrientation());
    }

    private record Ticker(ECS app, SpiderBody body) {
        void tick() {
            tick(1);
        }

        void tick(int times) {
            tick(times, it -> { });
        }

        void tick(int times, IntConsumer each) {
            for (int it = 0; it < times; it++) {
                each.accept(it);
                app.update();
                assertTrue(DoubleStream.of(body.getPosition().getX(), body.getPosition().getY(), body.getPosition().getZ()).allMatch(Double::isFinite));
            }
        }
    }
}
