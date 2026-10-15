package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.spider.components.body.GaitType;
import net.tfminecraft.spiders.utilities.maths.Maths;

public class Gait {
    /** Comfort zone kept around a lengthened stride. */
    private static final double STRIDE_COMFORT_RATIO = 1.5;

    private final GaitType type;

    private Posture stationary = new Posture(
        1.1,
        .25
    );

    private Posture moving = new Posture(
        1.1,
        .8
    );

    private double maxBodyDistanceFromGround = .25;

    private double maxSpeed;
    private double moveAcceleration = .15 / 4;

    private float rotateAcceleration = .15f / 4;
    private float rotationalDragCoefficient = .2f;

    private double legMoveSpeed;

    private double legLiftHeight = .35;

    private double comfortZoneRadius = 1.2;

    private double gravityAcceleration = .08;
    private double airDragCoefficient = .02;
    private double bounceFactor = .5;

    private double bodyHeightCorrectionAcceleration = gravityAcceleration * 4;
    private double bodyHeightCorrectionFactor = .25;

    private boolean legScanAlternativeGround = true;
    private double legScanHeightBias = .5;

    private double tridentKnockBack = .3;
    private double tridentRotationalKnockBack = tridentKnockBack / 4;
    private double legLookAheadFraction = .6;
    private double groundDragCoefficient = .2;

    private int samePairCooldown = 1;
    private int crossPairCooldown = 1;

    private double gripAcceleration = gravityAcceleration * 4;
    private boolean grip = false;

    private double topplingAcceleration = gravityAcceleration / 4;

    private double uncomfortableSpeedMultiplier = 0.0;

    private float preferredPitchLeeway = Maths.toRadians(10f);

    private OrientationMode scanOrientation = OrientationMode.YAxis;

    private float preferredRotationLerpFraction = .3f;

    private float rotationLerp = .3f;

    public Gait(double walkSpeed, GaitType type) {
        this.type = type;
        this.maxSpeed = walkSpeed;
        this.legMoveSpeed = walkSpeed * 2.5;
    }

    public static Gait defaultWalk() {
        return new Gait(.15, GaitType.WALK);
    }

    public static Gait defaultGallop() {
        Gait gait = new Gait(.4, GaitType.GALLOP);
        gait.moving.setBodyHeight(1.6);
        gait.legMoveSpeed = .5;
        gait.rotateAcceleration = .25f / 4;
        gait.uncomfortableSpeedMultiplier = .6;
        gait.samePairCooldown = 2;
        gait.crossPairCooldown = 4;
        return gait;
    }

    public void scale(double scale) {
        stationary.scale(scale);
        moving.scale(scale);
        maxBodyDistanceFromGround *= scale;
        maxSpeed *= scale;
        moveAcceleration *= scale;
        legMoveSpeed *= scale;
        legLiftHeight *= scale;
        comfortZoneRadius *= scale;
        legScanHeightBias *= scale;
        tridentRotationalKnockBack /= scale;
    }

    /**
     * Change the top speed. Stride length, stride speed and acceleration follow in proportion,
     * so steps keep their rhythm instead of falling behind (which would stall the spider).
     */
    public void setSpeed(double speed) {
        double factor = speed / maxSpeed;
        maxSpeed = speed;
        legMoveSpeed *= factor;
        moveAcceleration *= factor;
        moving.setTriggerZoneRadius(moving.getTriggerZoneRadius() * factor);
        comfortZoneRadius = Math.max(comfortZoneRadius, moving.getTriggerZoneRadius() * STRIDE_COMFORT_RATIO);
    }

    public GaitType getType() {
        return type;
    }
    public Posture getStationary() {
        return stationary;
    }

    public void setStationary(Posture stationary) {
        this.stationary = stationary;
    }

    public Posture getMoving() {
        return moving;
    }

    public void setMoving(Posture moving) {
        this.moving = moving;
    }

    public double getMaxBodyDistanceFromGround() {
        return maxBodyDistanceFromGround;
    }

    public void setMaxBodyDistanceFromGround(double maxBodyDistanceFromGround) {
        this.maxBodyDistanceFromGround = maxBodyDistanceFromGround;
    }

    public double getMaxSpeed() {
        return maxSpeed;
    }

    public void setMaxSpeed(double maxSpeed) {
        this.maxSpeed = maxSpeed;
    }

    public double getMoveAcceleration() {
        return moveAcceleration;
    }

    public void setMoveAcceleration(double moveAcceleration) {
        this.moveAcceleration = moveAcceleration;
    }

    public float getRotateAcceleration() {
        return rotateAcceleration;
    }

    public void setRotateAcceleration(float rotateAcceleration) {
        this.rotateAcceleration = rotateAcceleration;
    }

    public float getRotationalDragCoefficient() {
        return rotationalDragCoefficient;
    }

    public void setRotationalDragCoefficient(float rotationalDragCoefficient) {
        this.rotationalDragCoefficient = rotationalDragCoefficient;
    }

    public double getLegMoveSpeed() {
        return legMoveSpeed;
    }

    public void setLegMoveSpeed(double legMoveSpeed) {
        this.legMoveSpeed = legMoveSpeed;
    }

