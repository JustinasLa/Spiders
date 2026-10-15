package net.tfminecraft.spiders.utilities.inverse_kinematics;

public sealed interface IKJointPose3D permits IKSwingTwistJointState3D, IKHingeJointState3D {
    IKJointPose3D clone();

    void copy(IKJointPose3D other);

    void lerpState(IKJointPose3D other, double t);
}
