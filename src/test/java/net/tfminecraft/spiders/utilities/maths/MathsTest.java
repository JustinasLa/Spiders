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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MathsTest {
    private static final double EPS = 1e-6;

    private static void assertVector(double x, double y, double z, Vector actual) {
        assertEquals(x, actual.getX(), EPS, "x of " + actual);
        assertEquals(y, actual.getY(), EPS, "y of " + actual);
        assertEquals(z, actual.getZ(), EPS, "z of " + actual);
    }

    private static void assertVector(double x, double y, double z, Vector3f actual) {
        assertVector(x, y, z, new Vector(actual.x, actual.y, actual.z));
    }

    @Test
    @DisplayName("the basis vectors follow Minecraft's axes (+Z forward, +Y up, +X right)")
    void basisVectors() {
        assertVector(0, -1, 0, Maths.downVector());
        assertVector(0, 1, 0, Maths.upVector());
        assertVector(0, 0, 1, Maths.forwardVector());
        assertVector(1, 0, 0, Maths.rightVector());
        // fresh instances, so callers may mutate them
        Maths.upVector().multiply(5);
        assertVector(0, 1, 0, Maths.upVector());
    }

    @Test
    @DisplayName("vector conversions keep x, y and z and drop w")
    void conversions() {
        assertVector(1, 2, 3, Maths.toVector3f(new Vector4f(1, 2, 3, 4)));
        assertVector(1.5, -2, 3, Maths.toVector(new Vector3f(1.5f, -2f, 3f)));
        assertVector(1.25, -2, 3, Maths.toVector(new Vector3d(1.25, -2, 3)));
        assertVector(1, 2, 3, Maths.toVector(new Vector4f(1, 2, 3, 9)));

        Vector target = new Vector();
        assertSame(target, Maths.copy(target, new Vector3d(4, 5, 6)));
        assertVector(4, 5, 6, target);
        assertSame(target, Maths.copy(target, new Vector3f(7, 8, 9)));
        assertVector(7, 8, 9, target);
    }

    @Test
    @DisplayName("Vector3d conversion goes through float precision")
    void vector3dConversionUsesFloats() {
        Vector converted = Maths.toVector(new Vector3d(0.1, 0, 0));
        assertEquals((double) 0.1f, converted.getX(), 0.0);
    }

    @Test
    @DisplayName("pitch and yaw of a direction match the radians of a Location looking that way")
    void pitchAndYawMatchLocation() {
        List<Vector> directions = List.of(
            new Vector(0, 0, 1), new Vector(1, 0, 0), new Vector(-1, 0, 0),
            new Vector(0.3, 0.8, -0.5), new Vector(-0.2, -0.9, 0.4)
        );
        for (Vector direction : directions) {
            Location location = new Location(null, 0, 0, 0).setDirection(direction);
            // Location keeps yaw in [0, 360), so compare the angles modulo a full turn
            double yawDifference = Math.IEEEremainder(Maths.yawRadians(location) - Maths.yaw(direction), 2 * Math.PI);
            assertEquals(0.0, yawDifference, 1e-4, "yaw of " + direction);
            assertEquals(Maths.pitchRadians(location), Maths.pitch(direction), 1e-4, "pitch of " + direction);
        }
        assertEquals(-Math.PI / 2, Maths.pitch(new Vector(0, 1, 0)), 1e-6);
        assertEquals(-Math.PI / 2, Maths.yaw(new Vector(-1, 0, 0)), 1e-6);
    }

    @Test
    @DisplayName("rotate and transform turn the vector in place by the quaternion")
    void rotateAndTransform() {
        Vector forward = Maths.forwardVector();
        assertSame(forward, Maths.rotate(forward, new Quaternionf().rotateY((float) (Math.PI / 2))));
        assertVector(1, 0, 0, forward);

        Vector up = Maths.upVector();
        assertSame(up, Maths.rotate(up, new Quaterniond().rotateX(Math.PI / 2)));
        assertVector(0, 0, 1, up);

        Vector right = Maths.rightVector();
        assertSame(right, Maths.transform(new Quaterniond().rotateZ(Math.PI / 2), right));
        assertVector(0, 1, 0, right);
    }

    @Test
    @DisplayName("horizontal keeps the heading and drops pitch and roll")
    void horizontalKeepsHeading() {
        Quaternionf tilted = new Quaternionf().rotationYXZ(0.7f, 0.4f, 0.2f);
        Quaternionf flat = Maths.horizontal(tilted);

        Vector3f flatForward = new Vector3f(0, 0, 1).rotate(flat);
        Vector3f tiltedForward = new Vector3f(0, 0, 1).rotate(tilted);
        tiltedForward.y = 0;
        tiltedForward.normalize();
        assertVector(tiltedForward.x, tiltedForward.y, tiltedForward.z, flatForward);
        // yaw-only: up stays up
        assertVector(0, 1, 0, new Vector3f(0, 1, 0).rotate(flat));
    }

    @Test
    @DisplayName("horizontal of a straight-up look falls back to the yaw angle")
    void horizontalStraightUp() {
        Quaternionf lookingUp = new Quaternionf().rotationYXZ(0.5f, (float) (-Math.PI / 2), 0f);
        assertTrue(Math.abs(new Vector3f(0, 0, 1).rotate(lookingUp).y) > 0.999f);

        Quaternionf flat = Maths.horizontal(lookingUp);
        assertVector(0, 1, 0, new Vector3f(0, 1, 0).rotate(flat));
        float yaw = lookingUp.getEulerAnglesYXZ(new Vector3f()).y;
        Vector3f expected = new Vector3f(0, 0, 1).rotate(new Quaternionf().rotationY(yaw));
        assertVector(expected.x, expected.y, expected.z, new Vector3f(0, 0, 1).rotate(flat));
    }

    @Test
    @DisplayName("lerp blends scalars and vectors; vector lerp mutates its first argument")
    void lerp() {
        Vector a = new Vector(0, 10, -4);
        assertSame(a, Maths.lerp(a, new Vector(10, 0, 4), 0.25));
        assertVector(2.5, 7.5, -2, a);

        assertEquals(7.5, Maths.lerp(5.0, 15.0, 0.25), 0.0);
        assertEquals(7.5f, Maths.lerp(5f, 15f, 0.25f), 0f);
    }

    @Test
    @DisplayName("lerpSafely always makes progress unless t is zero or the ends match")
    void lerpSafely() {
        assertEquals(4, Maths.lerpSafely(4, 4, 0.5f));
        assertEquals(5, Maths.lerpSafely(0, 10, 0.5f));
        // 0.01 of the way would truncate to no change, so it steps by one
        assertEquals(1, Maths.lerpSafely(0, 10, 0.01f));
        assertEquals(9, Maths.lerpSafely(10, 0, 0.01f));
        assertEquals(0, Maths.lerpSafely(0, 10, 0f));
    }

    @Test
    @DisplayName("moveTowards steps by the speed and snaps when within reach")
    void moveTowardsScalars() {
        assertEquals(3.0, Maths.moveTowards(1.0, 10.0, 2.0), 0.0);
        assertEquals(-1.0, Maths.moveTowards(1.0, -10.0, 2.0), 0.0);
        assertEquals(10.0, Maths.moveTowards(9.5, 10.0, 2.0), 0.0);

        assertEquals(3f, Maths.moveTowards(1f, 10f, 2f), 0f);
        assertEquals(10f, Maths.moveTowards(9.5f, 10f, 2f), 0f);

        assertEquals(3, Maths.moveTowards(1, 10, 2));
        assertEquals(-1, Maths.moveTowards(1, -10, 2));
        assertEquals(10, Maths.moveTowards(9, 10, 2));
    }

    @Test
    @DisplayName("moveTowards on vectors moves along the line and snaps within reach")
    void moveTowardsVectors() {
        Vector vector = new Vector(0, 0, 0);
        assertSame(vector, Maths.moveTowards(vector, new Vector(3, 4, 0), 1.0));
        assertVector(0.6, 0.8, 0, vector);
        Maths.moveTowards(vector, new Vector(3, 4, 0), 100.0);
        assertVector(3, 4, 0, vector);

        Vector3f vector3f = new Vector3f();
        assertSame(vector3f, Maths.moveTowards(vector3f, new Vector3f(0, 0, 10), 2f));
        assertVector(0, 0, 2, vector3f);
        Maths.moveTowards(vector3f, new Vector3f(0, 0, 10), 8f);
        assertVector(0, 0, 10, vector3f);
    }

    @Test
    @DisplayName("angle conversions round-trip in both precisions")
    void angleConversions() {
        assertEquals(Math.PI, Maths.toRadians(180.0), 1e-12);
        assertEquals((float) Math.PI, Maths.toRadians(180f), 1e-6f);
        assertEquals(90.0, Maths.toDegrees(Math.PI / 2), 1e-12);
        assertEquals(90f, Maths.toDegrees((float) (Math.PI / 2)), 1e-4f);

        Location location = new Location(null, 0, 0, 0, 90f, -30f);
        assertEquals((float) -Math.PI / 2, Maths.yawRadians(location), 1e-6f);
        assertEquals((float) Math.toRadians(-30), Maths.pitchRadians(location), 1e-6f);
    }

    @Test
    @DisplayName("normalize maps a range onto [0, 1]")
    void normalize() {
        assertEquals(0.25, Maths.normalize(15.0, 10.0, 30.0), 0.0);
        assertEquals(1.5f, Maths.normalize(40f, 10f, 30f), 0f);
    }

    @Test
    @DisplayName("normal is the forward axis after both display rotations")
    void normalOfTransformation() {
        Transformation transformation = new Transformation(
            new Vector3f(5, 5, 5),
            new Quaternionf().rotateY((float) (Math.PI / 2)),
            new Vector3f(3, 3, 3),
            new Quaternionf().rotateX((float) (-Math.PI / 2))
        );
        // right rotation turns forward to up, the left yaw leaves up alone
        assertVector(0, 1, 0, Maths.normal(transformation));

        Transformation yawOnly = new Transformation(new Vector3f(), new Quaternionf().rotateY((float) (Math.PI / 2)), new Vector3f(2, 2, 2), new Quaternionf());
        assertVector(1, 0, 0, Maths.normal(yawOnly));
    }

    @Test
    @DisplayName("shear adds each coefficient to the named axis")
    void shear() {
        Matrix4f sheared = Maths.shear(new Matrix4f(), 1, 2, 3, 4, 5, 6);
        assertVector(1, 1, 2, sheared.transformPosition(new Vector3f(1, 0, 0)));
        assertVector(3, 1, 4, sheared.transformPosition(new Vector3f(0, 1, 0)));
        assertVector(5, 6, 1, sheared.transformPosition(new Vector3f(0, 0, 1)));

        assertEquals(Maths.shearMatrix(1, 2, 3, 4, 5, 0), Maths.shear(new Matrix4f(), 1, 2, 3, 4, 5));
        assertEquals(Maths.shearMatrix(1, 2, 3, 4, 0, 0), Maths.shear(new Matrix4f(), 1, 2, 3, 4));
        assertEquals(Maths.shearMatrix(1, 2, 3, 0, 0, 0), Maths.shear(new Matrix4f(), 1, 2, 3));
        assertEquals(Maths.shearMatrix(1, 2, 0, 0, 0, 0), Maths.shear(new Matrix4f(), 1, 2));
        assertEquals(Maths.shearMatrix(1, 0, 0, 0, 0, 0), Maths.shear(new Matrix4f(), 1));
        assertEquals(new Matrix4f(), Maths.shear(new Matrix4f()));

        Matrix4f translated = new Matrix4f().translation(10, 0, 0);
        assertSame(translated, Maths.shear(translated, 1));
        assertVector(11, 1, 0, translated.transformPosition(new Vector3f(1, 0, 0)));
    }

    @Test
    @DisplayName("eased is the smoothstep curve")
    void eased() {
        assertEquals(0f, Maths.eased(0f), 0f);
        assertEquals(1f, Maths.eased(1f), 0f);
        assertEquals(0.5f, Maths.eased(0.5f), 0f);
        assertEquals(0.15625f, Maths.eased(0.25f), 1e-7f);
    }

    @Test
    @DisplayName("average is the centroid and leaves the inputs alone")
    void average() {
        Vector first = new Vector(1, 2, 3);
        Vector second = new Vector(3, 4, 5);
        assertVector(2, 3, 4, Maths.average(List.of(first, second)));
        assertVector(1, 2, 3, first);
    }

    @Test
    @DisplayName("clamp limits to the range, passes NaN through and refuses an empty range")
    void clamp() {
        assertEquals(1.0, Maths.clamp(-5.0, 1.0, 2.0), 0.0);
        assertEquals(2.0, Maths.clamp(5.0, 1.0, 2.0), 0.0);
        assertEquals(1.5, Maths.clamp(1.5, 1.0, 2.0), 0.0);
        assertTrue(Double.isNaN(Maths.clamp(Double.NaN, 1.0, 2.0)));
        assertEquals(Double.doubleToRawLongBits(-0.0), Double.doubleToRawLongBits(Maths.clamp(-0.0, -1.0, 1.0)));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> Maths.clamp(0.0, 2.0, 1.0));
        assertTrue(error.getMessage().contains("Empty range"));

        assertEquals(1f, Maths.clamp(-5f, 1f, 2f), 0f);
        assertEquals(2f, Maths.clamp(5f, 1f, 2f), 0f);
        assertEquals(1.5f, Maths.clamp(1.5f, 1f, 2f), 0f);
        assertTrue(Float.isNaN(Maths.clamp(Float.NaN, 1f, 2f)));
        assertThrows(IllegalArgumentException.class, () -> Maths.clamp(0f, 2f, 1f));
    }

    @Test
    @DisplayName("roundToInt rounds half up, saturates and refuses NaN")
    void roundToInt() {
        assertEquals(3, Maths.roundToInt(2.5));
        assertEquals(-2, Maths.roundToInt(-2.5));
        assertEquals(Integer.MAX_VALUE, Maths.roundToInt(1e12));
        assertEquals(Integer.MIN_VALUE, Maths.roundToInt(-1e12));
        assertEquals(Integer.MAX_VALUE, Maths.roundToInt(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Maths.roundToInt(Double.NaN));
    }
}
