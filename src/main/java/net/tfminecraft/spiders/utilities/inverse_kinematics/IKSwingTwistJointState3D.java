package net.tfminecraft.spiders.utilities.inverse_kinematics;

public final class IKSwingTwistJointState3D implements IKJointPose3D {
    private double swingX;
    private double swingY;
    private double twist;

    public IKSwingTwistJointState3D(double swingX, double swingY, double twist) {
        this.swingX = swingX;
        this.swingY = swingY;
        this.twist = twist;
    }

    public IKSwingTwistJointState3D(double swingX, double swingY) {
        this(swingX, swingY, 0.0);
    }

    public IKSwingTwistJointState3D(double swingX) {
        this(swingX, 0.0);
    }

    public IKSwingTwistJointState3D() {
        this(0.0);
    }

    public double getSwingX() {
        return swingX;
    }

    public void setSwingX(double swingX) {
        this.swingX = swingX;
    }

    public double getSwingY() {
        return swingY;
    }

    public void setSwingY(double swingY) {
        this.swingY = swingY;
    }

    public double getTwist() {
        return twist;
    }

    public void setTwist(double twist) {
        this.twist = twist;
    }

    @Override
    public IKSwingTwistJointState3D clone() {
        return new IKSwingTwistJointState3D(swingX, swingY, twist);
    }

    @Override
    public void copy(IKJointPose3D other) {
        if (!(other instanceof IKSwingTwistJointState3D source)) {
            throw new IllegalArgumentException("IKSwingTwistJointState3D.copy requires a matching state.");
        }
        swingX = source.swingX;
        swingY = source.swingY;
        twist = source.twist;
    }

    @Override
    public void lerpState(IKJointPose3D other, double t) {
        if (!(other instanceof IKSwingTwistJointState3D target)) {
            throw new IllegalArgumentException("IKSwingTwistJointState3D.lerpState requires a matching state.");
        }
        swingX += (target.swingX - swingX) * t;
        swingY += (target.swingY - swingY) * t;
        twist += (target.twist - twist) * t;
    }
}
