package net.tfminecraft.spiders.utilities.inverse_kinematics;

public final class DampedLeastSquaresOptions {
    private final int iterations;
    private final double damping;
    private final double minDamping;
    private final double maxDamping;
    private final double dampingScale;
    private final double finiteDifference;
    private final double maxStep;
    private final double minStepScale;

    public DampedLeastSquaresOptions(
        int iterations,
        double damping,
        double minDamping,
        double maxDamping,
        double dampingScale,
        double finiteDifference,
        double maxStep,
        double minStepScale
    ) {
        this.iterations = iterations;
        this.damping = damping;
        this.minDamping = minDamping;
        this.maxDamping = maxDamping;
        this.dampingScale = dampingScale;
        this.finiteDifference = finiteDifference;
        this.maxStep = maxStep;
        this.minStepScale = minStepScale;
    }

    public DampedLeastSquaresOptions(
        int iterations,
        double damping,
        double minDamping,
        double maxDamping,
        double dampingScale,
        double finiteDifference,
        double maxStep
    ) {
        this(iterations, damping, minDamping, maxDamping, dampingScale, finiteDifference, maxStep, 1.0 / 64);
    }

    public DampedLeastSquaresOptions(
        int iterations,
        double damping,
        double minDamping,
        double maxDamping,
        double dampingScale,
        double finiteDifference
    ) {
        this(iterations, damping, minDamping, maxDamping, dampingScale, finiteDifference, 1.0);
    }

    public DampedLeastSquaresOptions(
        int iterations,
        double damping,
        double minDamping,
        double maxDamping,
        double dampingScale
    ) {
        this(iterations, damping, minDamping, maxDamping, dampingScale, 0.3);
    }

    public DampedLeastSquaresOptions(int iterations, double damping, double minDamping, double maxDamping) {
        this(iterations, damping, minDamping, maxDamping, 2.5);
    }

    public DampedLeastSquaresOptions(int iterations, double damping, double minDamping) {
        this(iterations, damping, minDamping, 8.0);
    }

    public DampedLeastSquaresOptions(int iterations, double damping) {
        this(iterations, damping, 0.02);
    }

    public DampedLeastSquaresOptions(int iterations) {
        this(iterations, 0.1);
    }

    public DampedLeastSquaresOptions() {
        this(24);
    }

    public int getIterations() {
        return iterations;
    }

    public double getDamping() {
        return damping;
    }

    public double getMinDamping() {
        return minDamping;
    }

    public double getMaxDamping() {
        return maxDamping;
    }

    public double getDampingScale() {
        return dampingScale;
    }

    public double getFiniteDifference() {
        return finiteDifference;
    }

    public double getMaxStep() {
        return maxStep;
    }

    public double getMinStepScale() {
        return minStepScale;
    }
}
