package net.tfminecraft.spiders.spider.configuration;

public class CloakOptions {
    private double moveSpeed = 1.0 / 255;
    private double lerpSpeed = .3;
    private double lerpRandomness = .3;
    /** Dimmed bright blocks match more colours but look washed out on bright gamma settings. */
    private boolean allowCustomBrightness = false;

    public double getMoveSpeed() {
        return moveSpeed;
    }

    public void setMoveSpeed(double moveSpeed) {
        this.moveSpeed = moveSpeed;
    }

    public double getLerpSpeed() {
        return lerpSpeed;
    }

    public void setLerpSpeed(double lerpSpeed) {
        this.lerpSpeed = lerpSpeed;
    }

    public double getLerpRandomness() {
        return lerpRandomness;
    }

    public void setLerpRandomness(double lerpRandomness) {
        this.lerpRandomness = lerpRandomness;
    }

    public boolean getAllowCustomBrightness() {
        return allowCustomBrightness;
    }

    public void setAllowCustomBrightness(boolean allowCustomBrightness) {
        this.allowCustomBrightness = allowCustomBrightness;
    }
}
