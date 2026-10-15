package net.tfminecraft.spiders.spider.configuration;

public class SpiderDebugOptions {
    private boolean scanBars = true;
    private boolean triggerZones = true;
    private boolean endEffectors = true;
    private boolean targetPositions = true;
    private boolean ikGuides = true;
    private boolean legPolygons = true;
    private boolean centreOfMass = true;
    private boolean normalForce = true;
    private boolean orientation = true;
    private boolean preferredOrientation = true;

    public boolean getScanBars() {
        return scanBars;
    }

    public void setScanBars(boolean scanBars) {
        this.scanBars = scanBars;
    }

    public boolean getTriggerZones() {
        return triggerZones;
    }

    public void setTriggerZones(boolean triggerZones) {
        this.triggerZones = triggerZones;
    }

    public boolean getEndEffectors() {
        return endEffectors;
    }

    public void setEndEffectors(boolean endEffectors) {
        this.endEffectors = endEffectors;
    }

    public boolean getTargetPositions() {
        return targetPositions;
    }

    public void setTargetPositions(boolean targetPositions) {
        this.targetPositions = targetPositions;
    }

    public boolean getIkGuides() {
        return ikGuides;
    }

    public void setIkGuides(boolean ikGuides) {
        this.ikGuides = ikGuides;
    }

    public boolean getLegPolygons() {
        return legPolygons;
    }

    public void setLegPolygons(boolean legPolygons) {
        this.legPolygons = legPolygons;
    }

    public boolean getCentreOfMass() {
        return centreOfMass;
    }

    public void setCentreOfMass(boolean centreOfMass) {
        this.centreOfMass = centreOfMass;
    }

    public boolean getNormalForce() {
        return normalForce;
    }

    public void setNormalForce(boolean normalForce) {
        this.normalForce = normalForce;
    }

    public boolean getOrientation() {
        return orientation;
    }

    public void setOrientation(boolean orientation) {
        this.orientation = orientation;
    }

    public boolean getPreferredOrientation() {
        return preferredOrientation;
    }

    public void setPreferredOrientation(boolean preferredOrientation) {
        this.preferredOrientation = preferredOrientation;
    }
}
