package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.maths.Maths;
import java.util.Arrays;
import java.util.List;

public final class SolveDampedLeastSquares {
    private static final double EPSILON = 0.000001;

    private SolveDampedLeastSquares() {
    }

    public static <State, Parameter, Metrics extends DampedLeastSquaresMetrics> void solveDampedLeastSquares(
        State state,
        LeastSquaresProblem<State, Parameter, Metrics> problem,
        DampedLeastSquaresOptions options
    ) {
        Metrics currentMetrics = problem.evaluateState(state);
        State bestState = problem.cloneState(state);
        double damping = Maths.clamp(options.getDamping(), options.getMinDamping(), options.getMaxDamping());

        int iterations = options.getIterations();
        for (int iteration = 0; iteration < iterations; iteration++) {
            if (currentMetrics.isSolved()) break;

            List<Parameter> parameters = problem.listParameters(state);
            if (parameters.isEmpty()) break;

            double[][] jacobian = computeJacobian(problem, state, currentMetrics, parameters, options.getFiniteDifference());
            double[] step = solveDampedLeastSquaresStep(problem, state, parameters, jacobian, currentMetrics.getErrorVector(), damping);
            if (step == null) {
                damping = Math.min(options.getMaxDamping(), damping * options.getDampingScale());
                continue;
            }

            limitStepMagnitude(step, options.getMaxStep());

            boolean accepted = false;
            double stepScale = 1.0;
            while (stepScale >= options.getMinStepScale()) {
                State trialState = problem.cloneState(state);
                problem.applyStep(trialState, parameters, step, stepScale);

                Metrics trialMetrics = problem.evaluateState(trialState);
                if (trialMetrics.getScore() < currentMetrics.getScore() - EPSILON) {
                    problem.copyState(state, trialState);
                    currentMetrics = trialMetrics;
                    accepted = true;

                    // Only improving steps are accepted, so the current state is always the best so far.
                    bestState = problem.cloneState(trialState);

                    damping = Math.max(options.getMinDamping(), damping / options.getDampingScale());
                    break;
                }

                stepScale *= 0.5;
            }

            if (!accepted) {
                damping = Math.min(options.getMaxDamping(), damping * options.getDampingScale());
            }
        }

        problem.copyState(state, bestState);
    }

    private static <State, Parameter, Metrics extends DampedLeastSquaresMetrics> double[][] computeJacobian(
        LeastSquaresProblem<State, Parameter, Metrics> problem,
        State state,
        Metrics baseMetrics,
        List<Parameter> parameters,
        double finiteDifference
    ) {
        int rows = baseMetrics.getErrorVector().length;
        double[][] jacobian = new double[rows][parameters.size()];

        for (int column = 0; column < parameters.size(); column++) {
            State forwardState = problem.cloneState(state);
            State backwardState = problem.cloneState(state);
            Parameter parameter = parameters.get(column);
            double forwardDelta = problem.perturbParameter(forwardState, parameter, finiteDifference);
            double backwardDelta = problem.perturbParameter(backwardState, parameter, -finiteDifference);

            if (Math.abs(forwardDelta) > EPSILON && Math.abs(backwardDelta) > EPSILON) {
                Metrics forwardMetrics = problem.evaluateState(forwardState);
                Metrics backwardMetrics = problem.evaluateState(backwardState);

                for (int row = 0; row < rows; row++) {
                    jacobian[row][column] =
                        (forwardMetrics.getErrorVector()[row] - backwardMetrics.getErrorVector()[row]) / (forwardDelta - backwardDelta);
                }
                continue;
            }

            // One side was clamped to zero: fall back to a one-sided difference.
            Metrics sampledMetrics = null;
            double denominator = 0.0;

            if (Math.abs(forwardDelta) > EPSILON) {
                sampledMetrics = problem.evaluateState(forwardState);
                denominator = forwardDelta;
            } else if (Math.abs(backwardDelta) > EPSILON) {
                sampledMetrics = problem.evaluateState(backwardState);
                denominator = backwardDelta;
            }

            // Both sides clamped (a fixed parameter): it has no effect, so its column stays zero.
            if (sampledMetrics == null) continue;

            for (int row = 0; row < rows; row++) {
                jacobian[row][column] = (sampledMetrics.getErrorVector()[row] - baseMetrics.getErrorVector()[row]) / denominator;
            }
        }

        return jacobian;
    }

