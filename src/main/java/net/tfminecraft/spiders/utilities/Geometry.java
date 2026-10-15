package net.tfminecraft.spiders.utilities;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class Geometry {
    private Geometry() {
    }

    public static void rotateAroundY(Vector vector, double angle, Vector origin) {
        vector.subtract(origin).rotateAroundY(angle).add(origin);
    }

    public static Vector3f getYXZRelative(Quaternionf quaternion, Quaternionf pivot) {
        Quaternionf relative = new Quaternionf(pivot).difference(quaternion);
        return relative.getEulerAnglesYXZ(new Vector3f());
    }

    public static Vector3f getRotationAroundAxis(Vector vector, Quaternionf pivot) {
        Quaternionf orientation = new Quaternionf().rotationTo(Maths.forwardVector().toVector3f(), vector.toVector3f());
        return getYXZRelative(orientation, pivot);
    }

    public static double verticalDistance(Vector vector, Vector other) {
        return Math.abs(vector.getY() - other.getY());
    }

    public static double horizontalDistance(Vector vector, Vector other) {
        double x = vector.getX() - other.getX();
        double z = vector.getZ() - other.getZ();
        return Math.sqrt(x * x + z * z);
    }

    public static double horizontalLength(Vector vector) {
        double x = vector.getX();
        double z = vector.getZ();
        return Math.sqrt(x * x + z * z);
    }

    public static Vector scaleTangential(Vector vector, Vector axis, double factor) {
        Vector unit = axis.clone().normalize();
        double along = vector.dot(unit);
        vector.subtract(unit.clone().multiply(along));
        vector.multiply(factor);
        vector.add(unit.clone().multiply(along));
        return vector;
    }

    public static Vector average(List<Vector> vectors) {
        return Maths.average(vectors);
    }

    public static RayTraceResult raycastGround(World world, Vector position, Vector direction, double maxDistance) {
        Location location = position.toLocation(world);
        return world.rayTraceBlocks(location, direction, maxDistance, FluidCollisionMode.NEVER, true);
    }

    public static CollisionResult resolveCollision(World world, Vector position, Vector direction) {
        if (direction.lengthSquared() < 1e-12) return null;

        Location location = position.toLocation(world);
        RayTraceResult ray = world.rayTraceBlocks(location.subtract(direction), direction, direction.length(), FluidCollisionMode.NEVER, true);
        if (ray == null) return null;

        BlockFace hitFace = ray.getHitBlockFace();
        if (hitFace == null) return null;
        return new CollisionResult(ray.getHitPosition(), ray.getHitPosition().clone().subtract(position), hitFace.getDirection());
    }

    public static boolean lookingAtPoint(Vector eye, Vector direction, Vector point, double tolerance) {
        double pointDistance = eye.distance(point);
        Vector lookingAtPoint = eye.clone().add(direction.clone().multiply(pointDistance));
        return lookingAtPoint.distance(point) < tolerance;
    }

    public static Transformation centredTransform(float xSize, float ySize, float zSize) {
        return new Transformation(
            new Vector3f(-xSize / 2, -ySize / 2, -zSize / 2),
            new AxisAngle4f(0f, 0f, 0f, 1f),
            new Vector3f(xSize, ySize, zSize),
            new AxisAngle4f(0f, 0f, 0f, 1f)
        );
    }

    public static Matrix4f matrixFromTransform(Transformation transformation) {
        Matrix4f matrix = new Matrix4f();
        matrix.translate(transformation.getTranslation());
        matrix.rotate(transformation.getLeftRotation());
        matrix.scale(transformation.getScale());
        matrix.rotate(transformation.getRightRotation());
        return matrix;
    }
}
