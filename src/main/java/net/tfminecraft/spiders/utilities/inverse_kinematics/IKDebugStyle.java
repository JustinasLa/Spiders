package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.Material;

public final class IKDebugStyle {
    private final double hingeArcRadiusFraction;
    private final double swingConeLengthFraction;
    private final double twistArcDistanceFraction;
    private final double twistArcRadius;
    private final double twistIndicatorLength;
    private final double axisLength;
    private final double targetAxisLength;

    private final int hingeArcSteps;
    private final int twistArcSteps;
    private final int coneRingSteps;
    private final int coneSpokes;

    private final boolean drawNodeAxes;
    private final boolean drawConstraints;

    private final Material hingeColor;
    private final Material swingColor;
    private final Material twistArcColor;
    private final Material twistIndicatorColor;

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes,
        boolean drawNodeAxes,
        boolean drawConstraints,
        Material hingeColor,
        Material swingColor,
        Material twistArcColor,
        Material twistIndicatorColor
    ) {
        this.hingeArcRadiusFraction = hingeArcRadiusFraction;
        this.swingConeLengthFraction = swingConeLengthFraction;
        this.twistArcDistanceFraction = twistArcDistanceFraction;
        this.twistArcRadius = twistArcRadius;
        this.twistIndicatorLength = twistIndicatorLength;
        this.axisLength = axisLength;
        this.targetAxisLength = targetAxisLength;
        this.hingeArcSteps = hingeArcSteps;
        this.twistArcSteps = twistArcSteps;
        this.coneRingSteps = coneRingSteps;
        this.coneSpokes = coneSpokes;
        this.drawNodeAxes = drawNodeAxes;
        this.drawConstraints = drawConstraints;
        this.hingeColor = hingeColor;
        this.swingColor = swingColor;
        this.twistArcColor = twistArcColor;
        this.twistIndicatorColor = twistIndicatorColor;
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes,
        boolean drawNodeAxes,
        boolean drawConstraints,
        Material hingeColor,
        Material swingColor,
        Material twistArcColor
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, coneSpokes, drawNodeAxes, drawConstraints, hingeColor, swingColor, twistArcColor,
            Material.MAGENTA_CONCRETE);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes,
        boolean drawNodeAxes,
        boolean drawConstraints,
        Material hingeColor,
        Material swingColor
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, coneSpokes, drawNodeAxes, drawConstraints, hingeColor, swingColor,
            Material.PINK_CONCRETE);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes,
        boolean drawNodeAxes,
        boolean drawConstraints,
        Material hingeColor
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, coneSpokes, drawNodeAxes, drawConstraints, hingeColor,
            Material.CYAN_CONCRETE);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes,
        boolean drawNodeAxes,
        boolean drawConstraints
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, coneSpokes, drawNodeAxes, drawConstraints,
            Material.LIGHT_BLUE_CONCRETE);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes,
        boolean drawNodeAxes
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, coneSpokes, drawNodeAxes, true);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps,
        int coneSpokes
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, coneSpokes, true);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps,
        int coneRingSteps
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, coneRingSteps, 3);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps,
        int twistArcSteps
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, twistArcSteps, 12);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength,
        int hingeArcSteps
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            hingeArcSteps, 10);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength,
        double targetAxisLength
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, targetAxisLength,
            18);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength,
        double axisLength
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, axisLength, 1.0);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius,
        double twistIndicatorLength
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, twistIndicatorLength, 0.6);
    }

    public IKDebugStyle(
        double hingeArcRadiusFraction,
        double swingConeLengthFraction,
        double twistArcDistanceFraction,
        double twistArcRadius
    ) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, twistArcRadius, 0.4);
    }

    public IKDebugStyle(double hingeArcRadiusFraction, double swingConeLengthFraction, double twistArcDistanceFraction) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, twistArcDistanceFraction, 0.3);
    }

    public IKDebugStyle(double hingeArcRadiusFraction, double swingConeLengthFraction) {
        this(hingeArcRadiusFraction, swingConeLengthFraction, 0.8);
    }

    public IKDebugStyle(double hingeArcRadiusFraction) {
        this(hingeArcRadiusFraction, 1.0);
    }

    public IKDebugStyle() {
        this(0.6);
    }

    public double hingeArcRadius(double length) {
        return length * hingeArcRadiusFraction;
    }

    public double swingConeLength(double length) {
        return length * swingConeLengthFraction;
    }

    public double getHingeArcRadiusFraction() {
        return hingeArcRadiusFraction;
    }

    public double getSwingConeLengthFraction() {
        return swingConeLengthFraction;
    }

    public double getTwistArcDistanceFraction() {
        return twistArcDistanceFraction;
    }

    public double getTwistArcRadius() {
        return twistArcRadius;
    }

    public double getTwistIndicatorLength() {
        return twistIndicatorLength;
    }

    public double getAxisLength() {
        return axisLength;
    }

    public double getTargetAxisLength() {
        return targetAxisLength;
    }

    public int getHingeArcSteps() {
        return hingeArcSteps;
    }

    public int getTwistArcSteps() {
        return twistArcSteps;
    }

    public int getConeRingSteps() {
        return coneRingSteps;
    }

    public int getConeSpokes() {
        return coneSpokes;
    }

    public boolean getDrawNodeAxes() {
        return drawNodeAxes;
    }

    public boolean getDrawConstraints() {
        return drawConstraints;
    }

    public Material getHingeColor() {
        return hingeColor;
    }

    public Material getSwingColor() {
        return swingColor;
    }

    public Material getTwistArcColor() {
        return twistArcColor;
    }

    public Material getTwistIndicatorColor() {
        return twistIndicatorColor;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof IKDebugStyle that)) return false;
        return Double.compare(hingeArcRadiusFraction, that.hingeArcRadiusFraction) == 0
            && Double.compare(swingConeLengthFraction, that.swingConeLengthFraction) == 0
            && Double.compare(twistArcDistanceFraction, that.twistArcDistanceFraction) == 0
            && Double.compare(twistArcRadius, that.twistArcRadius) == 0
            && Double.compare(twistIndicatorLength, that.twistIndicatorLength) == 0
            && Double.compare(axisLength, that.axisLength) == 0
            && Double.compare(targetAxisLength, that.targetAxisLength) == 0
            && hingeArcSteps == that.hingeArcSteps
            && twistArcSteps == that.twistArcSteps
            && coneRingSteps == that.coneRingSteps
            && coneSpokes == that.coneSpokes
            && drawNodeAxes == that.drawNodeAxes
            && drawConstraints == that.drawConstraints
            && hingeColor == that.hingeColor
            && swingColor == that.swingColor
            && twistArcColor == that.twistArcColor
            && twistIndicatorColor == that.twistIndicatorColor;
    }

    @Override
    public int hashCode() {
        int result = Double.hashCode(hingeArcRadiusFraction);
        result = result * 31 + Double.hashCode(swingConeLengthFraction);
        result = result * 31 + Double.hashCode(twistArcDistanceFraction);
        result = result * 31 + Double.hashCode(twistArcRadius);
        result = result * 31 + Double.hashCode(twistIndicatorLength);
        result = result * 31 + Double.hashCode(axisLength);
        result = result * 31 + Double.hashCode(targetAxisLength);
        result = result * 31 + Integer.hashCode(hingeArcSteps);
        result = result * 31 + Integer.hashCode(twistArcSteps);
        result = result * 31 + Integer.hashCode(coneRingSteps);
        result = result * 31 + Integer.hashCode(coneSpokes);
        result = result * 31 + Boolean.hashCode(drawNodeAxes);
        result = result * 31 + Boolean.hashCode(drawConstraints);
        result = result * 31 + hingeColor.hashCode();
        result = result * 31 + swingColor.hashCode();
        result = result * 31 + twistArcColor.hashCode();
        result = result * 31 + twistIndicatorColor.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "IKDebugStyle(hingeArcRadiusFraction=" + hingeArcRadiusFraction
            + ", swingConeLengthFraction=" + swingConeLengthFraction
            + ", twistArcDistanceFraction=" + twistArcDistanceFraction
            + ", twistArcRadius=" + twistArcRadius
            + ", twistIndicatorLength=" + twistIndicatorLength
            + ", axisLength=" + axisLength
            + ", targetAxisLength=" + targetAxisLength
            + ", hingeArcSteps=" + hingeArcSteps
            + ", twistArcSteps=" + twistArcSteps
            + ", coneRingSteps=" + coneRingSteps
            + ", coneSpokes=" + coneSpokes
            + ", drawNodeAxes=" + drawNodeAxes
            + ", drawConstraints=" + drawConstraints
            + ", hingeColor=" + hingeColor
            + ", swingColor=" + swingColor
            + ", twistArcColor=" + twistArcColor
            + ", twistIndicatorColor=" + twistIndicatorColor
            + ")";
    }
}
