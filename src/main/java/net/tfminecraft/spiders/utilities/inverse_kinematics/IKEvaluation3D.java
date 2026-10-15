package net.tfminecraft.spiders.utilities.inverse_kinematics;

public final class IKEvaluation3D implements DampedLeastSquaresMetrics {
    private final double positionError;
    private final double orientationError;
    private final double score;
    private final boolean isSolved;
    private final double[] errorVector;

    public IKEvaluation3D(
        double positionError,
        double orientationError,
        double score,
        boolean isSolved,
        double[] errorVector
    ) {
        this.positionError = positionError;
        this.orientationError = orientationError;
        this.score = score;
        this.isSolved = isSolved;
        this.errorVector = errorVector;
    }

    public double getPositionError() {
        return positionError;
    }

    public double getOrientationError() {
        return orientationError;
    }

    @Override
    public double getScore() {
        return score;
    }

    @Override
    public boolean isSolved() {
        return isSolved;
    }

    @Override
    public double[] getErrorVector() {
        return errorVector;
    }
}
