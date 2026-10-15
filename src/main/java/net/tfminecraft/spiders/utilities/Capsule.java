package net.tfminecraft.spiders.utilities;

import org.bukkit.util.Vector;

public class Capsule {
    private final Vector point1;
    private final Vector point2;
    private final double radius;

    public Capsule(Vector point1, Vector point2, double radius) {
        this.point1 = point1;
        this.point2 = point2;
        this.radius = radius;
    }

    public Vector getPoint1() {
        return point1;
    }

    public Vector getPoint2() {
        return point2;
    }

    public double getRadius() {
        return radius;
    }

    public LineSegment line() {
        return new LineSegment(point1, point2);
    }

    public boolean contains(Vector point) {
        return line().distanceSquared(point) <= radius * radius;
    }

    public Vector axis() {
        return point2.clone().subtract(point1);
    }
}
