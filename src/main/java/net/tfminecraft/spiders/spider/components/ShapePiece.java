package net.tfminecraft.spiders.spider.components;

import org.bukkit.util.Vector;
import org.joml.Matrix4f;

/** A drawn block: the unit cube mapped by [transform], placed relative to [origin]. */
public class ShapePiece {
    private final Vector origin;
    private final Matrix4f transform;

    public ShapePiece(Vector origin, Matrix4f transform) {
        this.origin = origin;
        this.transform = transform;
    }

    public Vector getOrigin() {
        return origin;
    }

    public Matrix4f getTransform() {
        return transform;
    }
}
