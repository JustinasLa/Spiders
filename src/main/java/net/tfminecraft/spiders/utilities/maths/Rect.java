package net.tfminecraft.spiders.utilities.maths;

import org.joml.Vector2d;

public class Rect {
    private double minX;
    private double minY;
    private double maxX;
    private double maxY;

    private Rect(double minX, double minY, double maxX, double maxY) {
        this.minX = minX;
        this.minY = minY;
        this.maxX = maxX;
        this.maxY = maxY;
    }

    public double getMinX() {
        return minX;
    }

    public void setMinX(double minX) {
        this.minX = minX;
    }

    public double getMinY() {
        return minY;
    }

    public void setMinY(double minY) {
        this.minY = minY;
    }

    public double getMaxX() {
        return maxX;
    }

    public void setMaxX(double maxX) {
        this.maxX = maxX;
    }

    public double getMaxY() {
        return maxY;
    }

    public void setMaxY(double maxY) {
        this.maxY = maxY;
    }

    public double getWidth() {
        return maxX - minX;
    }

    public double getHeight() {
        return maxY - minY;
    }

    public Vector2d getDimensions() {
        return new Vector2d(getWidth(), getHeight());
    }

    public static Rect fromMinMax(Vector2d min, Vector2d max) {
        return new Rect(min.x, min.y, max.x, max.y);
    }

    public static Rect fromCenter(Vector2d center, Vector2d dimensions) {
        return new Rect(
            center.x - dimensions.x / 2,
            center.y - dimensions.y / 2,
            center.x + dimensions.x / 2,
            center.y + dimensions.y / 2
        );
    }

    public Rect clone() {
        return new Rect(minX, minY, maxX, maxY);
    }

    public Rect expand(double padding) {
        minX -= padding;
        minY -= padding;
        maxX += padding;
        maxY += padding;
        return this;
    }

    public Rect setYCenter(double center, double height) {
        minY = center - height / 2;
        maxY = center + height / 2;
        return this;
    }

    public Rect lerp(Rect other, double t) {
        minX = Maths.lerp(minX, other.minX, t);
        minY = Maths.lerp(minY, other.minY, t);
        maxX = Maths.lerp(maxX, other.maxX, t);
        maxY = Maths.lerp(maxY, other.maxY, t);
        return this;
    }

    public Rect set(Rect other) {
        minX = other.minX;
        minY = other.minY;
        maxX = other.maxX;
        maxY = other.maxY;
        return this;
    }
}
