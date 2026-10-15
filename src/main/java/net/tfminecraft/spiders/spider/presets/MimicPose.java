package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Swing the lid, slide the tongue and shake, tilt or stretch the whole chest.
 * Base transforms are captured once in {@link MimicPose}.
 */
public class MimicPose {
    private final List<BlockDisplayModelPiece> chest = new ArrayList<>();
    private final List<Pair<BlockDisplayModelPiece, Matrix4f>> fixed = new ArrayList<>();
    private final List<Pair<BlockDisplayModelPiece, Matrix4f>> lid = new ArrayList<>();
    private final List<Pair<BlockDisplayModelPiece, Matrix4f>> tongue = new ArrayList<>();

    public MimicPose(DisplayModel model) {
        for (BlockDisplayModelPiece piece : model.getPieces()) {
            if (piece.getTags().contains(MimicShape.CHEST)) chest.add(piece);
            Pair<BlockDisplayModelPiece, Matrix4f> base = new Pair<>(piece, new Matrix4f(piece.getTransform()));
            if (piece.getTags().contains(MimicShape.LID)) lid.add(base);
            else if (piece.getTags().contains(MimicShape.TONGUE)) tongue.add(base);
            else fixed.add(base);
        }
    }

    /**
     * @param lidAngle radians the lid is raised (0 = shut).
     * @param tongueOut 0 hides the tongue inside the gullet, 1 hangs it over the lip.
     * @param sway side-to-side tongue swing in radians.
     */
    public void apply(float lidAngle, float tongueOut, float sway) {
        apply(lidAngle, tongueOut, sway, new Matrix4f());
    }

    /** As {@link #apply(float, float, float)}, with the whole chest moved by {@code body} (in model space). */
    public void apply(float lidAngle, float tongueOut, float sway, Matrix4f body) {
        // the chest model only comes in fixed steps, so the teeth snap to the same angle
        float openness = Math.clamp(lidAngle / ((float) Math.PI / 2), 0f, 1f);
        // like Kotlin's roundToInt, refuse a NaN angle rather than snapping the lid shut
        if (Float.isNaN(openness)) throw new IllegalArgumentException("Cannot round NaN value.");
        int step = Math.round(openness * MimicShape.OPENNESS_STEPS);
        for (BlockDisplayModelPiece piece : chest) piece.setItemModelData((float) step / MimicShape.OPENNESS_STEPS);
        float angle = step * ((float) Math.PI / 2) / MimicShape.OPENNESS_STEPS;

        for (Pair<BlockDisplayModelPiece, Matrix4f> pair : fixed) pair.first().getTransform().set(body).mul(pair.second());

        Vector3f hinge = MimicShape.getHINGE();
        Matrix4f lidTransform = new Matrix4f(body).translate(hinge).rotateX(-angle).translate(new Vector3f(hinge).negate());
        for (Pair<BlockDisplayModelPiece, Matrix4f> pair : lid) pair.first().getTransform().set(lidTransform).mul(pair.second());

        Vector3f root = MimicShape.getTONGUE_ROOT();
        float extent = Math.clamp(tongueOut, 0.001f, 1f);
        Matrix4f tongueTransform = new Matrix4f(body).translate(root).rotateY(sway).scale(extent).translate(new Vector3f(root).negate());
        for (Pair<BlockDisplayModelPiece, Matrix4f> pair : tongue) pair.first().getTransform().set(tongueTransform).mul(pair.second());
    }
}