    public double getLegLiftHeight() {
        return legLiftHeight;
    }

    public void setLegLiftHeight(double legLiftHeight) {
        this.legLiftHeight = legLiftHeight;
    }

    public double getComfortZoneRadius() {
        return comfortZoneRadius;
    }

    public void setComfortZoneRadius(double comfortZoneRadius) {
        this.comfortZoneRadius = comfortZoneRadius;
    }

    public double getGravityAcceleration() {
        return gravityAcceleration;
    }

    public void setGravityAcceleration(double gravityAcceleration) {
        this.gravityAcceleration = gravityAcceleration;
    }

    public double getAirDragCoefficient() {
        return airDragCoefficient;
    }

    public void setAirDragCoefficient(double airDragCoefficient) {
        this.airDragCoefficient = airDragCoefficient;
    }

    public double getBounceFactor() {
        return bounceFactor;
    }

    public void setBounceFactor(double bounceFactor) {
        this.bounceFactor = bounceFactor;
    }

    public double getBodyHeightCorrectionAcceleration() {
        return bodyHeightCorrectionAcceleration;
    }

    public void setBodyHeightCorrectionAcceleration(double bodyHeightCorrectionAcceleration) {
        this.bodyHeightCorrectionAcceleration = bodyHeightCorrectionAcceleration;
    }

    public double getBodyHeightCorrectionFactor() {
        return bodyHeightCorrectionFactor;
    }

    public void setBodyHeightCorrectionFactor(double bodyHeightCorrectionFactor) {
        this.bodyHeightCorrectionFactor = bodyHeightCorrectionFactor;
    }

    public boolean getLegScanAlternativeGround() {
        return legScanAlternativeGround;
    }

    public void setLegScanAlternativeGround(boolean legScanAlternativeGround) {
        this.legScanAlternativeGround = legScanAlternativeGround;
    }

    public double getLegScanHeightBias() {
        return legScanHeightBias;
    }

    public void setLegScanHeightBias(double legScanHeightBias) {
        this.legScanHeightBias = legScanHeightBias;
    }

    public double getTridentKnockBack() {
        return tridentKnockBack;
    }

    public void setTridentKnockBack(double tridentKnockBack) {
        this.tridentKnockBack = tridentKnockBack;
    }

    public double getTridentRotationalKnockBack() {
        return tridentRotationalKnockBack;
    }

    public void setTridentRotationalKnockBack(double tridentRotationalKnockBack) {
        this.tridentRotationalKnockBack = tridentRotationalKnockBack;
    }

    public double getLegLookAheadFraction() {
        return legLookAheadFraction;
    }

    public void setLegLookAheadFraction(double legLookAheadFraction) {
        this.legLookAheadFraction = legLookAheadFraction;
    }

    public double getGroundDragCoefficient() {
        return groundDragCoefficient;
    }

    public void setGroundDragCoefficient(double groundDragCoefficient) {
        this.groundDragCoefficient = groundDragCoefficient;
    }

    public int getSamePairCooldown() {
        return samePairCooldown;
    }

    public void setSamePairCooldown(int samePairCooldown) {
        this.samePairCooldown = samePairCooldown;
    }

    public int getCrossPairCooldown() {
        return crossPairCooldown;
    }

    public void setCrossPairCooldown(int crossPairCooldown) {
        this.crossPairCooldown = crossPairCooldown;
    }

    public double getGripAcceleration() {
        return gripAcceleration;
    }

    public void setGripAcceleration(double gripAcceleration) {
        this.gripAcceleration = gripAcceleration;
    }

    public boolean getGrip() {
        return grip;
    }

    public void setGrip(boolean grip) {
        this.grip = grip;
    }

    public double getTopplingAcceleration() {
        return topplingAcceleration;
    }

    public void setTopplingAcceleration(double topplingAcceleration) {
        this.topplingAcceleration = topplingAcceleration;
    }

    public double getUncomfortableSpeedMultiplier() {
        return uncomfortableSpeedMultiplier;
    }

    public void setUncomfortableSpeedMultiplier(double uncomfortableSpeedMultiplier) {
        this.uncomfortableSpeedMultiplier = uncomfortableSpeedMultiplier;
    }

    public float getPreferredPitchLeeway() {
        return preferredPitchLeeway;
    }

    public void setPreferredPitchLeeway(float preferredPitchLeeway) {
        this.preferredPitchLeeway = preferredPitchLeeway;
    }

    public OrientationMode getScanOrientation() {
        return scanOrientation;
    }

    public void setScanOrientation(OrientationMode scanOrientation) {
        this.scanOrientation = scanOrientation;
    }

    public float getPreferredRotationLerpFraction() {
        return preferredRotationLerpFraction;
    }

    public void setPreferredRotationLerpFraction(float preferredRotationLerpFraction) {
        this.preferredRotationLerpFraction = preferredRotationLerpFraction;
    }

    public float getRotationLerp() {
        return rotationLerp;
    }

    public void setRotationLerp(float rotationLerp) {
        this.rotationLerp = rotationLerp;
    }
}
