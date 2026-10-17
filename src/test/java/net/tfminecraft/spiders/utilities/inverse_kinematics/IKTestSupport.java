package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Shared chains and assertions for the inverse-kinematics tests. */
final class IKTestSupport {
    static final double ROOT_MAX_SWING = 0.3 * Math.PI;
    static final double ROOT_TWIST = 0.075 * Math.PI;
    static final double KNEE_LEEWAY = 0.65 * Math.PI;

    private IKTestSupport() {
    }

    /** The spider leg's hip: a swing cone with a little twist, as in {@code Presets.hipJoint()}. */
    static IKSwingTwistJoint3D hip() {
        return new IKSwingTwistJoint3D(ROOT_MAX_SWING, -ROOT_TWIST, ROOT_TWIST, new Vector(0, 1, 0));
    }

    /** The spider leg's knee: a hinge about +X that only bends one way, as in {@code Presets.kneeJoint()}. */
    static IKHingeJoint3D knee() {
        return new IKHingeJoint3D(0.0, KNEE_LEEWAY, new Vector(1, 0, 0), new Vector(0, 0, 1));
    }

    static IKHingeJoint3D hinge(double min, double max) {
        return new IKHingeJoint3D(min, max, new Vector(1, 0, 0), new Vector(0, 0, 1));
    }

    static IKChain3D chain(double length, IKJoint3D... joints) {
        List<IKChainSegment3D> segments = new ArrayList<>();
        for (IKJoint3D joint : joints) segments.add(new IKChainSegment3D(length, joint));
        return new IKChain3D(segments);
    }

    /** A hip and two knees, 1 block each: the default spider leg. */
    static IKChain3D leg() {
        return chain(1.0, hip(), knee(), knee());
    }

    static IKChainPose3D restPose(IKChain3D chain) {
        return chain.createPose(new Vector(), new Quaternionf());
    }

    static Vector endEffector(IKChain3D chain, IKChainPose3D pose) {
        List<IKChainWorldNode3D> nodes = chain.getWorldNodes(pose);
        return nodes.get(nodes.size() - 1).getPosition();
    }

    static void assertVector(double x, double y, double z, Vector actual, double tolerance) {
        assertEquals(x, actual.getX(), tolerance, "x of " + actual);
        assertEquals(y, actual.getY(), tolerance, "y of " + actual);
        assertEquals(z, actual.getZ(), tolerance, "z of " + actual);
    }

    static void assertVector(Vector expected, Vector actual, double tolerance) {
        assertVector(expected.getX(), expected.getY(), expected.getZ(), actual, tolerance);
    }

    static Vector rotated(Quaternionf rotation, double x, double y, double z) {
        Vector3f out = new Vector3f((float) x, (float) y, (float) z).rotate(rotation);
        return new Vector(out.x, out.y, out.z);
    }

    /** Angle between two rotations, treating q and -q as the same. */
    static double angleBetween(Quaternionf a, Quaternionf b) {
        return 2 * Math.acos(Math.min(1.0, Math.abs(a.dot(b))));
    }
}
