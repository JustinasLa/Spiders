package net.tfminecraft.spiders.spider.configuration;

import net.tfminecraft.spiders.utilities.maths.Maths;

public class Posture {
    private double bodyHeight;
    private double triggerZoneRadius;

    public Posture(double bodyHeight, double triggerZoneRadius) {
        this.bodyHeight = bodyHeight;
        this.triggerZoneRadius = triggerZoneRadius;
    }

    public double getBodyHeight() {
        return bodyHeight;
    }

    public void setBodyHeight(double bodyHeight) {
        this.bodyHeight = bodyHeight;
    }

    public double getTriggerZoneRadius() {
        return triggerZoneRadius;
    }

    public void setTriggerZoneRadius(double triggerZoneRadius) {
        this.triggerZoneRadius = triggerZoneRadius;
    }

    public Posture scale(double scale) {
        bodyHeight *= scale;
        triggerZoneRadius *= scale;
        return this;
    }

    public Posture clone() {
        return new Posture(
            bodyHeight,
            triggerZoneRadius
        );
    }

    public Posture lerp(Posture target, double factor) {
        this.bodyHeight = Maths.lerp(bodyHeight, target.bodyHeight, factor);
        this.triggerZoneRadius = Maths.lerp(triggerZoneRadius, target.triggerZoneRadius, factor);
        return this;
    }
}
