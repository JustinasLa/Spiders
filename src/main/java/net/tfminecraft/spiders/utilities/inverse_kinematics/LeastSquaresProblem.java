package net.tfminecraft.spiders.utilities.inverse_kinematics;

import java.util.List;

public interface LeastSquaresProblem<State, Parameter, Metrics extends DampedLeastSquaresMetrics> {
    State cloneState(State state);

    void copyState(State destination, State source);

    Metrics evaluateState(State state);

    List<Parameter> listParameters(State state);

    /** Applies `delta` to `parameter`, returning the delta actually applied after clamping. */
    double perturbParameter(State state, Parameter parameter, double delta);

    /** Returns the delta that perturbParameter would apply, i.e. `delta` clipped to the parameter's range. */
    double clampDelta(State state, Parameter parameter, double delta);

    void applyStep(State state, List<Parameter> parameters, double[] step, double scale);
}
