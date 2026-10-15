package net.tfminecraft.spiders.utilities;

import org.joml.Vector2d;

import java.util.List;

public final class Polygons {
    private Polygons() {
    }

    public static boolean pointInPolygon(Vector2d point, List<Vector2d> polygon) {
        // count intersections
        int count = 0;
        for (int i = 0; i < polygon.size(); i++) {
            Vector2d a = polygon.get(i);
            Vector2d b = polygon.get((i + 1) % polygon.size());

            if (a.y <= point.y && b.y > point.y || b.y <= point.y && a.y > point.y) {
                double slope = (b.x - a.x) / (b.y - a.y);
                double intersect = a.x + (point.y - a.y) * slope;
                if (intersect < point.x) count++;
            }
        }

        return count % 2 == 1;
    }

    public static Vector2d nearestPointInPolygon(Vector2d point, List<Vector2d> polygon) {
        Vector2d closest = polygon.get(0);
        double closestDistance = point.distance(closest);

        for (int i = 0; i < polygon.size(); i++) {
            Vector2d a = polygon.get(i);
            Vector2d b = polygon.get((i + 1) % polygon.size());

            Vector2d closestOnLine = nearestPointOnClampedLine(point, a, b);
            double distance = point.distance(closestOnLine);

            if (distance < closestDistance) {
                closest = closestOnLine;
                closestDistance = distance;
            }
        }

        return closest;
    }

    public static Vector2d nearestPointOnClampedLine(Vector2d point, Vector2d a, Vector2d b) {
        Vector2d ap = new Vector2d(point.x - a.x, point.y - a.y);
        Vector2d ab = new Vector2d(b.x - a.x, b.y - a.y);

        double dotProduct = ap.dot(ab);
        double lengthAB = a.distance(b);

        double t = dotProduct / (lengthAB * lengthAB);

        // Ensure the nearest point lies within the line segment
        double tClamped = coerceIn(t, 0.0, 1.0);

        double nearestX = a.x + tClamped * ab.x;
        double nearestY = a.y + tClamped * ab.y;

        return new Vector2d(nearestX, nearestY);
    }

    // Kotlin's coerceIn (keeps -0.0 and NaN as they are, unlike Math.clamp)
    private static double coerceIn(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
