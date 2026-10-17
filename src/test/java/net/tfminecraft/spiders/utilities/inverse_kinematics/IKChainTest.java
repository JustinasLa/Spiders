package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.angleBetween;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.assertVector;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.chain;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.hinge;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.hip;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.knee;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.rotated;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IKChainTest {
    private static final double EPS = 1e-5;
    private static final double REST_BIAS = 0.035;

    @Test
    @DisplayName("a chain's length is the sum of its segments and the chain axis is +Z")
    void totalLengthAndAxis() {
        IKChain3D chain = new IKChain3D(List.of(
            new IKChainSegment3D(1.5, hip()),
            new IKChainSegment3D(0.0, knee()),
            new IKChainSegment3D(2.25, knee())
        ));
        assertEquals(3.75, chain.getTotalLength(), 0.0);
        assertEquals(0.0, new IKChain3D(List.of()).getTotalLength(), 0.0);
        assertVector(0, 0, 1, IKChain3D.getCHAIN_AXIS(), 0.0);

        IKChainSegment3D segment = chain.getSegments().get(2);
        assertEquals(2.25, segment.getLength(), 0.0);
        assertInstanceOf(IKHingeJoint3D.class, segment.getJoint());
    }

    @Test
    @DisplayName("hinges rest just inside their range, biased off zero so the chain starts bent")
    void hingeRestStates() {
        assertEquals(REST_BIAS, rest(hinge(0.0, 1.0)), 0.0);
        assertEquals(0.5 + REST_BIAS, rest(hinge(0.5, 1.0)), 0.0);
        assertEquals(-REST_BIAS, rest(hinge(-1.0, 0.0)), 0.0);
        assertEquals(-0.5 - REST_BIAS, rest(hinge(-1.0, -0.5)), 0.0);
        assertEquals(REST_BIAS, rest(hinge(-1.0, 1.0)), 0.0);
        // a range narrower than the bias is clamped to stay legal
        assertEquals(0.01, rest(hinge(0.0, 0.01)), 0.0);
        assertEquals(-0.01, rest(hinge(-0.01, 0.0)), 1e-15);
        assertEquals(0.01, rest(hinge(-0.5, 0.01)), 0.0);

        IKJointPose3D swing = IKChain3D.restState(hip());
        IKSwingTwistJointState3D state = assertInstanceOf(IKSwingTwistJointState3D.class, swing);
        assertEquals(0.0, state.getSwingX(), 0.0);
        assertEquals(0.0, state.getSwingY(), 0.0);
        assertEquals(0.0, state.getTwist(), 0.0);
    }

    private static double rest(IKHingeJoint3D joint) {
        return ((IKHingeJointState3D) IKChain3D.restState(joint)).getAngle();
    }

    @Test
    @DisplayName("createPose copies the root transform and gives every joint its rest state")
    void createPose() {
        IKChain3D chain = IKTestSupport.leg();
        Vector position = new Vector(1, 2, 3);
        Quaternionf rotation = new Quaternionf().rotateY(0.5f);
        IKChainPose3D pose = chain.createPose(position, rotation);

        position.setX(99);
        rotation.rotateX(1f);
        assertVector(1, 2, 3, pose.getPosition(), 0.0);
        assertEquals(new Quaternionf().rotateY(0.5f), pose.getOrientation());
        assertEquals(3, pose.getSegments().size());
        assertInstanceOf(IKSwingTwistJointState3D.class, pose.getSegments().get(0));
        assertEquals(REST_BIAS, ((IKHingeJointState3D) pose.getSegments().get(1)).getAngle(), 0.0);
    }

    @Test
    @DisplayName("world nodes walk the chain: each joint's rotation accumulates and each segment extends along +Z")
    void worldNodesForwardKinematics() {
        IKChain3D chain = chain(1.0, hinge(-Math.PI, Math.PI), hinge(-Math.PI, Math.PI));
        IKChainPose3D pose = chain.createPose(new Vector(10, 0, 0), new Quaternionf());
        ((IKHingeJointState3D) pose.getSegments().get(0)).setAngle(Math.PI / 2);
        ((IKHingeJointState3D) pose.getSegments().get(1)).setAngle(Math.PI / 2);

        List<IKChainWorldNode3D> nodes = chain.getWorldNodes(pose);
        assertEquals(3, nodes.size());
        // root node is the pose itself
        assertVector(10, 0, 0, nodes.get(0).getPosition(), EPS);
        assertEquals(new Quaternionf(), nodes.get(0).getWorldRotation());
        assertEquals(new Quaternionf(), nodes.get(0).getLocalRotation());
        // +90 degrees about +X turns +Z to -Y
        assertVector(10, -1, 0, nodes.get(1).getPosition(), EPS);
        // another +90 degrees: now pointing -Z
        assertVector(10, -1, -1, nodes.get(2).getPosition(), EPS);
        assertEquals(Math.PI, angleBetween(new Quaternionf(), nodes.get(2).getWorldRotation()), EPS);
        assertEquals(Math.PI / 2, angleBetween(new Quaternionf(), nodes.get(2).getLocalRotation()), EPS);

        // the nodes are copies: changing them leaves the pose alone
        nodes.get(0).getPosition().setX(-50);
        assertVector(10, 0, 0, pose.getPosition(), 0.0);
    }

    @Test
    @DisplayName("the root orientation turns the whole chain")
    void worldNodesFollowRootOrientation() {
        IKChain3D chain = chain(2.0, hinge(-1, 1));
        IKChainPose3D pose = chain.createPose(new Vector(0, 5, 0), new Quaternionf().rotateY((float) (Math.PI / 2)));
        ((IKHingeJointState3D) pose.getSegments().get(0)).setAngle(0.0);

        // +Z yawed 90 degrees is +X
        assertVector(2, 5, 0, chain.getWorldNodes(pose).get(1).getPosition(), EPS);
    }

    @Test
    @DisplayName("a swing-twist joint swings off +Z by the swing angle, then twists about its own axis")
    void swingTwistRotation() {
        IKSwingTwistJoint3D joint = new IKSwingTwistJoint3D(1.0, -1.0, 1.0, new Vector(0, 1, 0));
        Quaternionf rotation = joint.rotation(new IKSwingTwistJointState3D(0.3, -0.4, 0.5));

        Vector axis = rotated(rotation, 0, 0, 1);
        assertEquals(0.5, Math.acos(axis.getZ()), EPS, "swing angle is |(x, y)|");
        // twist does not move the axis, only spins about it
        Vector untwisted = rotated(joint.rotation(new IKSwingTwistJointState3D(0.3, -0.4)), 0, 0, 1);
        assertVector(untwisted, axis, EPS);
        Vector twistedUp = rotated(rotation, 0, 1, 0);
        Vector plainUp = rotated(joint.rotation(new IKSwingTwistJointState3D(0.3, -0.4)), 0, 1, 0);
        assertEquals(0.5, Math.acos(twistedUp.dot(plainUp)), EPS);

        assertEquals(new Quaternionf(), joint.rotation(new IKSwingTwistJointState3D()));
        assertEquals(1.0, joint.getMaxSwing(), 0.0);
        assertEquals(-1.0, joint.getMinTwist(), 0.0);
        assertEquals(1.0, joint.getMaxTwist(), 0.0);
        assertVector(0, 1, 0, joint.getReferenceAxis(), 0.0);
    }

    @Test
    @DisplayName("swingRotation is identity for a negligible swing and otherwise rotates about (x, y, 0)")
    void swingRotation() {
        assertEquals(new Quaternionf(), IKChain3D.swingRotation(0.0, 0.0));
        assertEquals(new Quaternionf(), IKChain3D.swingRotation(1e-10, -1e-10));
        assertEquals(0.0, angleBetween(new Quaternionf().rotateX(0.7f), IKChain3D.swingRotation(0.7, 0.0)), EPS);
        assertEquals(0.0, angleBetween(new Quaternionf().rotateY(-0.7f), IKChain3D.swingRotation(0.0, -0.7)), EPS);
    }

    @Test
    @DisplayName("each swing component's limit shrinks as the other uses up the cone")
    void swingComponentLimit() {
        assertEquals(0.5, IKChain3D.swingComponentLimit(0.0, 0.5), 0.0);
        assertEquals(0.4, IKChain3D.swingComponentLimit(0.3, 0.5), 1e-12);
        assertEquals(0.4, IKChain3D.swingComponentLimit(-0.3, 0.5), 1e-12);
        assertEquals(0.0, IKChain3D.swingComponentLimit(0.6, 0.5), 0.0);
    }

    @Test
    @DisplayName("quaternionFromTo turns one direction onto another, including exact reversals")
    void quaternionFromTo() {
        assertEquals(new Quaternionf(), IKChain3D.quaternionFromTo(new Vector(0, 0, 2), new Vector(0, 0, 5), new Vector(1, 0, 0)));

        assertTurns(new Vector(0, 0, 1), new Vector(1, 1, 0), new Vector(1, 0, 0));
        assertTurns(new Vector(1, 2, 3), new Vector(-3, 0.5, 1), new Vector(1, 0, 0));

        // reversed: half a turn about from x reference, here +Z x +X = +Y
        Quaternionf reversed = assertTurns(new Vector(0, 0, 1), new Vector(0, 0, -1), new Vector(1, 0, 0));
        assertVector(0, 1, 0, rotated(reversed, 0, 1, 0), EPS);
        assertVector(-1, 0, 0, rotated(reversed, 1, 0, 0), EPS);

        // reversed and the reference is parallel too: falls back to another axis
        assertTurns(new Vector(0, 0, 1), new Vector(0, 0, -1), new Vector(0, 0, 1));
        assertTurns(new Vector(1, 0, 0), new Vector(-1, 0, 0), new Vector(1, 0, 0));
    }

    private static Quaternionf assertTurns(Vector from, Vector to, Vector reference) {
        Quaternionf rotation = IKChain3D.quaternionFromTo(from, to, reference);
        Vector3f turned = from.toVector3f().normalize().rotate(rotation);
        Vector3f expected = to.toVector3f().normalize();
        assertEquals(0.0, turned.distance(expected), 1e-4, "turned " + from + " towards " + to);
        return rotation;
    }

    @Test
    @DisplayName("shortestAngleDelta wraps into (-pi, pi]")
    void shortestAngleDelta() {
        assertEquals(0.5, IKChain3D.shortestAngleDelta(1.0, 1.5), 1e-12);
        assertEquals(-Math.PI / 2, IKChain3D.shortestAngleDelta(0.0, 3 * Math.PI / 2), 1e-12);
        assertEquals(Math.PI / 2, IKChain3D.shortestAngleDelta(0.0, -3 * Math.PI / 2), 1e-12);
        assertEquals(Math.PI, IKChain3D.shortestAngleDelta(0.0, Math.PI), 0.0);
        assertEquals(Math.PI, IKChain3D.shortestAngleDelta(0.0, -Math.PI), 0.0);
        assertEquals(0.5, IKChain3D.shortestAngleDelta(0.0, 0.5 + 6 * Math.PI), 1e-9);
    }

    @Test
    @DisplayName("hinge joints reject impossible limits, including NaN")
    void hingeValidation() {
        Vector x = new Vector(1, 0, 0);
        Vector z = new Vector(0, 0, 1);
        assertThrows(IllegalArgumentException.class, () -> new IKHingeJoint3D(1.0, 0.0, x, z));
        assertThrows(IllegalArgumentException.class, () -> new IKHingeJoint3D(Double.NaN, 0.0, x, z));
        assertThrows(IllegalArgumentException.class, () -> new IKHingeJoint3D(0.0, 1.0, new Vector(), z));
        assertThrows(IllegalArgumentException.class, () -> new IKHingeJoint3D(0.0, 1.0, x, new Vector()));

        IKHingeJoint3D joint = new IKHingeJoint3D(-0.5, 0.5, x, z);
        assertEquals(-0.5, joint.getMinAngle(), 0.0);
        assertEquals(0.5, joint.getMaxAngle(), 0.0);
        assertSame(x, joint.getAxis());
        assertSame(z, joint.getReference());
        IllegalArgumentException wrongState = assertThrows(IllegalArgumentException.class, () -> joint.rotation(new IKSwingTwistJointState3D()));
        assertTrue(wrongState.getMessage().contains("IKHingeJointState3D"));
    }

    @Test
    @DisplayName("swing-twist joints reject impossible limits, including NaN")
    void swingTwistValidation() {
        Vector up = new Vector(0, 1, 0);
        assertThrows(IllegalArgumentException.class, () -> new IKSwingTwistJoint3D(-0.1, 0.0, 0.0, up));
        assertThrows(IllegalArgumentException.class, () -> new IKSwingTwistJoint3D(Double.NaN, 0.0, 0.0, up));
        assertThrows(IllegalArgumentException.class, () -> new IKSwingTwistJoint3D(0.5, 0.2, 0.1, up));
        assertThrows(IllegalArgumentException.class, () -> new IKSwingTwistJoint3D(0.5, 0.0, 0.1, new Vector()));
        // a zero cone is a legal (rigid) joint
        new IKSwingTwistJoint3D(0.0, 0.0, 0.0, up);

        IllegalArgumentException wrongState = assertThrows(IllegalArgumentException.class, () -> hip().rotation(new IKHingeJointState3D()));
        assertTrue(wrongState.getMessage().contains("IKSwingTwistJointState3D"));
    }

    @Test
    @DisplayName("hinge states clone, copy and interpolate the short way round")
    void hingeState() {
        IKHingeJointState3D state = new IKHingeJointState3D(3.0);
        IKHingeJointState3D clone = state.clone();
        assertNotSame(state, clone);
        clone.setAngle(1.0);
        assertEquals(3.0, state.getAngle(), 0.0);

        state.copy(clone);
        assertEquals(1.0, state.getAngle(), 0.0);
        assertEquals(0.0, new IKHingeJointState3D().getAngle(), 0.0);

        // from 3 to -3 the short way crosses pi rather than zero
        IKHingeJointState3D wrapping = new IKHingeJointState3D(3.0);
        wrapping.lerpState(new IKHingeJointState3D(-3.0), 0.5);
        assertEquals(Math.PI, wrapping.getAngle(), 1e-12);

        IKHingeJointState3D halfway = new IKHingeJointState3D(0.0);
        halfway.lerpState(new IKHingeJointState3D(1.0), 0.25);
        assertEquals(0.25, halfway.getAngle(), 1e-12);

        assertThrows(IllegalArgumentException.class, () -> state.copy(new IKSwingTwistJointState3D()));
        assertThrows(IllegalArgumentException.class, () -> state.lerpState(new IKSwingTwistJointState3D(), 0.5));
    }

    @Test
    @DisplayName("swing-twist states clone, copy and interpolate component-wise")
    void swingTwistState() {
        IKSwingTwistJointState3D state = new IKSwingTwistJointState3D(0.1, 0.2, 0.3);
        IKSwingTwistJointState3D clone = state.clone();
        assertNotSame(state, clone);
        clone.setSwingX(1.0);
        clone.setSwingY(2.0);
        clone.setTwist(3.0);
        assertEquals(0.1, state.getSwingX(), 0.0);

        state.lerpState(clone, 0.5);
        assertEquals(0.55, state.getSwingX(), 1e-12);
        assertEquals(1.1, state.getSwingY(), 1e-12);
        assertEquals(1.65, state.getTwist(), 1e-12);

        state.copy(clone);
        assertEquals(1.0, state.getSwingX(), 0.0);
        assertEquals(2.0, state.getSwingY(), 0.0);
        assertEquals(3.0, state.getTwist(), 0.0);

        IKSwingTwistJointState3D partial = new IKSwingTwistJointState3D(0.4, 0.5);
        assertEquals(0.0, partial.getTwist(), 0.0);
        IKSwingTwistJointState3D swingOnly = new IKSwingTwistJointState3D(0.4);
        assertEquals(0.4, swingOnly.getSwingX(), 0.0);
        assertEquals(0.0, swingOnly.getSwingY(), 0.0);

        assertThrows(IllegalArgumentException.class, () -> state.copy(new IKHingeJointState3D()));
        assertThrows(IllegalArgumentException.class, () -> state.lerpState(new IKHingeJointState3D(), 0.5));
    }

    @Test
    @DisplayName("pose clone is deep and copy overwrites in place")
    void poseCloneAndCopy() {
        IKChain3D chain = IKTestSupport.leg();
        IKChainPose3D pose = chain.createPose(new Vector(1, 2, 3), new Quaternionf().rotateY(0.3f));
        IKChainPose3D clone = pose.clone();

        clone.getPosition().setX(7);
        clone.getOrientation().rotateX(1f);
        ((IKSwingTwistJointState3D) clone.getSegments().get(0)).setSwingX(0.4);
        ((IKHingeJointState3D) clone.getSegments().get(1)).setAngle(1.2);
        assertVector(1, 2, 3, pose.getPosition(), 0.0);
        assertEquals(new Quaternionf().rotateY(0.3f), pose.getOrientation());
        assertEquals(0.0, ((IKSwingTwistJointState3D) pose.getSegments().get(0)).getSwingX(), 0.0);

        Vector positionBefore = pose.getPosition();
        List<IKJointPose3D> segmentsBefore = pose.getSegments();
        pose.copy(clone);
        assertSame(positionBefore, pose.getPosition());
        assertSame(segmentsBefore, pose.getSegments());
        assertVector(7, 2, 3, pose.getPosition(), 0.0);
        assertEquals(clone.getOrientation(), pose.getOrientation());
        assertEquals(0.4, ((IKSwingTwistJointState3D) pose.getSegments().get(0)).getSwingX(), 0.0);
        assertEquals(1.2, ((IKHingeJointState3D) pose.getSegments().get(1)).getAngle(), 0.0);
    }

    @Test
    @DisplayName("pose lerp blends position, orientation and joints, clamping the amount to [0, 1]")
    void poseLerp() {
        IKChain3D chain = chain(1.0, hinge(-2, 2));
        IKChainPose3D from = chain.createPose(new Vector(0, 0, 0), new Quaternionf());
        IKChainPose3D to = chain.createPose(new Vector(4, 0, 0), new Quaternionf().rotateY(1f));
        ((IKHingeJointState3D) to.getSegments().get(0)).setAngle(1.0);

        IKChainPose3D half = from.clone();
        half.lerp(to, 0.5);
        assertVector(2, 0, 0, half.getPosition(), 1e-12);
        assertEquals(0.5, angleBetween(new Quaternionf(), half.getOrientation()), EPS);
        assertEquals(REST_BIAS + (1.0 - REST_BIAS) / 2, ((IKHingeJointState3D) half.getSegments().get(0)).getAngle(), 1e-12);

        IKChainPose3D over = from.clone();
        over.lerp(to, 3.0);
        assertVector(4, 0, 0, over.getPosition(), 1e-12);
        assertEquals(1.0, ((IKHingeJointState3D) over.getSegments().get(0)).getAngle(), 1e-12);

        IKChainPose3D under = from.clone();
        under.lerp(to, -1.0);
        assertVector(0, 0, 0, under.getPosition(), 0.0);
        assertEquals(REST_BIAS, ((IKHingeJointState3D) under.getSegments().get(0)).getAngle(), 0.0);
    }

    @Test
    @DisplayName("targets clone deeply and lerp their orientation only when they have one")
    void targets() {
        IKTarget3D positional = new IKTarget3D(new Vector(1, 0, 0));
        assertNull(positional.getOrientation());
        IKTarget3D positionalClone = positional.clone();
        assertNull(positionalClone.getOrientation());
        assertNotSame(positional.getPosition(), positionalClone.getPosition());

        IKTarget3D oriented = new IKTarget3D(new Vector(3, 0, 0), new Quaternionf().rotateY(1f));
        IKTarget3D orientedClone = oriented.clone();
        assertNotSame(oriented.getOrientation(), orientedClone.getOrientation());
        assertEquals(oriented.getOrientation(), orientedClone.getOrientation());

        // neither has an orientation: only the position moves
        IKTarget3D bothPlain = positional.clone();
        bothPlain.lerp(new IKTarget3D(new Vector(3, 0, 0)), 0.5);
        assertVector(2, 0, 0, bothPlain.getPosition(), 1e-12);
        assertNull(bothPlain.getOrientation());

        // gaining an orientation adopts a copy of it
        IKTarget3D gaining = positional.clone();
        gaining.lerp(oriented, 0.5);
        assertVector(2, 0, 0, gaining.getPosition(), 1e-12);
        assertEquals(oriented.getOrientation(), gaining.getOrientation());
        assertNotSame(oriented.getOrientation(), gaining.getOrientation());

        // both oriented: slerp
        IKTarget3D blending = new IKTarget3D(new Vector(), new Quaternionf());
        blending.lerp(oriented, 0.5);
        assertEquals(0.5, angleBetween(new Quaternionf(), blending.getOrientation()), EPS);

        // losing the orientation keeps the current one
        IKTarget3D keeping = oriented.clone();
        keeping.lerp(new IKTarget3D(new Vector(3, 0, 0)), 0.5);
        assertEquals(oriented.getOrientation(), keeping.getOrientation());

        keeping.setOrientation(null);
        assertNull(keeping.getOrientation());
    }

    @Test
    @DisplayName("value holders return what they were built with")
    void valueHolders() {
        JointParameterDescriptor descriptor = new JointParameterDescriptor(2, JointParameter.Twist);
        assertEquals(2, descriptor.getJointIndex());
        assertEquals(JointParameter.Twist, descriptor.getParameter());
        assertEquals(List.of(JointParameter.Angle, JointParameter.SwingX, JointParameter.SwingY, JointParameter.Twist),
            List.of(JointParameter.values()));

        double[] errors = {1, 2, 3};
        IKEvaluation3D evaluation = new IKEvaluation3D(0.5, 0.25, 14.0, true, errors);
        assertEquals(0.5, evaluation.getPositionError(), 0.0);
        assertEquals(0.25, evaluation.getOrientationError(), 0.0);
        assertEquals(14.0, evaluation.getScore(), 0.0);
        assertTrue(evaluation.isSolved());
        assertSame(errors, evaluation.getErrorVector());

        Vector position = new Vector(1, 1, 1);
        Quaternionf world = new Quaternionf().rotateX(1f);
        Quaternionf local = new Quaternionf().rotateY(1f);
        IKChainWorldNode3D node = new IKChainWorldNode3D(position, world, local);
        assertSame(position, node.getPosition());
        assertSame(world, node.getWorldRotation());
        assertSame(local, node.getLocalRotation());

        List<IKChainSegment3D> segments = new ArrayList<>();
        assertSame(segments, new IKChain3D(segments).getSegments());
    }
}
