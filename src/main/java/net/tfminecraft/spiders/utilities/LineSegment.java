package net.tfminecraft.spiders.utilities;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;

public class LineSegment {
    private final Vector point1;
    private final Vector point2;

    public LineSegment(Vector point1, Vector point2) {
        this.point1 = point1;
        this.point2 = point2;
    }

    public static LineSegment fromOffset(Vector origin, Vector offset) {
        return new LineSegment(origin, origin.clone().add(offset));
    }

    public Vector getPoint1() {
        return point1;
    }

    public Vector getPoint2() {
        return point2;
    }

    public Vector vector() {
        return point2.clone().subtract(point1);
    }

    public double distance(Vector point) {
        return Math.sqrt(distanceSquared(point));
    }

    public double distanceSquared(Vector point) {
        double abX = point2.getX() - point1.getX();
        double abY = point2.getY() - point1.getY();
        double abZ = point2.getZ() - point1.getZ();
        double t = projectionFactor(point);
        double dx = point.getX() - (point1.getX() + abX * t);
        double dy = point.getY() - (point1.getY() + abY * t);
        double dz = point.getZ() - (point1.getZ() + abZ * t);
        return dx * dx + dy * dy + dz * dz;
    }

    private double projectionFactor(Vector point) {
        double abX = point2.getX() - point1.getX();
        double abY = point2.getY() - point1.getY();
        double abZ = point2.getZ() - point1.getZ();
        double lenSq = abX * abX + abY * abY + abZ * abZ;
        if (lenSq < 1e-12) return 0.0;
        double apX = point.getX() - point1.getX();
        double apY = point.getY() - point1.getY();
        double apZ = point.getZ() - point1.getZ();
        return Maths.clamp((apX * abX + apY * abY + apZ * abZ) / lenSq, 0.0, 1.0);
    }

}
