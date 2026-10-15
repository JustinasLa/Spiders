package net.tfminecraft.spiders.utilities.inverse_kinematics;

public final class JointParameterDescriptor {
    private final int jointIndex;
    private final JointParameter parameter;

    public JointParameterDescriptor(int jointIndex, JointParameter parameter) {
        this.jointIndex = jointIndex;
        this.parameter = parameter;
    }

    public int getJointIndex() {
        return jointIndex;
    }

    public JointParameter getParameter() {
        return parameter;
    }
}
