package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderLegIK;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RetainedRenderTest {
    private final World world = mock(World.class);
    private final Cloak cloak = mock(Cloak.class);

    private SpiderBody spider;
    private SpiderOptions options;
    private BodyPlan plan;
    private DisplayModel bodyModel;
    private List<SegmentPlan> segments;
    /** What the leg's IK reports; empty until the first solve. */
    private List<IKChainWorldNode3D> nodes = List.of();

    private static BlockDisplayModelPiece piece() {
        return new BlockDisplayModelPiece(mock(BlockData.class), new Matrix4f());
    }

    private static List<BlockDisplayModelPiece> pieces(int count) {
        List<BlockDisplayModelPiece> pieces = new ArrayList<>();
        for (int i = 0; i < count; i++) pieces.add(piece());
        return pieces;
    }

    private BodyPlan plan(DisplayModel body, List<SegmentPlan> legSegments) {
        LegPlan legPlan = mock(LegPlan.class);
        when(legPlan.getSegments()).thenReturn(legSegments);
        BodyPlan bodyPlan = mock(BodyPlan.class);
        when(bodyPlan.getBodyModel()).thenReturn(body);
        when(bodyPlan.getLegs()).thenReturn(List.of(legPlan));
        return bodyPlan;
    }

    @BeforeEach
    void spawn() {
        bodyModel = new DisplayModel(pieces(2));
        segments = List.of(
            new SegmentPlan(1.0, null, new DisplayModel(pieces(1))),
            new SegmentPlan(1.0, null, new DisplayModel(pieces(2)))
        );
        plan = plan(bodyModel, segments);
        options = mock(SpiderOptions.class);
        when(options.getBodyPlan()).thenAnswer(call -> plan);

        spider = SpiderBody.create(world, new Vector(1.0, 64.0, 2.0));
        SpiderLegIK ik = mock(SpiderLegIK.class);
        when(ik.getNodes()).thenAnswer(call -> nodes);
        Leg leg = mock(Leg.class);
        when(leg.getIk()).thenReturn(ik);
        spider.setLegs(List.of(leg));
    }

    /** A solved three-node leg: two segments, each turned a different way. */
    private void solveLeg() {
        nodes = List.of(
            new IKChainWorldNode3D(new Vector(2.0, 64.0, 2.0), new Quaternionf(), new Quaternionf()),
            new IKChainWorldNode3D(new Vector(3.0, 63.0, 2.0), new Quaternionf().rotateZ(1f), new Quaternionf()),
            new IKChainWorldNode3D(new Vector(4.0, 62.0, 2.0), new Quaternionf().rotateY(.5f), new Quaternionf())
        );
    }

    private RenderSpiderEntities.Retained tick() {
        return RenderSpiderEntities.prepare(spider, cloak, options);
    }

    @Test
    @DisplayName("an unchanged spider keeps its tree and render entities between ticks, only moving them")
    void reusedAcrossTicks() {
        solveLeg();
        RenderSpiderEntities.Retained first = tick();
        List<RenderItem> entities = new ArrayList<>();
        for (RenderSpiderEntities.Entry entry : first.entries) entities.add(entry.entity);

        spider.getPosition().setY(70.0);
        solveLeg(); // a new solve hands out new nodes, same count
        bodyModel.getPieces().getFirst().setBlock(mock(BlockData.class)); // blinking edits pieces in place
        RenderSpiderEntities.Retained second = tick();

        assertSame(first, second);
        for (int i = 0; i < entities.size(); i++) assertSame(entities.get(i), second.entries.get(i).entity);
        assertEquals(70.0, second.parts.getFirst().location.getY());
        assertSame(world, second.parts.getFirst().location.getWorld());
    }

    @Test
    @DisplayName("handles are what RenderGroup.submit(spider) produced, so tracked displays and splay still match")
    void handleShapes() {
        solveLeg();
        List<Object> handles = new ArrayList<>();
        for (RenderSpiderEntities.Entry entry : tick().entries) handles.add(entry.handle);

        Pair<SpiderBody, SpiderBody> body = new Pair<>(spider, spider);
        Pair<SpiderBody, Pair<Integer, Integer>> segment0 = new Pair<>(spider, new Pair<>(0, 0));
        Pair<SpiderBody, Pair<Integer, Integer>> segment1 = new Pair<>(spider, new Pair<>(0, 1));
        assertEquals(List.of(
            new Pair<>(body, 0),
            new Pair<>(body, 1),
            new Pair<>(segment0, 0),
            new Pair<>(segment1, 0),
            new Pair<>(segment1, 1)
        ), handles);
    }

    @Test
    @DisplayName("each leg segment sits on its node, turned by the next node's world rotation")
    void segmentsFollowNodes() {
        solveLeg();
        RenderSpiderEntities.Part segment1 = tick().parts.get(2);

        assertEquals(nodes.get(1).getPosition(), segment1.origin);
        assertEquals(new Matrix4f().rotate(nodes.get(2).getWorldRotation()), segment1.parent);
    }

    @Test
    @DisplayName("the tree is rebuilt when the layout changes")
    void rebuiltOnLayoutChange() {
        RenderSpiderEntities.Retained beforeSolve = tick();
        assertEquals(2, beforeSolve.entries.size());

        solveLeg();
        RenderSpiderEntities.Retained solved = tick();
        assertNotSame(beforeSolve, solved);
        assertEquals(5, solved.entries.size());

        plan = plan(bodyModel, segments);
        RenderSpiderEntities.Retained newPlan = tick();
        assertNotSame(solved, newPlan);

        bodyModel.setPieces(pieces(2));
        RenderSpiderEntities.Retained newPieces = tick();
        assertNotSame(newPlan, newPieces);

        segments.get(1).getModel().getPieces().add(piece());
        RenderSpiderEntities.Retained addedPiece = tick();
        assertNotSame(newPieces, addedPiece);
        assertEquals(6, addedPiece.entries.size());

        assertSame(addedPiece, tick());
    }

    @Test
    @DisplayName("a spider not rendered for a frame is forgotten")
    void forgottenWhenNotRendered() {
        RenderSpiderEntities.Retained first = tick();
        RenderSpiderEntities.endFrame();
        assertSame(first, tick());

        RenderSpiderEntities.endFrame();
        RenderSpiderEntities.endFrame();
        assertNotSame(first, tick());
    }
}
