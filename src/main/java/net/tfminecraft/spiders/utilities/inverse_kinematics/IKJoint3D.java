package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.joml.Quaternionf;

public sealed interface IKJoint3D permits IKSwingTwistJoint3D, IKHingeJoint3D {
    Quaternionf rotation(IKJointPose3D state);
}
