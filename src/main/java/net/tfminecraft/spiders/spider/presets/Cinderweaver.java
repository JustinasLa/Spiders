package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.PaletteEntry;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SoundPlayer;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class Cinderweaver {
    private Cinderweaver() {}

    /** Organic, real-spider-inspired dark fantasy art direction; no resource pack required. */
    public static SpiderOptions cinderweaver(CinderweaverSize size) {
        return cinderweaver(size.getScale());
    }

    /** Organic, real-spider-inspired dark fantasy art direction; no resource pack required. */
    public static SpiderOptions cinderweaver() {
        return cinderweaver(CinderweaverSize.HUNTER);
    }

    public static SpiderOptions cinderweaver(double scale) {
        SpiderOptions options = new SpiderOptions();
        options.setBodyPlan(cinderweaverBodyPlan(scale));
        useOrganicSounds(options);
        options.getWalkGait().setMaxSpeed(0.12);
        options.getGallopGait().setMaxSpeed(0.24);
        options.getWalkGait().scale(scale);
        options.getGallopGait().scale(scale);
        return options;
    }

    public static void useOrganicSounds(SpiderOptions options) {
        options.getSound().setStep(new SoundPlayer(Sound.ENTITY_SPIDER_STEP, 0.14f, 0.75f));
        options.getSound().setStepBlockOverlay(new SoundPlayer(Sound.ENTITY_SPIDER_STEP, 0.06f, 0.85f));
    }

    /** Seals store full body plans. Upgrade the old firebox model without changing its saved rig. */
    public static boolean upgradeClockworkAppearance(BodyPlan plan) {
        boolean hasFirebox = false;
        for (BlockDisplayModelPiece piece : plan.getBodyModel().getPieces()) {
            if (piece.getBlock().getMaterial() == Material.BLAST_FURNACE) {
                hasFirebox = true;
                break;
            }
        }
        if (!hasFirebox) return false;
        if (plan.getLegs().size() != 8) return false;
        for (LegPlan leg : plan.getLegs()) {
            if (leg.getSegments().size() != 3) return false;
        }
        BodyPlan organic = cinderweaverBodyPlan(CinderweaverSize.HUNTER);
        organic.scale(plan.getScale() / organic.getScale());
        plan.setBodyModel(organic.getBodyModel());
        plan.setEyePalette(organic.getEyePalette());
        plan.setBlinkingPalette(organic.getBlinkingPalette());
        List<LegPlan> legs = plan.getLegs();
        for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
            List<SegmentPlan> segments = legs.get(legIndex).getSegments();
            for (int segmentIndex = 0; segmentIndex < segments.size(); segmentIndex++) {
                SegmentPlan segment = segments.get(segmentIndex);
                SegmentPlan replacement = organic.getLegs().get(legIndex).getSegments().get(segmentIndex);
                segment.setModel(replacement.getModel().scale((float) (segment.getLength() / replacement.getLength())));
            }
        }
        return true;
    }

    public static BodyPlan cinderweaverBodyPlan(CinderweaverSize size) {
        return cinderweaverBodyPlan(size.getScale());
    }

    public static BodyPlan cinderweaverBodyPlan(double scale) {
        BodyPlan plan = Presets.octopodBodyPlan(3, 0.7);
        plan.setBodyModel(cinderweaverTorso());

        for (LegPlan leg : plan.getLegs()) {
            List<SegmentPlan> segments = leg.getSegments();
            for (int index = 0; index < segments.size(); index++) {
                SegmentPlan segment = segments.get(index);
                float thickness = List.of(.18f, .13f, .085f).get(index);
                float length = (float) segment.getLength();
                List<BlockDisplayModelPiece> parts = new ArrayList<>();
                // Three narrowing sections give each articulated leg an organic taper.
                for (int section = 0; section <= 2; section++) {
                    float width = thickness * (1f - section * .26f);
                    parts.add(cuboid(
                        index == 0 ? Material.GRAY_TERRACOTTA : Material.BLACK_TERRACOTTA,
                        -width / 2, -width / 2, length * section / 3,
                        width, width, length / 3 + .012f
                    ));
                }
                if (index == 1) {
                    parts.add(link(Material.BLACK_CONCRETE, new Vector3f(0f, thickness / 2, length * .35f),
                        new Vector3f(0f, thickness / 2 + .13f, length * .25f), .018f, .008f));
                }
                segment.setModel(new DisplayModel(parts));
            }
        }

        // Subdued eyeshine rather than flashing lights. Real spiders do not blink.
        PaletteEntry eye = new PaletteEntry(Material.RED_TERRACOTTA.createBlockData(), new Display.Brightness(5, 0));
        plan.setEyePalette(new ArrayList<>(List.of(eye)));
        plan.setBlinkingPalette(new ArrayList<>(List.of(eye)));
        plan.scale(scale);
        return plan;
    }

    public static DisplayModel cinderweaverTorso() {
        List<BlockDisplayModelPiece> parts = new ArrayList<>();
        // A swollen abdomen and smaller, flatter cephalothorax joined by a narrow pedicel.
        parts.addAll(roundedBody(new Vector3f(0f, .02f, -1.03f), new Vector3f(.68f, .49f, .87f), 5, 7, Material.BLACK_TERRACOTTA));
        parts.add(cuboid(Material.BLACK_TERRACOTTA, -.16f, -.15f, -.47f, .32f, .26f, .39f));
        parts.addAll(roundedBody(new Vector3f(0f, -.015f, .20f), new Vector3f(.46f, .27f, .58f), 3, 5, Material.GRAY_TERRACOTTA));

        // Muted abdominal chevrons are natural markings, laid against the dorsal surface.
        for (float z : new float[]{-1.27f, -.95f}) {
            for (float side : new float[]{-1f, 1f}) {
                parts.add(link(Material.GRAY_TERRACOTTA, new Vector3f(side * .29f, .514f, z - .12f),
                    new Vector3f(0f, .514f, z + .04f), .045f, .025f));
            }
        }

        // Eight unequal eyes in a curved cluster: large central pair, three smaller pairs.
        for (float side : new float[]{-1f, 1f}) {
            for (float[] eye : new float[][]{
                {.085f, .025f, .782f, .066f},
                {.225f, .060f, .741f, .043f},
                {.315f, .115f, .550f, .034f},
                {.145f, .187f, .550f, .036f},
            }) {
                float x = eye[0];
                float y = eye[1];
                float z = eye[2];
                float radius = eye[3];
                parts.add(cuboid(Material.BLACK_CONCRETE, side * x - radius * 1.3f, y - radius * 1.3f,
                    z - .025f, radius * 2.6f, radius * 2.6f, radius * 1.3f));
                BlockDisplayModelPiece iris = cuboid(Material.RED_TERRACOTTA, side * x - radius, y - radius,
                    z + radius * .3f, radius * 2, radius * 2, radius * .75f, new ArrayList<>(List.of("eye")));
                iris.setBrightness(new Display.Brightness(5, 0));
                parts.add(iris);
            }

            // Paired chelicerae bend down and inward into sharp fangs; short palps flank them.
            parts.add(link(Material.BLACK_TERRACOTTA, new Vector3f(side * .18f, -.12f, .67f),
                new Vector3f(side * .21f, -.20f, .91f), .16f, .13f));
            parts.add(link(Material.BLACK_CONCRETE, new Vector3f(side * .21f, -.20f, .91f),
                new Vector3f(side * .15f, -.34f, 1.04f), .11f, .065f));
            parts.add(link(Material.BLACK_CONCRETE, new Vector3f(side * .15f, -.34f, 1.04f),
                new Vector3f(side * .06f, -.40f, .99f), .055f, .018f));
            parts.add(link(Material.GRAY_TERRACOTTA, new Vector3f(side * .35f, -.11f, .54f),
                new Vector3f(side * .47f, -.23f, .80f), .09f, .07f));
            parts.add(link(Material.BLACK_TERRACOTTA, new Vector3f(side * .47f, -.23f, .80f),
                new Vector3f(side * .39f, -.28f, .96f), .065f, .035f));

            // Sparse, short setae break up the silhouette without a particle cloud.
            for (float z : new float[]{-1.45f, -1.11f, -.77f}) {
                float t = (z + 1.03f) / .87f;
                float x = .68f * (float) Math.sqrt(1f - t * t) * .93f;
                parts.add(link(Material.BLACK_CONCRETE, new Vector3f(side * x, .09f, z),
                    new Vector3f(side * (x + .14f), .16f, z - .07f), .022f, .008f));
            }
            parts.add(link(Material.BLACK_TERRACOTTA, new Vector3f(side * .11f, -.19f, -1.77f),
                new Vector3f(side * .12f, -.22f, -1.99f), .07f, .03f));
        }
        return new DisplayModel(parts);
    }

    /** Compact horizontal slices make a rounded Minecraft silhouette with a bounded display count. */
    private static List<BlockDisplayModelPiece> roundedBody(Vector3f center, Vector3f radius, int rows, int slices, Material material) {
        List<BlockDisplayModelPiece> parts = new ArrayList<>();
        for (int row = 0; row < rows; row++) {
            float y = -1f + (row + .5f) * 2f / rows;
            for (int slice = 0; slice < slices; slice++) {
                float z = -1f + (slice + .5f) * 2f / slices;
                float widthSquared = 1f - y * y - z * z;
                if (widthSquared <= 0f) continue;
                float halfWidth = radius.x * (float) Math.sqrt(widthSquared);
                parts.add(cuboid(material, center.x - halfWidth, center.y + radius.y * (y - 1f / rows),
                    center.z + radius.z * (z - 1f / slices),
                    halfWidth * 2, radius.y * 2 / rows, radius.z * 2 / slices));
            }
        }
        return parts;
    }

    private static BlockDisplayModelPiece link(Material material, Vector3f from, Vector3f to, float width, float thickness) {
        Vector3f direction = new Vector3f(to).sub(from);
        float length = direction.length();
        return new BlockDisplayModelPiece(material.createBlockData(), new Matrix4f().translation(from)
            .rotate(new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), direction.normalize()))
            .translate(-width / 2, -thickness / 2, 0f).scale(width, thickness, length), null, new ArrayList<>(List.of("cloak")));
    }

    private static BlockDisplayModelPiece cuboid(
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

    private static BlockDisplayModelPiece cuboid(
        Material material, float x, float y, float z, float width, float height, float depth
    ) {
        return cuboid(material, x, y, z, width, height, depth, new ArrayList<>(List.of("cloak")));
    }
}
