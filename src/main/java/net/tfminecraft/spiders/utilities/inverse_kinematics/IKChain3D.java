package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class IKChain3D {
    private static final double REST_BIAS = 0.035;

    private static final double SWING_EPSILON = 1e-9;

    private final List<IKChainSegment3D> segments;

    public IKChain3D(List<IKChainSegment3D> segments) {
        this.segments = segments;
    }

    public List<IKChainSegment3D> getSegments() {
        return segments;
    }

    public double getTotalLength() {
        double sum = 0.0;
        for (IKChainSegment3D segment : segments) sum += segment.getLength();
        return sum;
    }

    public IKChainPose3D createPose(Vector position, Quaternionf rotation) {
        List<IKJointPose3D> jointStates = new ArrayList<>(segments.size());
        for (IKChainSegment3D segment : segments) jointStates.add(restState(segment.getJoint()));

        return new IKChainPose3D(
            position.clone(),
            new Quaternionf(rotation),
            jointStates
        );
    }

    public List<IKChainWorldNode3D> getWorldNodes(IKChainPose3D pose) {
        List<IKChainWorldNode3D> nodes = new ArrayList<>();
        nodes.add(new IKChainWorldNode3D(pose.getPosition().clone(), new Quaternionf(pose.getOrientation()), new Quaternionf(pose.getOrientation())));

        Vector cursor = pose.getPosition().clone();
        Quaternionf cursorRotation = new Quaternionf(pose.getOrientation());

        for (int index = 0; index < segments.size(); index++) {
            IKChainSegment3D segment = segments.get(index);
            IKJointPose3D jointState = pose.getSegments().get(index);

            Quaternionf localRotation = segment.getJoint().rotation(jointState).normalize();

            cursorRotation.mul(localRotation);
            cursor.add(Maths.rotate(getCHAIN_AXIS(), cursorRotation).multiply(segment.getLength()));

            nodes.add(new IKChainWorldNode3D(cursor.clone(), new Quaternionf(cursorRotation), localRotation));
        }

        return nodes;
    }

    public static Vector getCHAIN_AXIS() {
        return Maths.forwardVector();
    }

    public static IKJointPose3D restState(IKJoint3D joint) {
        return switch (joint) {
            case IKHingeJoint3D hinge -> {
                double angle;
                if (hinge.getMinAngle() >= 0.0) {
                    angle = hinge.getMinAngle() + REST_BIAS;
                } else if (hinge.getMaxAngle() <= 0.0) {
                    angle = hinge.getMaxAngle() - REST_BIAS;
                } else {
                    // Straddles zero, so bias positive: any bias will do, but it must not
                    // be exactly zero
                    angle = REST_BIAS;
                }
                yield new IKHingeJointState3D(coerceIn(angle, hinge.getMinAngle(), hinge.getMaxAngle()));
            }

            case IKSwingTwistJoint3D swingTwist -> new IKSwingTwistJointState3D();
        };
    }

    public static Quaternionf swingRotation(double x, double y) {
        double angle = Math.hypot(x, y);
        if (angle <= SWING_EPSILON) return new Quaternionf();
        return new Quaternionf().rotationAxis((float) angle, (float) (x / angle), (float) (y / angle), 0f);
    }

    public static double swingComponentLimit(double other, double maxSwing) {
        return Math.sqrt(Math.max(maxSwing * maxSwing - other * other, 0.0));
    }

    public static Quaternionf quaternionFromTo(Vector from, Vector to, Vector referenceAxis) {
        Vector3f a = from.toVector3f().normalize();
        Vector3f b = to.toVector3f().normalize();
        float dot = coerceIn(a.dot(b), -1f, 1f);

        if (dot >= 1f - 1e-6f) return new Quaternionf();

        if (dot <= -1f + 1e-6f) {
            Vector3f axis = new Vector3f(a).cross(referenceAxis.toVector3f());
            if (axis.lengthSquared() < 1e-12f) {
                axis.set(Math.abs(a.x) < 0.9f ? new Vector3f(1f, 0f, 0f) : new Vector3f(0f, 1f, 0f));
                axis.sub(new Vector3f(a).mul(a.dot(axis)));
            }
            return new Quaternionf().fromAxisAngleRad(axis.normalize(), (float) Math.PI);
        }

        Vector3f axis = new Vector3f(a).cross(b);
        return new Quaternionf().fromAxisAngleRad(axis.normalize(), (float) Math.acos(dot));
    }

    static double shortestAngleDelta(double from, double to) {
        double delta = to - from;
        while (delta > Math.PI) delta -= Math.PI * 2;
        while (delta <= -Math.PI) delta += Math.PI * 2;
        return delta;
    }

    // Kotlin coerceIn semantics (signed zeros and NaN behave exactly as in the original).
    static double coerceIn(double value, double minimumValue, double maximumValue) {
        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + maximumValue + " is less than minimum " + minimumValue + ".");
        }
        if (value < minimumValue) return minimumValue;
        if (value > maximumValue) return maximumValue;
        return value;
    }

    static float coerceIn(float value, float minimumValue, float maximumValue) {
        if (minimumValue > maximumValue) {
            throw new IllegalArgumentException("Cannot coerce value to an empty range: maximum " + maximumValue + " is less than minimum " + minimumValue + ".");
        }
        if (value < minimumValue) return minimumValue;
        if (value > maximumValue) return maximumValue;
        return value;
    }
}
