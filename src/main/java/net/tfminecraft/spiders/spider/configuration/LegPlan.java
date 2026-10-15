package net.tfminecraft.spiders.spider.configuration;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.List;

public class LegPlan {
    private Vector attachmentPosition;
    private Vector restPosition;
    private List<SegmentPlan> segments;
    //val restOrientation: Quaternionf = restOrientationFor(attachmentPosition, restPosition),
    private final Quaternionf restOrientation;

    public LegPlan(Vector attachmentPosition, Vector restPosition, List<SegmentPlan> segments, Quaternionf orientation) {
        this.attachmentPosition = attachmentPosition;
        this.restPosition = restPosition;
        this.segments = segments;
        this.restOrientation = restOrientationFor(attachmentPosition, restPosition).mul(orientation);
    }

    public Vector getAttachmentPosition() {
        return attachmentPosition;
    }

    public void setAttachmentPosition(Vector attachmentPosition) {
        this.attachmentPosition = attachmentPosition;
    }

    public Vector getRestPosition() {
        return restPosition;
    }

    public void setRestPosition(Vector restPosition) {
        this.restPosition = restPosition;
    }

    public List<SegmentPlan> getSegments() {
        return segments;
    }

    public void setSegments(List<SegmentPlan> segments) {
        this.segments = segments;
    }

    public Quaternionf getRestOrientation() {
        return restOrientation;
    }

    private static Quaternionf aimOrientation(Vector direction) {
        Vector d = direction.clone().normalize();

        // Inverse of rotationYXZ(yaw, pitch, 0) applied to the chain axis:
        //   rotationYXZ(y, x, 0) * (0, 0, 1) == (cos x sin y, -sin x, cos x cos y)
        double yaw = Math.atan2(d.getX(), d.getZ());
        double pitch = -Math.asin(coerceIn(d.getY(), -1.0, 1.0));

        return new Quaternionf().rotationYXZ((float) yaw, (float) pitch, 0f);
    }

    private static Quaternionf restOrientationFor(Vector attachment, Vector rest) {
        Vector direction = rest.clone().subtract(attachment);
        return direction.lengthSquared() < 1e-9 ? new Quaternionf() : aimOrientation(direction);
    }

    /** Kotlin's {@code Double.coerceIn}: NaN and signed zeros pass through unchanged. */
    private static double coerceIn(double value, double min, double max) {
        if (min > max) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + max + " is less than minimum " + min + ".");
        }
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
