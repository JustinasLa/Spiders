package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generic damped least-squares loop, driven with small problems whose answers are known. */
class SolveDampedLeastSquaresTest {
    /**
     * How close the solver gets: it only accepts steps that cut the squared error by more than 1e-6, so it
     * stops refining once the remaining error is around 1e-3.
     */
    private static final double CONVERGED = 1e-3;

    private record Metrics(double[] errorVector, boolean solved) implements DampedLeastSquaresMetrics {
        @Override
        public double[] getErrorVector() {
            return errorVector;
        }

        @Override
        public double getScore() {
            double score = 0.0;
            for (double value : errorVector) score += value * value;
            return score;
        }

        @Override
        public boolean isSolved() {
            return solved;
        }
    }

    /** One {@code applyStep} call: the state it started from, the full step and the scale tried. */
    private record Trial(double[] from, double[] step, double scale) {
    }

    /**
     * Minimise |error(x)| for x in a box. Parameters are indices into x. Records every step tried, and can
     * be told to ignore steps (so every trial is rejected) to watch how the damping reacts.
     */
    private static final class BoxProblem implements LeastSquaresProblem<double[], Integer, Metrics> {
        final Function<double[], double[]> error;
        final double[] min;
        final double[] max;
        double tolerance = 1e-12;
        boolean ignoreSteps = false;
        final List<Trial> trials = new ArrayList<>();
        int evaluations = 0;
        int parameterListings = 0;

        BoxProblem(Function<double[], double[]> error, double[] min, double[] max) {
            this.error = error;
            this.min = min;
            this.max = max;
        }

        static BoxProblem unbounded(int size, Function<double[], double[]> error) {
            double[] min = new double[size];
            double[] max = new double[size];
            Arrays.fill(min, Double.NEGATIVE_INFINITY);
            Arrays.fill(max, Double.POSITIVE_INFINITY);
            return new BoxProblem(error, min, max);
        }

        /** Full (scale 1) steps in the order they were tried. */
        List<Trial> fullSteps() {
            return trials.stream().filter(it -> it.scale() == 1.0).toList();
        }

        @Override
        public double[] cloneState(double[] state) {
            return state.clone();
        }

        @Override
        public void copyState(double[] destination, double[] source) {
            System.arraycopy(source, 0, destination, 0, source.length);
        }

        @Override
        public Metrics evaluateState(double[] state) {
            evaluations++;
            double[] errors = error.apply(state);
            double norm = 0.0;
            for (double value : errors) norm += value * value;
            return new Metrics(errors, Math.sqrt(norm) <= tolerance);
        }

        @Override
        public List<Integer> listParameters(double[] state) {
            parameterListings++;
            List<Integer> parameters = new ArrayList<>();
            for (int index = 0; index < state.length; index++) parameters.add(index);
            return parameters;
        }

        @Override
        public double perturbParameter(double[] state, Integer parameter, double delta) {
            double applied = clampDelta(state, parameter, delta);
            state[parameter] += applied;
            return applied;
        }

        @Override
        public double clampDelta(double[] state, Integer parameter, double delta) {
            return Math.max(min[parameter], Math.min(max[parameter], state[parameter] + delta)) - state[parameter];
        }

        @Override
        public void applyStep(double[] state, List<Integer> parameters, double[] step, double scale) {
            trials.add(new Trial(state.clone(), step.clone(), scale));
            if (ignoreSteps) return;
            for (int index = 0; index < parameters.size(); index++) {
                perturbParameter(state, parameters.get(index), step[index] * scale);
            }
        }
    }

    private static void solve(double[] state, BoxProblem problem, DampedLeastSquaresOptions options) {
        SolveDampedLeastSquares.solveDampedLeastSquares(state, problem, options);
    }

    /** For error = x - target with unit Jacobian the DLS step is -e / (1 + lambda^2); recover lambda. */
    private static double dampingOf(Trial trial, double target) {
        double error = trial.from()[0] - target;
        return Math.sqrt(-error / trial.step()[0] - 1.0);
    }

