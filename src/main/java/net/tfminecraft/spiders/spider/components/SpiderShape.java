package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.Pair;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class SpiderShape {
    private static final double BODY_REACH = 2.2;

    private SpiderShape() {
    }

    /** Every block the spider is drawn with, placed exactly as {@link net.tfminecraft.spiders.spider.components.rendering.RenderSpiderEntities} places them. */
    public static List<ShapePiece> shapePieces(SpiderBody spider, BodyPlan plan) {
        List<ShapePiece> pieces = new ArrayList<>();

        Matrix4f bodyTransform = new Matrix4f().rotate(spider.getOrientation());
        for (BlockDisplayModelPiece piece : plan.getBodyModel().getPieces()) pieces.add(new ShapePiece(spider.getPosition(), new Matrix4f(bodyTransform).mul(piece.getTransform())));

        List<Leg> legs = spider.getLegs();
        for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
            Leg leg = legs.get(legIndex);
            List<Quaternionf> rotations = leg.getIk().getSegmentModelRotations();
            for (int segmentIndex = 0; segmentIndex < rotations.size(); segmentIndex++) {
                SegmentPlan segment = segmentAt(plan, legIndex, segmentIndex);
                if (segment == null) continue;
                Matrix4f segmentTransform = new Matrix4f().rotate(rotations.get(segmentIndex));
                Vector parent = leg.getIk().getNodes().get(segmentIndex).getPosition();
                for (BlockDisplayModelPiece piece : segment.getModel().getPieces()) pieces.add(new ShapePiece(parent, new Matrix4f(segmentTransform).mul(piece.getTransform())));
            }
        }
        return pieces;
    }

    private static SegmentPlan segmentAt(BodyPlan plan, int legIndex, int segmentIndex) {
        List<LegPlan> legs = plan.getLegs();
        if (legIndex < 0 || legIndex >= legs.size()) return null;
        List<SegmentPlan> segments = legs.get(legIndex).getSegments();
        if (segmentIndex < 0 || segmentIndex >= segments.size()) return null;
        return segments.get(segmentIndex);
    }

    /** How far the spider's drawn shape can reach from its body position: body plus outstretched legs. */
    public static double reach(BodyPlan plan) {
        Double legReach = null;
        for (LegPlan leg : plan.getLegs()) {
            double sum = 0.0;
            for (SegmentPlan it : leg.getSegments()) sum += it.getLength();
            double value = leg.getAttachmentPosition().length() + sum;
            legReach = legReach == null ? value : Math.max(legReach, value);
        }
        return Math.max(legReach != null ? legReach : 0.0, BODY_REACH * plan.getScale());
    }

    /** Where along {@code from}→{@code to} (0..1) the segment first enters any piece, and that point; null if it misses. */
    public static Pair<Double, Vector> firstHit(List<ShapePiece> pieces, Vector from, Vector to) {
        double best = Double.MAX_VALUE;
        for (ShapePiece piece : pieces) {
            // into the piece's unit cube, where the slab test is simple
            Matrix4f inverse = new Matrix4f(piece.getTransform());
            if (inverse.determinant() == 0f) continue;
            inverse.invert();
            Vector3f start = inverse.transformPosition(new Vector3f(
                (float) (from.getX() - piece.getOrigin().getX()), (float) (from.getY() - piece.getOrigin().getY()), (float) (from.getZ() - piece.getOrigin().getZ())));
            Vector3f end = inverse.transformPosition(new Vector3f(
                (float) (to.getX() - piece.getOrigin().getX()), (float) (to.getY() - piece.getOrigin().getY()), (float) (to.getZ() - piece.getOrigin().getZ())));
            Double t = segmentEntersUnitCube(start, end);
            if (t == null) continue;
            if (t < best) best = t;
        }
        if (best == Double.MAX_VALUE) return null;
        return new Pair<>(best, from.clone().add(to.clone().subtract(from).multiply(best)));
    }

    /** Slab test against [0,1]³; returns the entry parameter in 0..1, or 0 when it starts inside. */
    private static Double segmentEntersUnitCube(Vector3f start, Vector3f end) {
        double enter = 0.0;
        double exit = 1.0;
        for (int axis = 0; axis <= 2; axis++) {
            double s = (double) start.get(axis);
            double d = (double) end.get(axis) - s;
            if (Math.abs(d) < 1e-12) {
                if (s < 0.0 || s > 1.0) return null;
                continue;
            }
            double t0 = (0.0 - s) / d;
            double t1 = (1.0 - s) / d;
            if (t0 > t1) {
                double swap = t0;
                t0 = t1;
                t1 = swap;
            }
            enter = Math.max(enter, t0);
            exit = Math.min(exit, t1);
            if (enter > exit) return null;
        }
        return enter;
    }
}
