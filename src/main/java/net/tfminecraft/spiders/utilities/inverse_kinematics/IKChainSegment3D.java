package net.tfminecraft.spiders.utilities.inverse_kinematics;

public final class IKChainSegment3D {
    private final double length;
    private final IKJoint3D joint;

    public IKChainSegment3D(double length, IKJoint3D joint) {
        this.length = length;
        this.joint = joint;
    }

    public double getLength() {
        return length;
    }

    public IKJoint3D getJoint() {
        return joint;
    }
}