    @Test
    @DisplayName("solves a well-posed linear system")
    void solvesLinearSystem() {
        // 2x = 4, y = -3
        BoxProblem problem = BoxProblem.unbounded(2, x -> new double[]{2 * x[0] - 4, x[1] + 3});
        problem.tolerance = 1e-6;
        double[] state = {0, 0};
        solve(state, problem, new DampedLeastSquaresOptions(50, 0.1, 0.02, 8.0, 2.5, 0.3, 10.0));

        assertEquals(2.0, state[0], CONVERGED);
        assertEquals(-3.0, state[1], CONVERGED);
    }

    @Test
    @DisplayName("solves a coupled system whose normal equations need row pivoting")
    void solvesCoupledSystem() {
        // one equation, two unknowns: x + 10y = 5 (many answers, any will do)
        BoxProblem problem = BoxProblem.unbounded(2, x -> new double[]{x[0] + 10 * x[1] - 5});
        problem.tolerance = 1e-6;
        double[] state = {0, 0};
        solve(state, problem, new DampedLeastSquaresOptions(50, 0.1, 0.01, 8.0, 2.5, 0.3, 10.0));

        assertEquals(5.0, state[0] + 10 * state[1], CONVERGED);
    }

    @Test
    @DisplayName("an already solved state is left alone without even listing parameters")
    void alreadySolved() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 1});
        problem.tolerance = 0.5;
        double[] state = {1.2};
        solve(state, problem, new DampedLeastSquaresOptions());

        assertArrayEquals(new double[]{1.2}, state);
        assertEquals(0, problem.parameterListings);
        assertTrue(problem.trials.isEmpty());
    }

    @Test
    @DisplayName("a problem with no parameters is left alone")
    void noParameters() {
        BoxProblem problem = BoxProblem.unbounded(0, x -> new double[]{1});
        double[] state = {};
        solve(state, problem, new DampedLeastSquaresOptions());

        assertEquals(1, problem.parameterListings);
        assertEquals(1, problem.evaluations);
        assertTrue(problem.trials.isEmpty());
    }

    @Test
    @DisplayName("parameters never leave their bounds; the closest legal answer is found")
    void respectsBounds() {
        // wants x = 5 but x is limited to [-1, 2]
        BoxProblem problem = new BoxProblem(x -> new double[]{x[0] - 5}, new double[]{-1}, new double[]{2});
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(30, 0.1, 0.02, 8.0, 2.5, 0.3, 10.0));

        assertEquals(2.0, state[0], 1e-9);
        for (Trial trial : problem.trials) assertTrue(trial.from()[0] <= 2.0);
    }

    @Test
    @DisplayName("a parameter pinned at its bound drops out and the others finish the job")
    void pinnedParameterDropsOut() {
        // x + y = 5 with y capped at 1, starting with y already at the cap
        BoxProblem problem = new BoxProblem(
            x -> new double[]{x[0] + x[1] - 5},
            new double[]{Double.NEGATIVE_INFINITY, 0},
            new double[]{Double.POSITIVE_INFINITY, 1}
        );
        problem.tolerance = 1e-6;
        double[] state = {0, 1};
        solve(state, problem, new DampedLeastSquaresOptions(30, 0.1, 0.02, 8.0, 2.5, 0.3, 10.0));

        assertEquals(1.0, state[1], 0.0);
        assertEquals(4.0, state[0], CONVERGED);
        // the pinned y was never asked to move (beyond negligible components, which are not clamped)
        for (Trial trial : problem.trials) assertTrue(Math.abs(trial.step()[1]) <= 1e-6, "y step " + trial.step()[1]);
    }

    @Test
    @DisplayName("a parameter at its lower bound still gets a slope from its free side")
    void oneSidedDifferenceAtLowerBound() {
        // x in [0, inf), wants x = 3; the backward difference is clamped away at x = 0
        BoxProblem problem = new BoxProblem(x -> new double[]{x[0] - 3}, new double[]{0}, new double[]{Double.POSITIVE_INFINITY});
        problem.tolerance = 1e-6;
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(30, 0.1, 0.02, 8.0, 2.5, 0.3, 10.0));

        assertEquals(3.0, state[0], CONVERGED);
    }

    @Test
    @DisplayName("when every parameter is pinned there is no step and nothing moves")
    void allPinned() {
        BoxProblem problem = new BoxProblem(x -> new double[]{x[0] - 5}, new double[]{0}, new double[]{2});
        double[] state = {2};
        solve(state, problem, new DampedLeastSquaresOptions(10));

        assertArrayEquals(new double[]{2}, state);
        assertTrue(problem.trials.isEmpty());
    }

    @Test
    @DisplayName("a fixed parameter has no slope and is never moved")
    void fixedParameter() {
        // x is fixed at 1, y is free: x + y = 5
        BoxProblem problem = new BoxProblem(
            x -> new double[]{x[0] + x[1] - 5},
            new double[]{1, Double.NEGATIVE_INFINITY},
            new double[]{1, Double.POSITIVE_INFINITY}
        );
        problem.tolerance = 1e-6;
        double[] state = {1, 0};
        solve(state, problem, new DampedLeastSquaresOptions(30, 0.1, 0.02, 8.0, 2.5, 0.3, 10.0));

        assertEquals(1.0, state[0], 0.0);
        assertEquals(4.0, state[1], CONVERGED);
    }

    @Test
    @DisplayName("damping starts clamped to its range and grows by the scale, up to the maximum, while steps fail")
    void dampingGrowsOnRejection() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 1});
        problem.ignoreSteps = true;
        double[] state = {0};
        // starting damping 0.25 is below the 0.5 minimum; scale 2; capped at 4
        solve(state, problem, new DampedLeastSquaresOptions(6, 0.25, 0.5, 4.0, 2.0, 0.3, 10.0));

        double[] dampings = problem.fullSteps().stream().mapToDouble(it -> dampingOf(it, 1.0)).toArray();
        assertArrayEquals(new double[]{0.5, 1, 2, 4, 4, 4}, dampings, 1e-9);
        assertArrayEquals(new double[]{0}, state);
    }

    @Test
    @DisplayName("damping above the maximum starts at the maximum")
    void dampingStartsAtMaximum() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 1});
        problem.ignoreSteps = true;
        solve(new double[]{0}, problem, new DampedLeastSquaresOptions(1, 100.0, 0.5, 4.0, 2.0, 0.3, 10.0));

        assertEquals(4.0, dampingOf(problem.fullSteps().get(0), 1.0), 1e-9);
    }

    @Test
    @DisplayName("damping shrinks by the scale, down to the minimum, while steps succeed")
    void dampingShrinksOnAcceptance() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 100});
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(6, 4.0, 0.5, 8.0, 2.0, 0.3, 1000.0));

        double[] dampings = problem.fullSteps().stream().mapToDouble(it -> dampingOf(it, 100.0)).toArray();
        assertArrayEquals(new double[]{4, 2, 1, 0.5, 0.5, 0.5}, dampings, 1e-6);
        assertTrue(Math.abs(state[0] - 100) < 1, "converging on 100, got " + state[0]);
    }

    @Test
    @DisplayName("each step is capped at maxStep")
    void maxStep() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 100});
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(5, 0.1, 0.02, 8.0, 2.5, 0.3, 0.25));

        assertEquals(1.25, state[0], 1e-9);
        for (Trial trial : problem.fullSteps()) assertEquals(0.25, trial.step()[0], 1e-12);
    }

    @Test
    @DisplayName("a negligible step is not rescaled even when above maxStep")
    void negligibleStepIsNotRescaled() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 1e-7});
        problem.ignoreSteps = true;
        solve(new double[]{0}, problem, new DampedLeastSquaresOptions(1, 0.0, 0.0, 1.0, 2.0, 0.3, 1e-8));

        assertEquals(1e-7, problem.fullSteps().get(0).step()[0], 1e-12);
    }

    @Test
    @DisplayName("an overshooting step is halved until it improves the score")
    void lineSearchHalvesTheStep() {
        // atan flattens out, so the linearised step from 0 badly overshoots the root at 3
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{Math.atan(x[0] - 3)});
        problem.tolerance = 1e-6;
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(40, 0.001, 0.001, 8.0, 2.5, 0.3, 100.0));

        List<Double> firstScales = new ArrayList<>();
        for (Trial trial : problem.trials) {
            if (trial.from()[0] != 0.0) break;
            firstScales.add(trial.scale());
        }
        assertEquals(List.of(1.0, 0.5, 0.25), firstScales);
        assertEquals(3.0, state[0], CONVERGED);
    }

    @Test
    @DisplayName("with minStepScale above 1 no step is ever tried")
    void noLineSearch() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{x[0] - 1});
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(5, 0.1, 0.02, 8.0, 2.5, 0.3, 1.0, 2.0));

        assertTrue(problem.trials.isEmpty());
        assertArrayEquals(new double[]{0}, state);
    }

    @Test
    @DisplayName("a step that would not improve the score by more than epsilon is rejected")
    void flatProblemDoesNotMove() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{0.5});
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(3));

        assertArrayEquals(new double[]{0}, state);
        assertTrue(problem.trials.stream().allMatch(it -> it.step()[0] == 0.0));
    }

    @Test
    @DisplayName("a singular system without damping gives no step and nothing moves")
    void singularSystem() {
        // y has no effect and there is no damping to regularise it
        BoxProblem problem = BoxProblem.unbounded(2, x -> new double[]{x[0] - 1});
        double[] state = {0, 0};
        solve(state, problem, new DampedLeastSquaresOptions(3, 0.0, 0.0, 0.0));

        assertTrue(problem.trials.isEmpty());
        assertArrayEquals(new double[]{0, 0}, state);
    }

    @Test
    @DisplayName("a non-finite error vector gives no step and nothing moves")
    void nonFiniteErrors() {
        BoxProblem problem = BoxProblem.unbounded(1, x -> new double[]{Double.NaN});
        double[] state = {0};
        solve(state, problem, new DampedLeastSquaresOptions(3));

        assertTrue(problem.trials.isEmpty());
        assertArrayEquals(new double[]{0}, state);
    }

    @Test
    @DisplayName("options fill in documented defaults")
    void optionDefaults() {
        DampedLeastSquaresOptions defaults = new DampedLeastSquaresOptions();
        assertEquals(24, defaults.getIterations());
        assertEquals(0.1, defaults.getDamping(), 0.0);
        assertEquals(0.02, defaults.getMinDamping(), 0.0);
        assertEquals(8.0, defaults.getMaxDamping(), 0.0);
        assertEquals(2.5, defaults.getDampingScale(), 0.0);
        assertEquals(0.3, defaults.getFiniteDifference(), 0.0);
        assertEquals(1.0, defaults.getMaxStep(), 0.0);
        assertEquals(1.0 / 64, defaults.getMinStepScale(), 0.0);

        DampedLeastSquaresOptions full = new DampedLeastSquaresOptions(3, 0.2, 0.1, 5.0, 3.0, 0.05, 0.5, 0.25);
        assertEquals(3, full.getIterations());
        assertEquals(0.2, full.getDamping(), 0.0);
        assertEquals(0.1, full.getMinDamping(), 0.0);
        assertEquals(5.0, full.getMaxDamping(), 0.0);
        assertEquals(3.0, full.getDampingScale(), 0.0);
        assertEquals(0.05, full.getFiniteDifference(), 0.0);
        assertEquals(0.5, full.getMaxStep(), 0.0);
        assertEquals(0.25, full.getMinStepScale(), 0.0);
    }
}