    /** Solves `(JᵀJ + λ²I) · step = -Jᵀ · error`, returning null if the system is singular. */
    private static <State, Parameter, Metrics extends DampedLeastSquaresMetrics> double[] solveDampedLeastSquaresStep(
        LeastSquaresProblem<State, Parameter, Metrics> problem,
        State state,
        List<Parameter> parameters,
        double[][] jacobian,
        double[] errorVector,
        double damping
    ) {
        int columnCount = parameters.size();
        boolean[] active = new boolean[columnCount];
        Arrays.fill(active, true);

        while (true) {
            double[] step = solveRestrictedStep(jacobian, errorVector, damping, active);
            if (step == null) return null;

            boolean pinned = false;

            for (int index = 0; index < columnCount; index++) {
                if (!active[index]) continue;

                double delta = step[index];
                if (Math.abs(delta) <= EPSILON) continue;

                double applied = problem.clampDelta(state, parameters.get(index), delta);
                step[index] = applied;

                if (Math.abs(applied) <= EPSILON) {
                    active[index] = false;
                    pinned = true;
                }
            }

            // Re-solve without the pinned parameters; solveRestrictedStep returns null once none are left.
            if (!pinned) return step;
        }
    }

    private static double[] solveRestrictedStep(
        double[][] jacobian,
        double[] errorVector,
        double damping,
        boolean[] active
    ) {
        int activeCount = 0;
        for (boolean isActive : active) if (isActive) activeCount++;
        int[] columns = new int[activeCount];
        int cursor = 0;
        for (int index = 0; index < active.length; index++) {
            if (active[index]) columns[cursor++] = index;
        }
        if (columns.length == 0) return null;

        int size = columns.length;
        double[][] normalMatrix = new double[size][size];
        double[] rhs = new double[size];
        double dampingSquared = damping * damping;

        for (int row = 0; row < jacobian.length; row++) {
            for (int column = 0; column < size; column++) {
                double left = jacobian[row][columns[column]];
                rhs[column] -= left * errorVector[row];

                for (int innerColumn = 0; innerColumn < size; innerColumn++) {
                    normalMatrix[column][innerColumn] += left * jacobian[row][columns[innerColumn]];
                }
            }
        }

        for (int index = 0; index < size; index++) {
            normalMatrix[index][index] += dampingSquared;
        }

        double[] solution = solveLinearSystem(normalMatrix, rhs);
        if (solution == null) return null;

        double[] step = new double[active.length];
        for (int index = 0; index < size; index++) {
            if (!Double.isFinite(solution[index])) return null;
            step[columns[index]] = solution[index];
        }

        return step;
    }

    /** Gauss-Jordan elimination with partial pivoting. */
    private static double[] solveLinearSystem(double[][] matrix, double[] rhs) {
        int size = rhs.length;
        double[][] augmented = new double[size][];
        for (int row = 0; row < size; row++) {
            double[] values = new double[size + 1];
            for (int column = 0; column < size + 1; column++) {
                values[column] = column == size ? rhs[row] : matrix[row][column];
            }
            augmented[row] = values;
        }

        for (int pivot = 0; pivot < size; pivot++) {
            int pivotRow = pivot;
            for (int row = pivot + 1; row < size; row++) {
                if (Math.abs(augmented[row][pivot]) > Math.abs(augmented[pivotRow][pivot])) {
                    pivotRow = row;
                }
            }

            if (Math.abs(augmented[pivotRow][pivot]) <= EPSILON) return null;

            if (pivotRow != pivot) {
                double[] swap = augmented[pivot];
                augmented[pivot] = augmented[pivotRow];
                augmented[pivotRow] = swap;
            }

            double[] pivotValues = augmented[pivot];
            double pivotValue = pivotValues[pivot];
            for (int column = pivot; column <= size; column++) {
                pivotValues[column] /= pivotValue;
            }

            for (int row = 0; row < size; row++) {
                if (row == pivot) continue;

                double[] currentRow = augmented[row];
                double factor = currentRow[pivot];
                if (Math.abs(factor) <= EPSILON) continue;

                for (int column = pivot; column <= size; column++) {
                    currentRow[column] -= factor * pivotValues[column];
                }
            }
        }

        double[] result = new double[size];
        for (int index = 0; index < size; index++) result[index] = augmented[index][size];
        return result;
    }

    private static void limitStepMagnitude(double[] step, double maxStep) {
        double maxMagnitude = 0.0;
        for (double value : step) maxMagnitude = Math.max(maxMagnitude, Math.abs(value));

        if (maxMagnitude <= maxStep || maxMagnitude <= EPSILON) return;

        double scale = maxStep / maxMagnitude;
        for (int index = 0; index < step.length; index++) step[index] *= scale;
    }
}
