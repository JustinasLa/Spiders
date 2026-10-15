package net.tfminecraft.spiders.utilities.maths;

import org.bukkit.Location;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.List;

public final class Maths {
    private Maths() {
    }

    public static Vector downVector() {
        return new Vector(0, -1, 0);
    }

    public static Vector upVector() {
        return new Vector(0, 1, 0);
    }

    public static Vector forwardVector() {
        return new Vector(0, 0, 1);
    }

    public static Vector backwardVector() {
        return new Vector(0, 0, -1);
    }

    public static Vector leftVector() {
        return new Vector(-1, 0, 0);
    }

    public static Vector rightVector() {
        return new Vector(1, 0, 0);
    }

    public static Vector4f toVector4f(Vector vector) {
        return new Vector4f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ(), 1f);
    }

    public static Vector4f toVector4f(Vector3f vector) {
        return new Vector4f(vector.x, vector.y, vector.z, 1f);
    }

    public static Vector3f toVector3f(Vector4f vector) {
        return new Vector3f(vector.x, vector.y, vector.z);
    }

    public static Vector toVector(Vector3f vector) {
        return new Vector((double) vector.x, (double) vector.y, (double) vector.z);
    }

    public static Vector toVector(Vector3d vector) {
        return new Vector((float) vector.x, (float) vector.y, (float) vector.z);
    }

    public static Vector toVector(Vector4f vector) {
        return new Vector((double) vector.x, (double) vector.y, (double) vector.z);
    }

    public static Vector copy(Vector target, Vector3d vector) {
        target.setX(vector.x);
        target.setY(vector.y);
        target.setZ(vector.z);
        return target;
    }

    public static Vector copy(Vector target, Vector3f vector) {
        target.setX((double) vector.x);
        target.setY((double) vector.y);
        target.setZ((double) vector.z);
        return target;
    }

    public static float pitch(Vector vector) {
        double x = vector.getX();
        double y = vector.getY();
        double z = vector.getZ();
        return -(float) Math.atan2(y, Math.sqrt(x * x + z * z));
    }

    public static float yaw(Vector vector) {
        return -(float) Math.atan2(-vector.getX(), vector.getZ());
    }

    public static Vector rotate(Vector vector, Quaterniond quaternion) {
        return copy(vector, new Vector3d(vector.getX(), vector.getY(), vector.getZ()).rotate(quaternion));
    }

    public static Vector rotate(Vector vector, Quaternionf quaternion) {
        return copy(vector, new Vector3f((float) vector.getX(), (float) vector.getY(), (float) vector.getZ()).rotate(quaternion));
    }

    /** Yaw-only orientation: same heading as this quaternion, zero pitch/roll. */
    public static Quaternionf horizontal(Quaternionf quaternion) {
        Vector3f forward = new Vector3f(0f, 0f, 1f).rotate(quaternion);
        forward.y = 0f;
        if (forward.lengthSquared() < 1e-10f) {
            return new Quaternionf().rotationYXZ(quaternion.getEulerAnglesYXZ(new Vector3f()).y, 0f, 0f);
        }
        forward.normalize();
        return new Quaternionf().rotationTo(new Vector3f(0f, 0f, 1f), forward);
    }

    public static Vector lerp(Vector vector, Vector other, double t) {
        vector.setX(vector.getX() + (other.getX() - vector.getX()) * t);
        vector.setY(vector.getY() + (other.getY() - vector.getY()) * t);
        vector.setZ(vector.getZ() + (other.getZ() - vector.getZ()) * t);
        return vector;
    }

    public static Vector moveTowards(Vector vector, Vector target, double speed) {
        Vector diff = target.clone().subtract(vector);
        double distance = diff.length();
        if (distance <= speed) {
            vector.copy(target);
        } else {
            vector.add(diff.multiply(speed / distance));
        }
        return vector;
    }

    public static Vector3f moveTowards(Vector3f vector, Vector3f target, float speed) {
        Vector3f diff = new Vector3f(target).sub(vector);
        float distance = diff.length();
        if (distance <= speed) {
            vector.set(target);
        } else {
            vector.add(diff.mul(speed / distance));
        }
        return vector;
    }

    public static float yawRadians(Location location) {
        return -toRadians(location.getYaw());
    }

    public static float pitchRadians(Location location) {
        return toRadians(location.getPitch());
    }

    public static Quaternionf getQuaternion(Location location) {
        return new Quaternionf().rotationYXZ(yawRadians(location), pitchRadians(location), 0f);
    }

    public static Vector transform(Quaterniond quaternion, Vector vector) {
        copy(vector, quaternion.transform(vector.toVector3d()));
        return vector;
    }

    public static double lerp(double value, double other, double t) {
        return value * (1 - t) + other * t;
    }

    public static float lerp(float value, float other, float t) {
        return value * (1 - t) + other * t;
    }

    public static int lerpSafely(int value, int other, float t) {
        if (other == value) return value;
        int result = (int) lerp((float) value, (float) other, t);
        if (result == value && t != 0f) return moveTowards(value, other, 1);
        return result;
    }

    public static double moveTowards(double value, double target, double speed) {
        double distance = target - value;
        return Math.abs(distance) < speed ? target : value + speed * Math.signum(distance);
    }

    public static float moveTowards(float value, float target, float speed) {
        float distance = target - value;
        return Math.abs(distance) < speed ? target : value + speed * Math.signum(distance);
    }

    public static int moveTowards(int value, int target, int speed) {
        int distance = target - value;
        return Math.abs(distance) < speed ? target : value + speed * Integer.signum(distance);
    }

    public static double toRadians(double value) {
        return Math.toRadians(value);
    }

    public static float toRadians(float value) {
        return (float) Math.toRadians((double) value);
    }

    public static double toDegrees(double value) {
        return Math.toDegrees(value);
    }

    public static float toDegrees(float value) {
        return (float) Math.toDegrees((double) value);
    }


    public static double normalize(double value, double min, double max) {
        return (value - min) / (max - min);
    }

    public static double denormalize(double value, double min, double max) {
        return value * (max - min) + min;
    }

    public static float normalize(float value, float min, float max) {
        return (value - min) / (max - min);
    }

    public static float denormalize(float value, float min, float max) {
        return value * (max - min) + min;
    }

    public static Vector3f normal(Transformation transformation) {
        Quaternionf rotation = new Quaternionf(transformation.getLeftRotation()).mul(transformation.getRightRotation());
        Vector3f forward = new Vector3f(0f, 0f, 1f).rotate(rotation);
        return forward.normalize();
    }

    public static Matrix4f shearMatrix(
        float xy,
        float xz,
        float yx,
        float yz,
        float zx,
        float zy
    ) {
        return new Matrix4f(
            1f, xy, xz, 0f,
            yx, 1f, yz, 0f,
            zx, zy, 1f, 0f,
            0f, 0f, 0f, 1f
        );
    }

    public static Matrix4f shear(
        Matrix4f matrix,
        float xy,
        float xz,
        float yx,
        float yz,
        float zx,
        float zy
    ) {
        return matrix.mul(shearMatrix(
            xy,
            xz,
            yx,
            yz,
            zx,
            zy
        ));
    }

    public static Matrix4f shear(Matrix4f matrix, float xy, float xz, float yx, float yz, float zx) {
        return shear(matrix, xy, xz, yx, yz, zx, 0f);
    }

    public static Matrix4f shear(Matrix4f matrix, float xy, float xz, float yx, float yz) {
        return shear(matrix, xy, xz, yx, yz, 0f, 0f);
    }

    public static Matrix4f shear(Matrix4f matrix, float xy, float xz, float yx) {
        return shear(matrix, xy, xz, yx, 0f, 0f, 0f);
    }

    public static Matrix4f shear(Matrix4f matrix, float xy, float xz) {
        return shear(matrix, xy, xz, 0f, 0f, 0f, 0f);
    }

    public static Matrix4f shear(Matrix4f matrix, float xy) {
        return shear(matrix, xy, 0f, 0f, 0f, 0f, 0f);
    }

    public static Matrix4f shear(Matrix4f matrix) {
        return shear(matrix, 0f, 0f, 0f, 0f, 0f, 0f);
    }

    public static float eased(float value) {
        return value * value * (3 - 2 * value);
    }


    public static Vector average(List<Vector> vectors) {
        Vector out = new Vector(0.0, 0.0, 0.0);
        for (Vector vector : vectors) out.add(vector);
        return out.multiply(1.0 / vectors.size());
    }

    public static Vector3f averageVector3f(List<Vector3f> vectors) {
        Vector3f out = new Vector3f();
        for (Vector3f vector : vectors) out.add(vector);
        return out.mul(1f / vectors.size());
    }
}
