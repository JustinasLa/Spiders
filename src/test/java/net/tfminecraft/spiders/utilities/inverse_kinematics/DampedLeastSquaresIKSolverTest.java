package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.KNEE_LEEWAY;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.ROOT_MAX_SWING;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.ROOT_TWIST;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.angleBetween;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.assertVector;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.chain;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.endEffector;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.hinge;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.hip;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.knee;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.restPose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DampedLeastSquaresIKSolverTest {
    /** The leg solver as the spiders use it: 1 cm tolerance, default options. */
    private static final IKSolver3D LEG_SOLVER =
        DampedLeastSquaresIKSolver3D.createDampedLeastSquaresIKSolver3D(IKEvaluator3D.createIKEvaluator3D(0.01));

    private static IKSwingTwistJointState3D swing(IKChainPose3D pose, int index) {
        return (IKSwingTwistJointState3D) pose.getSegments().get(index);
    }

    private static IKHingeJointState3D bend(IKChainPose3D pose, int index) {
        return (IKHingeJointState3D) pose.getSegments().get(index);
    }

    private static void assertWithinLimits(IKChain3D chain, IKChainPose3D pose) {
        for (int index = 0; index < chain.getSegments().size(); index++) {
            IKJoint3D joint = chain.getSegments().get(index).getJoint();
            IKJointPose3D state = pose.getSegments().get(index);
            if (joint instanceof IKHingeJoint3D hinge) {
                double angle = ((IKHingeJointState3D) state).getAngle();
                assertTrue(angle >= hinge.getMinAngle() - 1e-12 && angle <= hinge.getMaxAngle() + 1e-12,
                    "hinge " + index + " at " + angle + " outside [" + hinge.getMinAngle() + ", " + hinge.getMaxAngle() + "]");
            } else {
                IKSwingTwistJoint3D swingTwist = (IKSwingTwistJoint3D) joint;
                IKSwingTwistJointState3D swing = (IKSwingTwistJointState3D) state;
                double swingAngle = Math.hypot(swing.getSwingX(), swing.getSwingY());
                assertTrue(swingAngle <= swingTwist.getMaxSwing() + 1e-9, "swing " + index + " of " + swingAngle + " outside the cone");
                assertTrue(swing.getTwist() >= swingTwist.getMinTwist() - 1e-12 && swing.getTwist() <= swingTwist.getMaxTwist() + 1e-12,
                    "twist " + index + " at " + swing.getTwist());
            }
        }
    }

    /** A target the leg can certainly reach: where its tip is in a legal pose. */
    private static Vector reachable(IKChain3D chain, IKChainPose3D root, double swingX, double swingY, double knee1, double knee2) {
        IKChainPose3D pose = root.clone();
        swing(pose, 0).setSwingX(swingX);
        swing(pose, 0).setSwingY(swingY);
        bend(pose, 1).setAngle(knee1);
        bend(pose, 2).setAngle(knee2);
        return endEffector(chain, pose);
    }

    @Test
    @DisplayName("the leg solver brings the foot onto reachable targets from rest, within limits")
    void reachesReachableTargets() {
        IKChain3D leg = IKTestSupport.leg();
        double[][] poses = {
            {0.3, -0.2, 0.8, 0.6},
            {-0.5, 0.4, 1.2, 0.3},
            {0.0, 0.6, 0.4, 1.5},
            {0.7, 0.1, 0.1, 0.1},
        };
        for (double[] legal : poses) {
            IKChainPose3D pose = restPose(leg);
            Vector target = reachable(leg, pose, legal[0], legal[1], legal[2], legal[3]);
            double before = endEffector(leg, pose).distance(target);

            LEG_SOLVER.solve(leg, pose, new IKTarget3D(target));

            double after = endEffector(leg, pose).distance(target);
            assertTrue(after <= 0.01, "foot " + after + " from " + target + " (started " + before + " away)");
            assertWithinLimits(leg, pose);
        }
    }

    @Test
    @DisplayName("the solver works in world space: the root's position and orientation carry the chain")
    void worksInWorldSpace() {
        IKChain3D leg = IKTestSupport.leg();
        IKChainPose3D pose = leg.createPose(new Vector(100, 64, -20), new Quaternionf().rotateY(2.0f).rotateX(-0.3f));
        Vector target = reachable(leg, pose, -0.2, 0.3, 0.9, 0.7);

        LEG_SOLVER.solve(leg, pose, new IKTarget3D(target));

        assertTrue(endEffector(leg, pose).distance(target) <= 0.01);
        assertVector(100, 64, -20, pose.getPosition(), 0.0);
        assertWithinLimits(leg, pose);
    }

    @Test
    @DisplayName("tracking a moving target from the last solution needs only small corrections")
    void tracksMovingTarget() {
        IKChain3D leg = IKTestSupport.leg();
        IKChainPose3D pose = restPose(leg);
        IKTarget3D target = new IKTarget3D(reachable(leg, pose, 0.2, 0.2, 0.9, 0.9));
        LEG_SOLVER.solve(leg, pose, target);

        for (int step = 0; step < 10; step++) {
            target.getPosition().add(new Vector(0.02, 0, 0.01));
            LEG_SOLVER.solve(leg, pose, target);
            assertTrue(endEffector(leg, pose).distance(target.getPosition()) <= 0.01, "lost the target at step " + step);
            assertWithinLimits(leg, pose);
        }
    }

    @Test
    @DisplayName("a hinge stops at its limit when the target lies beyond it")
    void hingeStopsAtLimit() {
        IKChain3D arm = chain(1.0, hinge(0.0, Math.PI / 4));
        IKChainPose3D pose = restPose(arm);
        // straight down needs +90 degrees about X but the hinge stops at 45
        LEG_SOLVER.solve(arm, pose, new IKTarget3D(new Vector(0, -1, 0)));

        assertEquals(Math.PI / 4, bend(pose, 0).getAngle(), 1e-9);
        assertVector(0, -Math.sqrt(0.5), Math.sqrt(0.5), endEffector(arm, pose), 1e-6);
    }

    @Test
    @DisplayName("a hinge that cannot bend towards the target stays at the near limit")
    void hingeCannotBendBackwards() {
        IKChain3D arm = chain(1.0, hinge(0.0, Math.PI / 2));
        IKChainPose3D pose = restPose(arm);
        // up needs a negative angle; the closest legal pose is straight
        LEG_SOLVER.solve(arm, pose, new IKTarget3D(new Vector(0, 1, 0.2)));

        assertEquals(0.0, bend(pose, 0).getAngle(), 1e-9);
        assertWithinLimits(arm, pose);
    }

    @Test
    @DisplayName("a swing stops on its cone, leaning towards a target outside it")
    void swingStaysInCone() {
        IKChain3D arm = chain(1.0, new IKSwingTwistJoint3D(0.3, -0.1, 0.1, new Vector(0, 1, 0)));
        IKChainPose3D pose = restPose(arm);
        // 90 degrees off axis, straight up
        LEG_SOLVER.solve(arm, pose, new IKTarget3D(new Vector(0, 1, 0)));

        IKSwingTwistJointState3D state = swing(pose, 0);
        assertEquals(0.3, Math.hypot(state.getSwingX(), state.getSwingY()), 1e-3);
        assertWithinLimits(arm, pose);
        // the tip leans the full 0.3 rad off +Z, straight towards the target
        assertVector(0, Math.sin(0.3), Math.cos(0.3), endEffector(arm, pose), 1e-3);
    }

    @Test
    @DisplayName("a diagonal target outside the cone still leaves the swing on the cone, in the target's quadrant")
    void diagonalSwingStaysInCone() {
        IKChain3D arm = chain(1.0, new IKSwingTwistJoint3D(0.3, -0.1, 0.1, new Vector(0, 1, 0)));
        IKChainPose3D pose = restPose(arm);
        Vector target = new Vector(1, 1, 0);
        double before = endEffector(arm, pose).distance(target);
        LEG_SOLVER.solve(arm, pose, new IKTarget3D(target));

        IKSwingTwistJointState3D state = swing(pose, 0);
        assertEquals(0.3, Math.hypot(state.getSwingX(), state.getSwingY()), 1e-3);
        assertWithinLimits(arm, pose);
        Vector tip = endEffector(arm, pose);
        assertTrue(tip.getX() >= -1e-9 && tip.getY() >= -1e-9, "leaning towards the target, got " + tip);
        assertTrue(tip.distance(target) < before);
        // Note: the components are clamped one at a time, so the first to hit the rim can use the whole
        // cone and pin the other at zero; the swing then rests on an axis rather than on the diagonal.
    }

    @Test
    @DisplayName("an out-of-reach target straightens the chain and points it at the target")
    void outOfReach() {
        IKChain3D arm = chain(1.0, new IKSwingTwistJoint3D(Math.PI / 2, 0, 0, new Vector(0, 1, 0)), hinge(-1, 1));
        IKChainPose3D pose = restPose(arm);
        Vector target = new Vector(0, 3, 4);
        LEG_SOLVER.solve(arm, pose, new IKTarget3D(target));

        Vector tip = endEffector(arm, pose);
        assertEquals(2.0, tip.length(), 0.01, "fully extended");
        assertEquals(3.0, tip.distance(target), 0.01, "on the line to the target");
    }

    @Test
    @DisplayName("an orientation target is matched together with the position")
    void matchesOrientation() {
        IKChain3D arm = chain(1.0, hip());
        IKChainPose3D pose = restPose(arm);
        IKChainPose3D goal = pose.clone();
        swing(goal, 0).setSwingX(0.2);
        swing(goal, 0).setSwingY(-0.3);
        swing(goal, 0).setTwist(0.15);
        List<IKChainWorldNode3D> goalNodes = arm.getWorldNodes(goal);
        IKTarget3D target = new IKTarget3D(goalNodes.get(1).getPosition(), goalNodes.get(1).getWorldRotation());

        IKSolver3D solver = DampedLeastSquaresIKSolver3D.createDampedLeastSquaresIKSolver3D(
            IKEvaluator3D.createIKEvaluator3D(0.01, 0.01, 1.0, 1.0),
            new DampedLeastSquaresOptions(50)
        );
        solver.solve(arm, pose, target);

        IKChainWorldNode3D tip = arm.getWorldNodes(pose).get(1);
        assertTrue(tip.getPosition().distance(target.getPosition()) <= 0.01);
        assertTrue(angleBetween(tip.getWorldRotation(), target.getOrientation()) <= 0.02);
        assertEquals(0.15, swing(pose, 0).getTwist(), 0.02);
    }

    @Test
    @DisplayName("an already solved pose is left exactly as it was")
    void alreadySolved() {
        IKChain3D leg = IKTestSupport.leg();
        IKChainPose3D pose = restPose(leg);
        IKChainPose3D before = pose.clone();

        DampedLeastSquaresIKSolver3D.createDampedLeastSquaresIKSolver3D().solve(leg, pose, new IKTarget3D(endEffector(leg, pose)));

        for (int index = 0; index < 3; index++) {
            if (index == 0) {
                assertEquals(0.0, swing(pose, 0).getSwingX(), 0.0);
                assertEquals(0.0, swing(pose, 0).getTwist(), 0.0);
            } else {
                assertEquals(bend(before, index).getAngle(), bend(pose, index).getAngle(), 0.0);
            }
        }
    }

    @Test
    @DisplayName("a pose that does not match the chain is refused")
    void mismatchedTopology() {
        IKChain3D hinged = chain(1.0, knee());
        IKChainPose3D swingPose = restPose(chain(1.0, hip()));
        IKTarget3D target = new IKTarget3D(new Vector(0, 0, 5));

        // the default evaluator notices while walking the chain
        assertThrows(IllegalArgumentException.class, () -> LEG_SOLVER.solve(hinged, swingPose, target));

        // an evaluator that never walks it is caught when the solver sizes a step
        IKEvaluator3D blind = (chain, pose, ignored) -> new IKEvaluation3D(1, 0, 1, false, new double[]{1});
        IKSolver3D solver = DampedLeastSquaresIKSolver3D.createDampedLeastSquaresIKSolver3D(blind);
        assertThrows(IllegalStateException.class, () -> solver.solve(hinged, swingPose, target));
        assertThrows(IllegalStateException.class, () -> solver.solve(chain(1.0, hip()), restPose(hinged), target));
    }

    @Test
    @DisplayName("hinges have one parameter, swing-twist joints three")
    void parameterDescriptors() {
        List<JointParameterDescriptor> descriptors = DampedLeastSquaresIKSolver3D.createParameterDescriptors(
            restPose(IKTestSupport.leg()).getSegments());

        assertEquals(5, descriptors.size());
        assertEquals(List.of(JointParameter.SwingX, JointParameter.SwingY, JointParameter.Twist, JointParameter.Angle, JointParameter.Angle),
            descriptors.stream().map(JointParameterDescriptor::getParameter).toList());
        assertEquals(List.of(0, 0, 0, 1, 2), descriptors.stream().map(JointParameterDescriptor::getJointIndex).toList());
    }

    @Test
    @DisplayName("each swing component's range shrinks as the other uses up the cone, and twist has its own range")
    void swingClampIsConeShaped() {
        IKChain3D arm = chain(1.0, new IKSwingTwistJoint3D(0.5, -0.2, 0.3, new Vector(0, 1, 0)));
        IKChainPose3D pose = restPose(arm);
        swing(pose, 0).setSwingX(0.3);

        // with x at 0.3 of a 0.5 cone, y can reach +-0.4
        assertEquals(0.4, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.SwingY), 1.0), 1e-12);
        assertEquals(-0.4, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.SwingY), -1.0), 1e-12);
        assertEquals(0.2, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.SwingX), 1.0), 1e-12);
        assertEquals(0.05, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.SwingX), 0.05), 1e-12);
        assertEquals(0.3, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.Twist), 1.0), 1e-12);
        assertEquals(-0.2, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.Twist), -1.0), 1e-12);
        // a hinge angle means nothing to a swing-twist joint
        assertEquals(0.0, DampedLeastSquaresIKSolver3D.clampDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.Angle), 1.0), 0.0);
    }

    @Test
    @DisplayName("applying a delta moves only the named parameter, and refuses a mismatched pose")
    void applyDelta() {
        IKChain3D arm = chain(1.0, hip(), knee());
        IKChainPose3D pose = restPose(arm);

        DampedLeastSquaresIKSolver3D.applyDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.SwingX), 0.1);
        DampedLeastSquaresIKSolver3D.applyDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.SwingY), -0.2);
        DampedLeastSquaresIKSolver3D.applyDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.Twist), 0.05);
        DampedLeastSquaresIKSolver3D.applyDelta(arm, pose, new JointParameterDescriptor(0, JointParameter.Angle), 9.0);
        DampedLeastSquaresIKSolver3D.applyDelta(arm, pose, new JointParameterDescriptor(1, JointParameter.Angle), 0.5);

        assertEquals(0.1, swing(pose, 0).getSwingX(), 1e-12);
        assertEquals(-0.2, swing(pose, 0).getSwingY(), 1e-12);
        assertEquals(0.05, swing(pose, 0).getTwist(), 1e-12);
        assertEquals(0.535, bend(pose, 1).getAngle(), 1e-12);

        IKChainPose3D swapped = restPose(chain(1.0, knee(), hip()));
        assertThrows(IllegalStateException.class,
            () -> DampedLeastSquaresIKSolver3D.applyDelta(arm, swapped, new JointParameterDescriptor(0, JointParameter.SwingX), 0.1));
        assertThrows(IllegalStateException.class,
            () -> DampedLeastSquaresIKSolver3D.applyDelta(arm, swapped, new JointParameterDescriptor(1, JointParameter.Angle), 0.1));
    }

    @Test
    @DisplayName("the leg's hip and knee limits match the presets")
    void presetLimits() {
        IKSwingTwistJoint3D hip = hip();
        assertEquals(ROOT_MAX_SWING, hip.getMaxSwing(), 0.0);
        assertEquals(-ROOT_TWIST, hip.getMinTwist(), 0.0);
        IKHingeJoint3D knee = (IKHingeJoint3D) net.tfminecraft.spiders.spider.presets.Presets.kneeJoint();
        assertEquals(KNEE_LEEWAY, knee.getMaxAngle(), 0.0);
        assertEquals(0.0, knee.getMinAngle(), 0.0);
    }
}
