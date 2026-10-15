package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SoundPlayer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKJoint3D;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class MimicPreset {
    private MimicPreset() {}

    public static SpiderOptions mimic() {
        SpiderOptions options = new SpiderOptions();
        options.setBodyPlan(mimicBodyPlan());
        options.setWalkGait(mimicWalkGait());
        options.setGallopGait(mimicGallopGait());
        options.getSound().setStep(new SoundPlayer(Sound.ENTITY_ZOMBIE_STEP, 0.12f, 1.3f));
        options.getSound().setStepBlockOverlay(new SoundPlayer(Sound.ENTITY_ZOMBIE_STEP, 0.05f, 1.5f));
        return options;
    }

    public static Gait mimicWalkGait() {
        Gait gait = Gait.defaultWalk();
        gait.setMaxSpeed(0.12);
        gait.setLegMoveSpeed(0.12 * 2.5);
        gait.getStationary().setBodyHeight(MimicShape.STANDING_HEIGHT);
        gait.getMoving().setBodyHeight(MimicShape.STANDING_HEIGHT);
        gait.getMoving().setTriggerZoneRadius(.6);
        return gait;
    }

    /** The flight gait: 7 blocks/s, faster than a sprinting player. */
    public static Gait mimicGallopGait() {
        Gait gait = Gait.defaultGallop();
        gait.setSpeed(.35);
        gait.getStationary().setBodyHeight(MimicShape.STANDING_HEIGHT);
        gait.getMoving().setBodyHeight(MimicShape.GALLOP_HEIGHT);
        return gait;
    }

    public static BodyPlan mimicBodyPlan() {
        BodyPlan plan = new BodyPlan();
        plan.setBodyModel(mimicChestModel());

        // Hips under the chest's corners; long legs splay out wide and knuckle up above the lid.
        Vector hip = new Vector(5 * MimicShape.PX, -6 * MimicShape.PX, 5 * MimicShape.PX);
        for (double[] pair : new double[][]{{1.0, .95}, {-1.0, -.95}}) {
            double hipZ = pair[0];
            double restZ = pair[1];
            for (double side : new double[]{1.0, -1.0}) {
                LegPlan leg = new LegPlan(
                    new Vector(side * hip.getX(), hip.getY(), hipZ * hip.getZ()),
                    new Vector(side * 1.0, .0, restZ),
                    mimicLegSegments(),
                    new Quaternionf().rotateX(-(Maths.toRadians(20f)))
                );
                List<LegPlan> legs = new ArrayList<>(plan.getLegs());
                legs.add(leg);
                plan.setLegs(legs);
            }
        }
        return plan;
    }

    private static List<SegmentPlan> mimicLegSegments() {
        double[] lengths = {.55, .65, .6};
        List<SegmentPlan> segments = new ArrayList<>();
        for (int index = 0; index < lengths.length; index++) {
            double length = lengths[index];
            IKJoint3D joint = index == 0 ? Presets.hipJoint() : Presets.kneeJoint();
            segments.add(new SegmentPlan(length, joint, mimicLegModel(index, (float) length)));
        }
        return segments;
    }

    /** Wooden limbs cut from the chest's own planks, with black iron-bound knuckles like its frame. */
    private static DisplayModel mimicLegModel(int index, float length) {
        float thickness = List.of(.15f, .11f, .08f).get(index);
        List<BlockDisplayModelPiece> parts = new ArrayList<>();
        for (int section = 0; section <= 1; section++) {
            float width = thickness * (1f - section * .18f);
            parts.add(wood(-width / 2, -width / 2, length * section / 2, width, width, length / 2 + .012f));
        }
        // Knuckles bound in black where the segments meet, pinned like the latch.
        parts.add(box(Material.BLACK_TERRACOTTA, -thickness * .62f, -thickness * .62f, -thickness * .3f,
            thickness * 1.24f, thickness * 1.24f, thickness * .9f));
        parts.add(box(Material.SMOOTH_STONE, -thickness * .7f, -thickness * .2f, -thickness * .05f,
            thickness * 1.4f, thickness * .4f, thickness * .4f));
        // A black-capped wooden foot.
        if (index == 2) parts.add(box(Material.BLACK_TERRACOTTA, -thickness * .55f, -thickness * .55f, length - .02f,
            thickness * 1.1f, thickness * 1.1f, .05f));
        return new DisplayModel(parts);
    }

    public static DisplayModel mimicChestModel() {
        float px = MimicShape.PX;
        float half = MimicShape.HALF;
        float seam = MimicShape.SEAM;
        float mouth = MimicShape.MOUTH;
        List<BlockDisplayModelPiece> parts = new ArrayList<>();

        // The vanilla chest. Item models fill the unit cube, and the chest sits on its floor, 1 px below ours.
        parts.add(new BlockDisplayModelPiece(
            Material.CHEST.createBlockData(),
            new Matrix4f().translation(-.5f, -half, -.5f),
            null,
            new ArrayList<>(List.of(MimicShape.CHEST)),
            MimicShape.getCHEST_MODEL()
        ));

        // A black gullet on the floor of the mouth, and lower fangs that hide inside the shut lid.
        parts.add(box(Material.BLACK_CONCRETE, -half + px, mouth, -half + px, half * 2 - 2 * px, .05f * px, half * 2 - 2 * px));
        for (Fang fang : fangs(false)) parts.addAll(fang.pieces(mouth, 1f));

        // The lid's black palate, its fangs, and a spider's cluster of glowing slit eyes that stare out when it gapes.
        List<BlockDisplayModelPiece> lid = new ArrayList<>();
        lid.add(box(Material.BLACK_CONCRETE, -half + px, seam - .05f * px, -half + px, half * 2 - 2 * px, .05f * px, half * 2 - 2 * px));
        for (Fang fang : fangs(true)) lid.addAll(fang.pieces(seam - .05f * px, -1f));
        for (float[] eye : EYES) lid.addAll(eye(eye[0] * px, seam - .05f * px, eye[1] * px, eye[2] * px, eye[3] > 0));
        for (BlockDisplayModelPiece it : lid) it.setTags(new ArrayList<>(List.of(MimicShape.LID)));
        parts.addAll(lid);

        // Tongue: lies in the gullet and slides out over the front lip as the lid opens.
        parts.add(box(Material.NETHER_WART_BLOCK, -2 * px, mouth + .05f * px, -3 * px, 4 * px, 1.2f * px, 12 * px,
            new ArrayList<>(List.of(MimicShape.TONGUE))));
        parts.add(link(Material.NETHER_WART_BLOCK, new Vector3f(0f, mouth + .6f * px, 9 * px),
            new Vector3f(0f, mouth - 3.5f * px, 10.5f * px), 3.4f * px, 1.1f * px, new ArrayList<>(List.of(MimicShape.TONGUE))));

        return new DisplayModel(parts);
    }

    /** Palate eyes: x, z, width (px), and whether it has a slit pupil. */
    private static final float[][] EYES = {
        {-2.4f, 1.0f, 2.4f, 1}, {2.4f, 1.0f, 2.4f, 1},
        {0f, -1.4f, 1.3f, 1},
        {-4.1f, -.4f, 1f, 0}, {4.1f, -.4f, 1f, 0},
        {-1.5f, -3.4f, .9f, 0}, {1.5f, -3.4f, .9f, 0},
    };

    /** A red eye that glows in the dark of the mouth, facing down from the palate, with a black slit. */
    private static List<BlockDisplayModelPiece> eye(float x, float palate, float z, float width, boolean slit) {
        float px = MimicShape.PX;
        float depth = width * .66f;
        List<BlockDisplayModelPiece> pieces = new ArrayList<>();
        BlockDisplayModelPiece glow = box(Material.RED_CONCRETE, x - width / 2, palate - .12f * px, z - depth / 2, width, .12f * px, depth);
        glow.setBrightness(new Display.Brightness(15, 15));
        pieces.add(glow);
        if (slit) pieces.add(box(Material.BLACK_CONCRETE, x - width * .09f, palate - .16f * px, z - depth * .45f, width * .18f, .04f * px, depth * .9f));
        return pieces;
    }

    /** One fang: a broad bone root and a narrow tip, standing up from the jaw ({@code direction} 1) or hanging from it (-1). */
    private record Fang(float x, float z, float width, float length) {
        List<BlockDisplayModelPiece> pieces(float jaw, float direction) {
            float root = length * .55f;
            float tip = length - root;
            float tipWidth = width * .55f;
            return List.of(
                box(Material.BONE_BLOCK, x - width / 2, direction > 0 ? jaw : jaw - root, z - width / 2, width, root, width),
                box(Material.BONE_BLOCK, x - tipWidth / 2, direction > 0 ? jaw + root : jaw - length, z - tipWidth / 2, tipWidth, tip, tipWidth)
            );
        }
    }

    /**
     * Uneven fangs along the front and side rims, a shorter row behind them, and long canines at the
     * front corners. Lower fangs reach up into the shut lid and upper fangs down into the base, so the
     * shut chest hides them all.
     */
    static List<Fang> fangs(boolean upper) {
        float px = MimicShape.PX;
        // just inside the chest's rim
        float rim = MimicShape.HALF - 1.8f * px;
        float offset = upper ? .5f : 0f;
        // a fixed seed: every mimic has the same crooked smile
        java.util.Random random = new java.util.Random(upper ? 7 : 11);
        float maxLength = (upper ? 4.4f : 3.5f) * px;
        List<Fang> out = new ArrayList<>();
        for (int i = 0; i <= 4; i++) {
            float x = (-4f + 2f * (i + offset)) * px;
            out.add(new Fang(x, rim, (1.1f + .3f * random.nextFloat()) * px, maxLength * (.5f + .45f * random.nextFloat())));
        }
        for (float side : new float[]{-1f, 1f}) for (int i = 0; i <= 2; i++) {
            out.add(new Fang(side * rim, (4f - 3f * (i + offset)) * px, (1f + .3f * random.nextFloat()) * px, maxLength * (.45f + .45f * random.nextFloat())));
        }
        // the inner row
        for (int i = 0; i <= 3; i++) {
            float x = (-3f + 2f * (i + offset)) * px;
            out.add(new Fang(x, rim - 1.6f * px, .8f * px, maxLength * (.3f + .25f * random.nextFloat())));
        }
        // canines
        for (float side : new float[]{-1f, 1f}) out.add(new Fang(side * (rim - .2f * px), rim - .2f * px, 1.5f * px, maxLength));
        return out;
    }

    private static BlockDisplayModelPiece link(
        Material material, Vector3f from, Vector3f to, float width, float thickness,
        List<String> tags
    ) {
        Vector3f direction = new Vector3f(to).sub(from);
        float length = direction.length();
        return new BlockDisplayModelPiece(material.createBlockData(), new Matrix4f().translation(from)
            .rotate(new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), direction.normalize()))
            .translate(-width / 2, -thickness / 2, 0f).scale(width, thickness, length), null, tags);
    }

    /** A box of the chest's plank texture. */
    private static BlockDisplayModelPiece wood(float x, float y, float z, float width, float height, float depth) {
        BlockDisplayModelPiece piece = box(Material.STRIPPED_OAK_WOOD, x, y, z, width, height, depth);
        piece.setItemModel(MimicShape.getWOOD_MODEL());
        return piece;
    }

    private static BlockDisplayModelPiece box(
        Material material, float x, float y, float z, float width, float height, float depth,
        List<String> tags
    ) {
        return new BlockDisplayModelPiece(
            material.createBlockData(),
            new Matrix4f().translation(x, y, z).scale(width, height, depth),
            null,
            tags
        );
    }

    private static BlockDisplayModelPiece box(
        Material material, float x, float y, float z, float width, float height, float depth
    ) {
        return box(material, x, y, z, width, height, depth, new ArrayList<>());
    }
}
