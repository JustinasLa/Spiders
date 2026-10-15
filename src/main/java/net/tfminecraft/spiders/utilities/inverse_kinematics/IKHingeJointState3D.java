package net.tfminecraft.spiders.utilities.inverse_kinematics;

public final class IKHingeJointState3D implements IKJointPose3D {
    private double angle;

    public IKHingeJointState3D(double angle) {
        this.angle = angle;
    }

    public IKHingeJointState3D() {
        this(0.0);
    }

    public double getAngle() {
        return angle;
    }

    public void setAngle(double angle) {
        this.angle = angle;
    }

    @Override
    public IKHingeJointState3D clone() {
        return new IKHingeJointState3D(angle);
    }

    @Override
    public void copy(IKJointPose3D other) {
        if (!(other instanceof IKHingeJointState3D source)) {
            throw new IllegalArgumentException("IKHingeJointState3D.copy requires a matching state.");
        }
        angle = source.angle;
    }

    @Override
    public void lerpState(IKJointPose3D other, double t) {
        if (!(other instanceof IKHingeJointState3D target)) {
            throw new IllegalArgumentException("IKHingeJointState3D.lerpState requires a matching state.");
        }
        angle += IKChain3D.shortestAngleDelta(angle, target.angle) * t;
    }
}
