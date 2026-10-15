package net.tfminecraft.spiders.utilities.inverse_kinematics;

@FunctionalInterface
public interface IKSolver3D {
    void solve(IKChain3D chain, IKChainPose3D pose, IKTarget3D target);
}
