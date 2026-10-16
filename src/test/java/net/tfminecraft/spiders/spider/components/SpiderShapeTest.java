package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.utilities.Pair;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpiderShapeTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    private static final ShapePiece CUBE = new ShapePiece(new Vector(), new Matrix4f());

    private static Pair<Double, Vector> hit(Vector from, Vector to, ShapePiece... pieces) {
        return SpiderShape.firstHit(List.of(pieces), from, to);
    }

    @Test
    @DisplayName("a segment is caught where it enters a block, from either side")
    void aSegmentIsCaughtWhereItEntersABlock() {
        Pair<Double, Vector> forward = hit(new Vector(-1.0, .5, .5), new Vector(2.0, .5, .5), CUBE);
        assertNotNull(forward);
        assertEquals(1.0 / 3, forward.first(), 1e-6);
        assertTrue(forward.second().distance(new Vector(0.0, .5, .5)) < 1e-6);

        Pair<Double, Vector> back = hit(new Vector(2.0, .5, .5), new Vector(-1.0, .5, .5), CUBE);
        assertNotNull(back);
        assertTrue(back.second().distance(new Vector(1.0, .5, .5)) < 1e-6);

        Pair<Double, Vector> inside = hit(new Vector(.5, .5, .5), new Vector(3.0, 3.0, 3.0), CUBE);
        assertNotNull(inside);
        assertEquals(0.0, inside.first());
        assertEquals(CUBE.getOrigin(), new Vector());
    }

    @Test
    @DisplayName("segments beside, past, short of or along the outside of a block miss it")
    void segmentsThatPassABlockMissIt() {
        assertNull(hit(new Vector(-1.0, 2.0, .5), new Vector(2.0, 2.0, .5), CUBE), "parallel above");
        assertNull(hit(new Vector(-1.0, 3.5, .5), new Vector(2.0, .5, .5), CUBE), "diagonal past the corner");
        assertNull(hit(new Vector(-3.0, .5, .5), new Vector(-1.0, .5, .5), CUBE), "stops short");
        assertNull(hit(new Vector(-1.0, .5, .5), new Vector(2.0, .5, .5)), "no pieces");
    }

    @Test
    @DisplayName("the nearest of several pieces is hit, and flat pieces are ignored")
    void theNearestOfSeveralPiecesIsHit() {
        ShapePiece far = new ShapePiece(new Vector(4.0, 0.0, 0.0), new Matrix4f());
        ShapePiece flat = new ShapePiece(new Vector(-.5, 0.0, 0.0), new Matrix4f().zero());
        ShapePiece stretched = new ShapePiece(new Vector(2.0, 0.0, 0.0), new Matrix4f().scale(.5f, 1f, 1f));
        Pair<Double, Vector> first = hit(new Vector(-1.0, .5, .5), new Vector(6.0, .5, .5), far, flat, stretched);
        assertNotNull(first);
        assertTrue(first.second().distance(new Vector(2.0, .5, .5)) < 1e-6, "Hit " + first.second());
        assertTrue(first.first() > 0.0);
        assertEquals(first.first(), hit(new Vector(-1.0, .5, .5), new Vector(6.0, .5, .5), stretched, far).first(), 1e-9);
        assertNull(hit(new Vector(-1.0, .5, .5), new Vector(6.0, .5, .5), flat));
    }

    @Test
    @DisplayName("a legless body reaches only as far as its body")
    void aLeglessBodyReachesOnlyAsFarAsItsBody() {
        BodyPlan plan = new BodyPlan();
        plan.setScale(2.0);
        assertEquals(4.4, SpiderShape.reach(plan), 1e-9);
        plan.setLegs(List.of(new LegPlan(new Vector(1.0, 0.0, 0.0), new Vector(3.0, 0.0, 0.0), List.of(), new Quaternionf())));
        assertEquals(4.4, SpiderShape.reach(plan), 1e-9, "A short leg doesn't shrink the reach");
    }

    @Test
    @DisplayName("legs and segments the plan doesn't describe are not drawn")
    void legsAndSegmentsThePlanDoesntDescribeAreNotDrawn() {
        World world = new Room((x, y, z) -> y < 0 && y >= -2).world();
        Run run = spawn(world, .8, false, new Vector(.5, 0.0, .5), false);
        run.app().update();
        BodyPlan full = run.options().getBodyPlan();
        int body = full.getBodyModel().getPieces().size();
        LegPlan firstLeg = full.getLegs().get(0);
        int firstSegment = firstLeg.getSegments().get(0).getModel().getPieces().size();

        BodyPlan partial = new BodyPlan();
        partial.setBodyModel(full.getBodyModel());
        partial.setLegs(List.of(new LegPlan(firstLeg.getAttachmentPosition(), firstLeg.getRestPosition(), List.of(firstLeg.getSegments().get(0)), firstLeg.getRestOrientation())));
        assertTrue(run.body().getLegs().size() > 1 && firstLeg.getSegments().size() > 1, "Setup: the spider should have jointed legs");
        assertEquals(body + firstSegment, SpiderShape.shapePieces(run.body(), partial).size());
        assertTrue(SpiderShape.shapePieces(run.body(), full).size() > body + firstSegment);
        assertEquals(body, SpiderShape.shapePieces(SpiderBody.create(world, new Vector()), full).size());
    }

    @Test
    @DisplayName("a shape piece keeps its origin and transform")
    void aShapePieceKeepsItsOriginAndTransform() {
        Matrix4f transform = new Matrix4f().scale(2f);
        Vector origin = new Vector(1.0, 2.0, 3.0);
        ShapePiece piece = new ShapePiece(origin, transform);
        assertEquals(origin, piece.getOrigin());
        assertEquals(transform, piece.getTransform());
    }
}
