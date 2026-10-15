package net.tfminecraft.spiders.spider.components.body;

import org.bukkit.util.Vector;

import java.util.List;

public class ContactInfo {
    // most of these fields are only used for debug rendering
    private final Vector supportAxis;
    private final Vector centerOfPressure;
    private final List<Vector> contactPolygon;
    private final Vector centreOfMass;
    private final boolean isStable;

    public ContactInfo(Vector supportAxis, Vector centerOfPressure, List<Vector> contactPolygon, Vector centreOfMass, boolean isStable) {
        this.supportAxis = supportAxis;
        this.centerOfPressure = centerOfPressure;
        this.contactPolygon = contactPolygon;
        this.centreOfMass = centreOfMass;
        this.isStable = isStable;
    }

    public ContactInfo(Vector supportAxis, Vector centerOfPressure, List<Vector> contactPolygon, Vector centreOfMass) {
        this(supportAxis, centerOfPressure, contactPolygon, centreOfMass, true);
    }

    public ContactInfo(Vector supportAxis, Vector centerOfPressure, List<Vector> contactPolygon) {
        this(supportAxis, centerOfPressure, contactPolygon, null, true);
    }

    public ContactInfo(Vector supportAxis, Vector centerOfPressure) {
        this(supportAxis, centerOfPressure, null, null, true);
    }

    public ContactInfo(Vector supportAxis) {
        this(supportAxis, null, null, null, true);
    }

    public Vector getSupportAxis() {
        return supportAxis;
    }

    public Vector getCenterOfPressure() {
        return centerOfPressure;
    }

    public List<Vector> getContactPolygon() {
        return contactPolygon;
    }

    public Vector getCentreOfMass() {
        return centreOfMass;
    }

    public boolean isStable() {
        return isStable;
    }
}
