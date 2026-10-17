package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

@FunctionalInterface
public interface IKEvaluator3D {
    IKEvaluation3D evaluate(IKChain3D chain, IKChainPose3D pose, IKTarget3D target);

    static IKEvaluator3D createIKEvaluator3D(
        double tolerance,
        double orientationTolerance,
        double positionWeight,
        double orientationWeight
    ) {
        return (chain, pose, target) ->
            evaluateIK(chain, pose, target, tolerance, orientationTolerance, positionWeight, orientationWeight);
    }

    static IKEvaluator3D createIKEvaluator3D(double tolerance, double orientationTolerance, double positionWeight) {
        return createIKEvaluator3D(tolerance, orientationTolerance, positionWeight, 0.35);
    }

    static IKEvaluator3D createIKEvaluator3D(double tolerance, double orientationTolerance) {
        return createIKEvaluator3D(tolerance, orientationTolerance, 1.0);
    }

    static IKEvaluator3D createIKEvaluator3D(double tolerance) {
        return createIKEvaluator3D(tolerance, tolerance);
    }

    static IKEvaluator3D createIKEvaluator3D() {
        return createIKEvaluator3D(0.001);
    }

    private static IKEvaluation3D evaluateIK(
        IKChain3D chain,
        IKChainPose3D pose,
        IKTarget3D target,
        double tolerance,
        double orientationTolerance,
        double positionWeight,
        double orientationWeight
    ) {
        List<IKChainWorldNode3D> worldNodes = chain.getWorldNodes(pose);
        IKChainWorldNode3D endEffector = worldNodes.get(worldNodes.size() - 1);

        Vector positionErrorVector = target.getPosition().clone().subtract(endEffector.getPosition());
        double positionError = positionErrorVector.length();

        List<Double> errorVector = new ArrayList<>();
        errorVector.add(positionErrorVector.getX() * positionWeight);
        errorVector.add(positionErrorVector.getY() * positionWeight);
        errorVector.add(positionErrorVector.getZ() * positionWeight);

        double orientationError = 0.0;
        Quaternionf targetOrientation = target.getOrientation();
        if (targetOrientation != null) {
            Vector orientationErrorVector = rotationErrorVector(endEffector.getWorldRotation(), targetOrientation);
            orientationError = orientationErrorVector.length();

            errorVector.add(orientationErrorVector.getX() * orientationWeight);
            errorVector.add(orientationErrorVector.getY() * orientationWeight);
            errorVector.add(orientationErrorVector.getZ() * orientationWeight);
        }

        boolean isSolved = positionError <= tolerance &&
            (targetOrientation == null || orientationError <= orientationTolerance);

        double score = 0.0;
        for (double value : errorVector) score += value * value;

        double[] errorArray = new double[errorVector.size()];
        for (int index = 0; index < errorArray.length; index++) errorArray[index] = errorVector.get(index);

        return new IKEvaluation3D(
            positionError,
            orientationError,
            score,
            isSolved,
            errorArray
        );
    }

    private static Vector rotationErrorVector(Quaternionf currentRotation, Quaternionf targetRotation) {
        final double EPSILON = 0.000001;

        Quaternionf inverseCurrent = new Quaternionf(currentRotation).invert();
        Quaternionf delta = new Quaternionf(targetRotation).mul(inverseCurrent).normalize();

        if (delta.w < 0f) {
            delta.x = -delta.x;
            delta.y = -delta.y;
            delta.z = -delta.z;
            delta.w = -delta.w;
        }

        float angle = 2 * (float) Math.acos(Maths.clamp(delta.w, -1f, 1f));
        float sine = (float) Math.sqrt(Math.max(1f - delta.w * delta.w, 0f));

        // A tiny angle always has a tiny sine (w rounds to 1), so the sine test covers both.
        if (sine <= EPSILON) {
            return new Vector((double) delta.x, (double) delta.y, (double) delta.z).multiply(2.0);
        }

        return new Vector(
            (double) (delta.x / sine),
            (double) (delta.y / sine),
            (double) (delta.z / sine)
        ).multiply(angle);
    }
}
