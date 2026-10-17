package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.chain;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.hinge;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IKEvaluatorTest {
    /** A one-segment chain lying straight along +Z, 2 blocks long, so its tip is at (0, 0, 2). */
    private static final IKChain3D STRAIGHT = chain(2.0, hinge(-1, 1));

    private static IKChainPose3D straightPose() {
        IKChainPose3D pose = IKTestSupport.restPose(STRAIGHT);
        ((IKHingeJointState3D) pose.getSegments().get(0)).setAngle(0.0);
        return pose;
    }

    @Test
    @DisplayName("without a target orientation only the weighted position error counts")
    void positionOnly() {
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.1, 0.1, 2.0, 5.0);
        IKEvaluation3D evaluation = evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(3, 0, 6)));

        assertEquals(5.0, evaluation.getPositionError(), 1e-9);
        assertEquals(0.0, evaluation.getOrientationError(), 0.0);
        // target minus tip, times the position weight
        assertArrayEquals(new double[]{6, 0, 8}, evaluation.getErrorVector(), 1e-9);
        assertEquals(100.0, evaluation.getScore(), 1e-9);
        assertFalse(evaluation.isSolved());
    }

    @Test
    @DisplayName("a tip within tolerance is solved, inclusive of the boundary")
    void toleranceBoundary() {
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.5);
        assertTrue(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2.5))).isSolved());
        assertTrue(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0.3, 0, 2))).isSolved());
        assertFalse(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2.6))).isSolved());
    }

    @Test
    @DisplayName("the default evaluator wants the tip within a millimetre")
    void defaultTolerance() {
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D();
        assertTrue(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2.0009))).isSolved());
        assertFalse(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2.0011))).isSolved());
    }

    @Test
    @DisplayName("an orientation target adds its rotation error (axis times angle) with its own weight")
    void orientationError() {
        // tolerance 0.01, orientation tolerance 0.01, position weight 1, orientation weight 0.35 (defaults)
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.01, 0.01);
        Quaternionf turned = new Quaternionf().rotateY(0.5f);
        IKEvaluation3D evaluation = evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2), turned));

        assertEquals(0.0, evaluation.getPositionError(), 1e-9);
        assertEquals(0.5, evaluation.getOrientationError(), 1e-5);
        double[] errors = evaluation.getErrorVector();
        assertEquals(6, errors.length);
        assertArrayEquals(new double[]{0, 0.5 * 0.35, 0}, new double[]{errors[3], errors[4], errors[5]}, 1e-5);
        assertEquals(0.5 * 0.35 * 0.5 * 0.35, evaluation.getScore(), 1e-6);
        // position is perfect but the orientation is off
        assertFalse(evaluation.isSolved());
    }

    @Test
    @DisplayName("orientation and position must both be within their tolerances")
    void orientationTolerance() {
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.01, 0.2, 1.0);
        Vector tip = new Vector(0, 0, 2);
        assertTrue(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(tip, new Quaternionf().rotateY(0.1f))).isSolved());
        assertFalse(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(tip, new Quaternionf().rotateY(0.3f))).isSolved());
        assertFalse(evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 3), new Quaternionf())).isSolved());
    }

    @Test
    @DisplayName("q and -q are the same orientation, and a matching orientation has zero error")
    void quaternionDoubleCover() {
        IKEvaluator3D evaluator = IKEvaluator3D.createIKEvaluator3D(0.01);
        Quaternionf tipRotation = new Quaternionf();
        Quaternionf negated = new Quaternionf(-tipRotation.x, -tipRotation.y, -tipRotation.z, -tipRotation.w);

        IKEvaluation3D same = evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2), tipRotation));
        IKEvaluation3D flipped = evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2), negated));
        assertEquals(0.0, same.getOrientationError(), 0.0);
        assertEquals(0.0, flipped.getOrientationError(), 0.0);
        assertTrue(flipped.isSolved());

        // a large rotation written with negative w takes the short way round too
        Quaternionf turned = new Quaternionf().rotateY(0.5f);
        Quaternionf turnedNegated = new Quaternionf(-turned.x, -turned.y, -turned.z, -turned.w);
        IKEvaluation3D shortWay = evaluator.evaluate(STRAIGHT, straightPose(), new IKTarget3D(new Vector(0, 0, 2), turnedNegated));
        assertEquals(0.5, shortWay.getOrientationError(), 1e-5);
    }
}
