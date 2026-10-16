package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.maths.Maths;
import java.util.ArrayList;
import java.util.List;

public final class DampedLeastSquaresIKSolver3D {
    private DampedLeastSquaresIKSolver3D() {
    }

    public static IKSolver3D createDampedLeastSquaresIKSolver3D(IKEvaluator3D evaluator, DampedLeastSquaresOptions options) {
        return (chain, pose, target) -> solve(chain, pose, target, evaluator, options);
    }

    public static IKSolver3D createDampedLeastSquaresIKSolver3D(IKEvaluator3D evaluator) {
        return createDampedLeastSquaresIKSolver3D(evaluator, new DampedLeastSquaresOptions());
    }

    public static IKSolver3D createDampedLeastSquaresIKSolver3D() {
        return createDampedLeastSquaresIKSolver3D(IKEvaluator3D.createIKEvaluator3D());
    }

    private static void solve(
        IKChain3D chain,
        IKChainPose3D pose,
        IKTarget3D ikTarget,
        IKEvaluator3D evaluator,
        DampedLeastSquaresOptions options
    ) {
        LeastSquaresProblem<IKChainPose3D, JointParameterDescriptor, IKEvaluation3D> problem =
            new LeastSquaresProblem<>() {
                @Override
                public IKChainPose3D cloneState(IKChainPose3D state) {
                    return state.clone();
                }

                @Override
                public void copyState(IKChainPose3D destination, IKChainPose3D source) {
                    destination.copy(source);
                }

                @Override
                public IKEvaluation3D evaluateState(IKChainPose3D state) {
                    return evaluator.evaluate(chain, state, ikTarget);
                }

                @Override
                public List<JointParameterDescriptor> listParameters(IKChainPose3D state) {
                    return createParameterDescriptors(state.getSegments());
                }

                @Override
                public double perturbParameter(IKChainPose3D state, JointParameterDescriptor parameter, double delta) {
                    return DampedLeastSquaresIKSolver3D.perturbParameter(chain, state, parameter, delta);
                }

                @Override
                public double clampDelta(IKChainPose3D state, JointParameterDescriptor parameter, double delta) {
                    return DampedLeastSquaresIKSolver3D.clampDelta(chain, state, parameter, delta);
                }

                @Override
                public void applyStep(
                    IKChainPose3D state,
                    List<JointParameterDescriptor> parameters,
                    double[] step,
                    double scale
                ) {
                    DampedLeastSquaresIKSolver3D.applyStep(chain, state, parameters, step, scale);
                }
            };

        SolveDampedLeastSquares.solveDampedLeastSquares(pose, problem, options);
    }

    private static List<JointParameterDescriptor> createParameterDescriptors(List<IKJointPose3D> jointStates) {
        List<JointParameterDescriptor> descriptors = new ArrayList<>();

        for (int index = 0; index < jointStates.size(); index++) {
            switch (jointStates.get(index)) {
                case IKHingeJointState3D hinge -> {
                    descriptors.add(new JointParameterDescriptor(index, JointParameter.Angle));
                }

                case IKSwingTwistJointState3D swingTwist -> {
                    descriptors.add(new JointParameterDescriptor(index, JointParameter.SwingX));
                    descriptors.add(new JointParameterDescriptor(index, JointParameter.SwingY));
                    descriptors.add(new JointParameterDescriptor(index, JointParameter.Twist));
                }
            }
        }

        return descriptors;
    }

    private static double perturbParameter(
        IKChain3D chain,
        IKChainPose3D pose,
        JointParameterDescriptor descriptor,
        double delta
    ) {
        double appliedDelta = clampDelta(chain, pose, descriptor, delta);
        applyDelta(chain, pose, descriptor, appliedDelta);
        return appliedDelta;
    }

    private static double clampDelta(
        IKChain3D chain,
        IKChainPose3D pose,
        JointParameterDescriptor descriptor,
        double delta
    ) {
        IKJoint3D joint = chain.getSegments().get(descriptor.getJointIndex()).getJoint();
        IKJointPose3D jointPose = pose.getSegments().get(descriptor.getJointIndex());

        if (joint instanceof IKHingeJoint3D hinge && jointPose instanceof IKHingeJointState3D hingePose) {
            return Maths.clamp(hingePose.getAngle() + delta, hinge.getMinAngle(), hinge.getMaxAngle()) - hingePose.getAngle();
        }

        if (joint instanceof IKSwingTwistJoint3D swingTwist && jointPose instanceof IKSwingTwistJointState3D swingTwistPose) {
            return switch (descriptor.getParameter()) {
                // The swing is limited to a cone, so each component's range depends on the other.
                case SwingX -> {
                    double limit = IKChain3D.swingComponentLimit(swingTwistPose.getSwingY(), swingTwist.getMaxSwing());
                    yield Maths.clamp(swingTwistPose.getSwingX() + delta, -limit, limit) - swingTwistPose.getSwingX();
                }

                case SwingY -> {
                    double limit = IKChain3D.swingComponentLimit(swingTwistPose.getSwingX(), swingTwist.getMaxSwing());
                    yield Maths.clamp(swingTwistPose.getSwingY() + delta, -limit, limit) - swingTwistPose.getSwingY();
                }

                case Twist -> Maths.clamp(swingTwistPose.getTwist() + delta, swingTwist.getMinTwist(), swingTwist.getMaxTwist()) - swingTwistPose.getTwist();

                case Angle -> 0.0;
            };
        }

        throw new IllegalStateException("IKSolver requires matching topology.");
    }

    private static void applyDelta(
        IKChain3D chain,
        IKChainPose3D pose,
        JointParameterDescriptor descriptor,
        double delta
    ) {
        IKJoint3D joint = chain.getSegments().get(descriptor.getJointIndex()).getJoint();
        IKJointPose3D jointPose = pose.getSegments().get(descriptor.getJointIndex());

        if (joint instanceof IKHingeJoint3D && jointPose instanceof IKHingeJointState3D hingePose) {
            hingePose.setAngle(hingePose.getAngle() + delta);
            return;
        }

        if (joint instanceof IKSwingTwistJoint3D && jointPose instanceof IKSwingTwistJointState3D swingTwistPose) {
            switch (descriptor.getParameter()) {
                case SwingX -> swingTwistPose.setSwingX(swingTwistPose.getSwingX() + delta);
                case SwingY -> swingTwistPose.setSwingY(swingTwistPose.getSwingY() + delta);
                case Twist -> swingTwistPose.setTwist(swingTwistPose.getTwist() + delta);
                case Angle -> {
                }
            }
            return;
        }

        throw new IllegalStateException("IKSolver requires matching topology.");
    }

    private static void applyStep(
        IKChain3D chain,
        IKChainPose3D pose,
        List<JointParameterDescriptor> descriptors,
        double[] step,
        double scale
    ) {
        for (int index = 0; index < descriptors.size(); index++) {
            applyDelta(chain, pose, descriptors.get(index), clampDelta(chain, pose, descriptors.get(index), step[index] * scale));
        }
    }
}
