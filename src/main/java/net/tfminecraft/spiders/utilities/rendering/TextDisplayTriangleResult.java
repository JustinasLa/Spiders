package net.tfminecraft.spiders.utilities.rendering;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public class TextDisplayTriangleResult {
    private final List<Matrix4f> transforms;
    private final Vector3f xAxis;
    private final Vector3f yAxis;
    private final Vector3f zAxis;
    private final float height;
    private final float width;
    private final Quaternionf rotation;
    private final float shear;

    public TextDisplayTriangleResult(
        List<Matrix4f> transforms,
        Vector3f xAxis,
        Vector3f yAxis,
        Vector3f zAxis,
        float height,
        float width,
        Quaternionf rotation,
        float shear
    ) {
        this.transforms = transforms;
        this.xAxis = xAxis;
        this.yAxis = yAxis;
        this.zAxis = zAxis;
        this.height = height;
        this.width = width;
        this.rotation = rotation;
        this.shear = shear;
    }

    public List<Matrix4f> getTransforms() {
        return transforms;
    }

    public Vector3f getXAxis() {
        return xAxis;
    }

    public Vector3f getYAxis() {
        return yAxis;
    }

    public Vector3f getZAxis() {
        return zAxis;
    }

    public float getHeight() {
        return height;
    }

    public float getWidth() {
        return width;
    }

    public Quaternionf getRotation() {
        return rotation;
    }

    public float getShear() {
        return shear;
    }
}
