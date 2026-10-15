package net.tfminecraft.spiders.utilities.inverse_kinematics;

public interface DampedLeastSquaresMetrics {
    double[] getErrorVector();

    double getScore();

    boolean isSolved();
}
