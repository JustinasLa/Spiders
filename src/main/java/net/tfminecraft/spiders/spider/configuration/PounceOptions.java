package net.tfminecraft.spiders.spider.configuration;

/** A pounce gallops at a target and leaps the last stretch. The run uses the gallop gait's speed. */
public class PounceOptions {
    /** Longest leap in blocks for a scale 1 spider; scales with the spider. */
    private double leapRange = 5.0;

    /** Fastest leap along the ground, as a multiple of the gallop speed. */
    private double leapBoost = 2.0;

    /** Upward launch speed in blocks per tick for a scale 1 spider; scales with the square root of its size. */
    private double leapLift = .5;

    /** Give up the chase after this many ticks. */
    private int giveUpTicks = 400;

    public double getLeapRange() {
        return leapRange;
    }

    public void setLeapRange(double leapRange) {
        this.leapRange = leapRange;
    }

    public double getLeapBoost() {
        return leapBoost;
    }

    public void setLeapBoost(double leapBoost) {
        this.leapBoost = leapBoost;
    }

    public double getLeapLift() {
        return leapLift;
    }

    public void setLeapLift(double leapLift) {
        this.leapLift = leapLift;
    }

    public int getGiveUpTicks() {
        return giveUpTicks;
    }

    public void setGiveUpTicks(int giveUpTicks) {
        this.giveUpTicks = giveUpTicks;
    }
}
