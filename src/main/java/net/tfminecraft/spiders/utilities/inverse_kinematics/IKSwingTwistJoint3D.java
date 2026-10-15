package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public final class IKSwingTwistJoint3D implements IKJoint3D {
    private final double maxSwing;
    private final double minTwist;
    private final double maxTwist;
    private final Vector referenceAxis;

    public IKSwingTwistJoint3D(double maxSwing, double minTwist, double maxTwist, Vector referenceAxis) {
        this.maxSwing = maxSwing;
        this.minTwist = minTwist;
        this.maxTwist = maxTwist;
        this.referenceAxis = referenceAxis;

        if (!(maxSwing >= 0.0)) throw new IllegalArgumentException("IKSwingTwistJoint3D.maxSwing cannot be negative.");
        if (!(minTwist <= maxTwist)) throw new IllegalArgumentException("IKSwingTwistJoint3D.minTwist cannot be greater than maxTwist.");
        if (!(referenceAxis.lengthSquared() > 0.0)) throw new IllegalArgumentException("IKSwingTwistJoint3D.referenceAxis must be non-zero.");
    }

    public double getMaxSwing() {
        return maxSwing;
    }

    public double getMinTwist() {
        return minTwist;
    }

    public double getMaxTwist() {
        return maxTwist;
    }

    public Vector getReferenceAxis() {
        return referenceAxis;
    }

    @Override
    public Quaternionf rotation(IKJointPose3D state) {
        if (!(state instanceof IKSwingTwistJointState3D pose)) {
            throw new IllegalArgumentException("IKSwingTwistJoint3D requires a matching IKSwingTwistJointState3D.");
        }

        Quaternionf swingRotation = IKChain3D.swingRotation(pose.getSwingX(), pose.getSwingY());
        Quaternionf twistRotation = new Quaternionf().fromAxisAngleRad(IKChain3D.getCHAIN_AXIS().toVector3f(), (float) pose.getTwist());
        return swingRotation.mul(twistRotation).normalize();
    }
}
