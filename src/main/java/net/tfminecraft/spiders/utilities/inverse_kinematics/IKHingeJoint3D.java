package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public final class IKHingeJoint3D implements IKJoint3D {
    private final double minAngle;
    private final double maxAngle;
    private final Vector axis;
    private final Vector reference;

    public IKHingeJoint3D(double minAngle, double maxAngle, Vector axis, Vector reference) {
        this.minAngle = minAngle;
        this.maxAngle = maxAngle;
        this.axis = axis;
        this.reference = reference;

        if (!(minAngle <= maxAngle)) throw new IllegalArgumentException("IKHingeJoint3D.minAngle cannot be greater than maxAngle.");
        if (!(axis.lengthSquared() > 0.0)) throw new IllegalArgumentException("IKHingeJoint3D.axis must be non-zero.");
        if (!(reference.lengthSquared() > 0.0)) throw new IllegalArgumentException("IKHingeJoint3D.reference must be non-zero.");
    }

    public double getMinAngle() {
        return minAngle;
    }

    public double getMaxAngle() {
        return maxAngle;
    }

    public Vector getAxis() {
        return axis;
    }

    public Vector getReference() {
        return reference;
    }

    @Override
    public Quaternionf rotation(IKJointPose3D state) {
        if (!(state instanceof IKHingeJointState3D pose)) {
            throw new IllegalArgumentException("IKHingeJoint3D requires a matching IKHingeJointState3D.");
        }
        return new Quaternionf().fromAxisAngleRad(axis.toVector3f(), (float) pose.getAngle());
    }
}
