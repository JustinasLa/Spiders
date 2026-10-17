package net.tfminecraft.spiders.utilities;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector2d;
import org.joml.Vector3f;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GeometryTest {
    private static void assertVector(double x, double y, double z, Vector actual) {
        assertEquals(x, actual.getX(), 1e-9, "x of " + actual);
        assertEquals(y, actual.getY(), 1e-9, "y of " + actual);
        assertEquals(z, actual.getZ(), 1e-9, "z of " + actual);
    }

    @Test
    @DisplayName("rotateAroundY turns a vector about another point, in place")
    void rotateAroundYUsesTheOrigin() {
        Vector vector = new Vector(2, 5, 0);
        Geometry.rotateAroundY(vector, Math.PI / 2, new Vector(1, 0, 0));
        assertVector(1, 5, -1, vector);
    }

    @Test
    @DisplayName("getYXZRelative gives the Euler angles of a rotation relative to a pivot")
    void yxzRelativeToAPivot() {
        Quaternionf pivot = new Quaternionf().rotateY(0.25f);
        Quaternionf rotation = new Quaternionf().rotateY(0.75f);
        Vector3f angles = Geometry.getYXZRelative(rotation, pivot);
        assertEquals(0.0, angles.x, 1e-6);
        assertEquals(0.5, angles.y, 1e-6);
        assertEquals(0.0, angles.z, 1e-6);
    }

    @Test
    @DisplayName("horizontal measures ignore height")
    void horizontalMeasuresIgnoreHeight() {
        assertEquals(5.0, Geometry.horizontalDistance(new Vector(0, 5, 0), new Vector(3, -2, 4)), 1e-9);
        assertEquals(5.0, Geometry.horizontalLength(new Vector(3, 9, 4)), 1e-9);
    }

    @Test
    @DisplayName("scaleTangential scales only the part across the axis")
    void scaleTangentialKeepsTheAxialPart() {
        Vector vector = new Vector(2, 3, 0);
        assertSame(vector, Geometry.scaleTangential(vector, new Vector(0, 2, 0), 0.5));
        assertVector(1, 3, 0, vector);
    }

    @Test
    @DisplayName("average is the centroid")
    void averageIsTheCentroid() {
        assertVector(1, 2, 3, Geometry.average(List.of(new Vector(0, 0, 0), new Vector(2, 4, 6))));
    }

    @Test
    @DisplayName("raycastGround traces blocks from the position, ignoring fluids and passable blocks")
    void raycastGroundTracesBlocks() {
        World world = mock(World.class);
        RayTraceResult hit = new RayTraceResult(new Vector(0, 60, 0));
        Vector down = new Vector(0, -1, 0);
        when(world.rayTraceBlocks(new Location(world, 0, 64, 0), down, 10.0, FluidCollisionMode.NEVER, true)).thenReturn(hit);
        assertSame(hit, Geometry.raycastGround(world, new Vector(0, 64, 0), down, 10.0));
    }

    @Test
    @DisplayName("resolveCollision finds the face a movement ends inside, and how far back it is")
    void resolveCollisionFindsTheHitFace() {
        World world = mock(World.class);
        Vector direction = new Vector(0, -2, 0);
        // the ray starts one step back, at y 7, and runs the step's length
        when(world.rayTraceBlocks(eq(new Location(world, 0, 7, 0)), eq(direction), eq(2.0), eq(FluidCollisionMode.NEVER), eq(true)))
            .thenReturn(new RayTraceResult(new Vector(0, 6, 0), mock(Block.class), BlockFace.UP));

        CollisionResult result = Geometry.resolveCollision(world, new Vector(0, 5, 0), direction);
        assertEquals(new CollisionResult(new Vector(0, 6, 0), new Vector(0, 1, 0), new Vector(0, 1, 0)), result);
    }

    @Test
    @DisplayName("resolveCollision is null without movement, without a hit, or without a hit face")
    void resolveCollisionWithoutAHit() {
        World still = mock(World.class);
        assertNull(Geometry.resolveCollision(still, new Vector(0, 5, 0), new Vector(0, 0, 0)));
        verifyNoInteractions(still);

        World air = mock(World.class);
        assertNull(Geometry.resolveCollision(air, new Vector(0, 5, 0), new Vector(1, 0, 0)));

        World faceless = mock(World.class);
        when(faceless.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(), any(FluidCollisionMode.class), eq(true)))
            .thenReturn(new RayTraceResult(new Vector(0, 5, 0)));
        assertNull(Geometry.resolveCollision(faceless, new Vector(0, 5, 0), new Vector(1, 0, 0)));
    }

    @Test
    @DisplayName("lookingAtPoint allows a tolerance around the line of sight")
    void lookingAtPointHasATolerance() {
        Vector eye = new Vector(0, 0, 0);
        Vector east = new Vector(1, 0, 0);
        assertTrue(Geometry.lookingAtPoint(eye, east, new Vector(5, 0.1, 0), 0.5));
        assertFalse(Geometry.lookingAtPoint(eye, east, new Vector(0, 5, 0), 0.5));
    }

    @Test
    @DisplayName("centredTransform is a box of the given size centred on the origin")
    void centredTransformIsCentred() {
        Transformation transform = Geometry.centredTransform(2f, 4f, 6f);
        assertEquals(new Vector3f(-1f, -2f, -3f), transform.getTranslation());
        assertEquals(new Vector3f(2f, 4f, 6f), transform.getScale());
        assertEquals(new Quaternionf(), transform.getLeftRotation());
        assertEquals(new Quaternionf(), transform.getRightRotation());
    }

    private static final List<Vector2d> SQUARE = List.of(new Vector2d(0, 0), new Vector2d(4, 0), new Vector2d(4, 4), new Vector2d(0, 4));

    @Test
    @DisplayName("pointInPolygon counts edge crossings")
    void pointInPolygonCountsCrossings() {
        assertTrue(Polygons.pointInPolygon(new Vector2d(2, 2), SQUARE));
        assertFalse(Polygons.pointInPolygon(new Vector2d(5, 2), SQUARE), "right of it");
        assertFalse(Polygons.pointInPolygon(new Vector2d(-1, 2), SQUARE), "left of it");
        assertFalse(Polygons.pointInPolygon(new Vector2d(2, 5), SQUARE), "above it");
    }

    @Test
    @DisplayName("nearestPointInPolygon is the closest point on its outline")
    void nearestPointInPolygonIsOnTheOutline() {
        assertEquals(new Vector2d(4, 2), Polygons.nearestPointInPolygon(new Vector2d(6, 2), SQUARE));
        assertEquals(new Vector2d(2, 4), Polygons.nearestPointInPolygon(new Vector2d(2, 9), SQUARE));
        assertEquals(new Vector2d(0, 0), Polygons.nearestPointInPolygon(new Vector2d(-1, -1), SQUARE));
    }

    @Test
    @DisplayName("nearestPointOnClampedLine stays within the segment")
    void nearestPointOnClampedLineIsClamped() {
        Vector2d a = new Vector2d(0, 0);
        Vector2d b = new Vector2d(4, 0);
        assertEquals(new Vector2d(2, 0), Polygons.nearestPointOnClampedLine(new Vector2d(2, 3), a, b));
        assertEquals(a, Polygons.nearestPointOnClampedLine(new Vector2d(-3, 1), a, b));
        assertEquals(b, Polygons.nearestPointOnClampedLine(new Vector2d(9, 1), a, b));
    }

    @Test
    @DisplayName("a line segment measures distance to its nearest point, clamped to its ends")
    void lineSegmentDistance() {
        LineSegment segment = LineSegment.fromOffset(new Vector(0, 0, 0), new Vector(4, 0, 0));
        assertVector(0, 0, 0, segment.getPoint1());
        assertVector(4, 0, 0, segment.getPoint2());
        assertVector(4, 0, 0, segment.vector());
        assertEquals(3.0, segment.distance(new Vector(2, 3, 0)), 1e-9);
        assertEquals(5.0, segment.distance(new Vector(7, 4, 0)), 1e-9, "past the end");
        assertEquals(25.0, segment.distanceSquared(new Vector(-3, 0, 4)), 1e-9, "before the start");

        LineSegment point = new LineSegment(new Vector(1, 1, 1), new Vector(1, 1, 1));
        assertEquals(2.0, point.distance(new Vector(1, 3, 1)), 1e-9, "a zero-length segment is a point");
    }

    @Test
    @DisplayName("a capsule contains every point within its radius of its axis")
    void capsuleContainsPointsNearItsAxis() {
        Capsule capsule = new Capsule(new Vector(0, 0, 0), new Vector(0, 4, 0), 1.0);
        assertTrue(capsule.contains(new Vector(1, 2, 0)), "on the surface");
        assertTrue(capsule.contains(new Vector(0, 4.5, 0)), "in an end cap");
        assertFalse(capsule.contains(new Vector(1.1, 2, 0)));
        assertFalse(capsule.contains(new Vector(0, 5.5, 0)));
        assertVector(0, 4, 0, capsule.axis());
        assertEquals(1.0, capsule.getRadius());
        assertSame(capsule.getPoint1(), capsule.line().getPoint1());
        assertSame(capsule.getPoint2(), capsule.line().getPoint2());
    }

    @Test
    @DisplayName("collision results are equal when position, offset and normal are")
    void collisionResultEquality() {
        CollisionResult result = new CollisionResult(new Vector(1, 2, 3), new Vector(0, 1, 0), new Vector(0, 1, 0));
        CollisionResult same = new CollisionResult(new Vector(1, 2, 3), new Vector(0, 1, 0), new Vector(0, 1, 0));
        assertEquals(result, result);
        assertEquals(result, same);
        assertEquals(result.hashCode(), same.hashCode());
        assertNotEquals(result, new CollisionResult(new Vector(9, 2, 3), new Vector(0, 1, 0), new Vector(0, 1, 0)));
        assertNotEquals(result, new CollisionResult(new Vector(1, 2, 3), new Vector(9, 1, 0), new Vector(0, 1, 0)));
        assertNotEquals(result, new CollisionResult(new Vector(1, 2, 3), new Vector(0, 1, 0), new Vector(9, 1, 0)));
        assertNotEquals(result, "a string");
        assertVector(1, 2, 3, result.getPosition());
        assertVector(0, 1, 0, result.getOffset());
        assertVector(0, 1, 0, result.getNormal());
        assertEquals("CollisionResult(position=1.0,2.0,3.0, offset=0.0,1.0,0.0, normal=0.0,1.0,0.0)", result.toString());
    }
}
