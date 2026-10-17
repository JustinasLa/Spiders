package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.rendering.EmptyRenderItem;
import net.tfminecraft.spiders.utilities.rendering.RenderEntity;
import net.tfminecraft.spiders.utilities.rendering.RenderGroup;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.assertVector;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.chain;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.hinge;
import static net.tfminecraft.spiders.utilities.inverse_kinematics.IKTestSupport.restPose;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The debug guides are built as a tree of line displays; these tests read the tree without spawning anything. */
class IKDebugRendererTest {
    private static final double EPS = 1e-5;

    private final World world = mock(World.class);
    private MockedStatic<Bukkit> bukkit;

    @BeforeEach
    void mockBlockData() {
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> {
            BlockData data = mock(BlockData.class);
            when(data.getMaterial()).thenReturn(call.getArgument(0));
            return data;
        });
    }

    @AfterEach
    void restoreBukkit() {
        bukkit.close();
    }

    /** One rendered line, read back from its display callbacks. */
    private record Line(Vector start, Vector end, Material material, float thickness, Display.Brightness brightness) {
    }

    @SuppressWarnings("unchecked")
    private Line line(RenderItem item) {
        RenderEntity<BlockDisplay> entity = (RenderEntity<BlockDisplay>) assertInstanceOf(RenderEntity.class, item);
        assertEquals(BlockDisplay.class, entity.getClazz());
        assertSame(world, entity.getLocation().getWorld());

        BlockDisplay display = mock(BlockDisplay.class);
        entity.getInit().accept(display);
        entity.getUpdate().accept(display);

        ArgumentCaptor<Matrix4f> matrix = ArgumentCaptor.forClass(Matrix4f.class);
        verify(display).setTransformationMatrix(matrix.capture());
        ArgumentCaptor<BlockData> block = ArgumentCaptor.forClass(BlockData.class);
        verify(display).setBlock(block.capture());
        ArgumentCaptor<Display.Brightness> brightness = ArgumentCaptor.forClass(Display.Brightness.class);
        verify(display).setBrightness(brightness.capture());

        Vector start = entity.getLocation().toVector();
        // the unit block is scaled to thickness x thickness x length and turned along the line
        Vector3f along = matrix.getValue().transformPosition(new Vector3f(0.5f, 0.5f, 1f));
        Vector end = start.clone().add(new Vector(along.x, along.y, along.z));
        float thickness = matrix.getValue().getScale(new Vector3f()).x;
        return new Line(start, end, block.getValue().getMaterial(), thickness, brightness.getValue());
    }

    @SuppressWarnings("unchecked")
    private static Map<Object, RenderItem> children(RenderItem group) {
        try {
            Field field = RenderGroup.class.getDeclaredField("children");
            field.setAccessible(true);
            return (Map<Object, RenderItem>) field.get(assertInstanceOf(RenderGroup.class, group));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static List<Object> keys(RenderItem group) {
        return new ArrayList<>(children(group).keySet());
    }

    private static List<Object> indices(int count, Object... extra) {
        List<Object> keys = new ArrayList<>();
        for (int index = 0; index < count; index++) keys.add(index);
        keys.addAll(List.of(extra));
        return keys;
    }

    private static IKDebugStyle style(double axisLength, boolean drawNodeAxes, boolean drawConstraints) {
        return new IKDebugStyle(0.6, 1.0, 0.8, 0.3, 0.4, axisLength, 1.0, 18, 10, 12, 3, drawNodeAxes, drawConstraints);
    }

    @Test
    @DisplayName("guides hold constraints per joint, axes per node, and the target's axes")
    void treeStructure() {
        IKChain3D leg = IKTestSupport.leg();
        IKTarget3D target = new IKTarget3D(new Vector(0, -1, 2), new Quaternionf());
        RenderGroup guides = IKDebugRenderer.renderIKGuides(world, leg, restPose(leg), target);

        assertEquals(List.of("constraints", new Pair<>("axes", 0), new Pair<>("axes", 1), new Pair<>("axes", 2), new Pair<>("axes", 3), "target"),
            keys(guides));
        RenderItem constraints = guides.get("constraints");
        assertEquals(List.of(0, 1, 2), keys(constraints));

        RenderGroup hipGuide = (RenderGroup) children(constraints).get(0);
        assertEquals(List.of("swingRing", "swingSpoke0", "swingSpoke1", "swingSpoke2", "twistArc", "twistIndicator"), keys(hipGuide));
        assertEquals(indices(12), keys(hipGuide.get("swingRing")));
        assertEquals(indices(10, "spokeStart", "spokeEnd"), keys(hipGuide.get("twistArc")));

        assertEquals(indices(18, "spokeStart", "spokeEnd"), keys(children(constraints).get(1)));
        assertEquals(indices(3), keys(guides.get(new Pair<>("axes", 0))));
        assertEquals(indices(3), keys(guides.get("target")));
    }

    @Test
    @DisplayName("without a target orientation no target axes are drawn")
    void noTargetAxes() {
        IKChain3D arm = chain(1.0, hinge(0, 1));
        assertEquals(List.of("constraints", new Pair<>("axes", 0), new Pair<>("axes", 1)),
            keys(IKDebugRenderer.renderIKGuides(world, arm, restPose(arm))));
        assertEquals(List.of("constraints", new Pair<>("axes", 0), new Pair<>("axes", 1)),
            keys(IKDebugRenderer.renderIKGuides(world, arm, restPose(arm), new IKTarget3D(new Vector(0, 0, 1)))));
    }

    @Test
    @DisplayName("the style can switch off constraints and node axes")
    void styleSwitches() {
        IKChain3D arm = chain(1.0, hinge(0, 1));
        IKTarget3D target = new IKTarget3D(new Vector(), new Quaternionf());
        assertEquals(List.of("target"), keys(IKDebugRenderer.renderIKGuides(world, arm, restPose(arm), target, style(0.6, false, false))));
        assertEquals(List.of("constraints"), keys(IKDebugRenderer.renderIKGuides(world, arm, restPose(arm), null, style(0.6, false, true))));
    }

    @Test
    @DisplayName("node axes are red, green and blue lines along the node's rotated X, Y and Z")
    void nodeAxes() {
        IKChain3D arm = chain(1.0, hinge(0, 1));
        IKChainPose3D pose = arm.createPose(new Vector(1, 2, 3), new Quaternionf().rotateY((float) (Math.PI / 2)));
        IKTarget3D target = new IKTarget3D(new Vector(-4, 0, 0), new Quaternionf());
        RenderGroup guides = IKDebugRenderer.renderIKGuides(world, arm, pose, target);

        RenderGroup axes = (RenderGroup) guides.get(new Pair<>("axes", 0));
        Line x = line(axes.get(0));
        Line y = line(axes.get(1));
        Line z = line(axes.get(2));
        assertVector(1, 2, 3, x.start(), EPS);
        // yawed 90 degrees: X points to -Z and Z points to +X
        assertVector(1, 2, 3 - 0.6, x.end(), EPS);
        assertVector(1, 2 + 0.6, 3, y.end(), EPS);
        assertVector(1 + 0.6, 2, 3, z.end(), EPS);
        assertEquals(List.of(Material.RED_CONCRETE, Material.LIME_CONCRETE, Material.BLUE_CONCRETE), List.of(x.material(), y.material(), z.material()));
        assertEquals(0.015f, x.thickness(), 1e-6f);
        assertEquals(new Display.Brightness(15, 15), x.brightness());

        // target axes are longer
        Line targetZ = line(((RenderGroup) guides.get("target")).get(2));
        assertVector(-4, 0, 0, targetZ.start(), EPS);
        assertVector(-4, 0, 1, targetZ.end(), EPS);
    }

    @Test
    @DisplayName("a hinge guide is an arc over the joint's range, bending the same way the joint does")
    void hingeArc() {
        IKChain3D arm = chain(1.0, hinge(0, Math.PI / 2));
        IKChainPose3D pose = arm.createPose(new Vector(5, 0, 0), new Quaternionf());
        RenderGroup arc = (RenderGroup) children(IKDebugRenderer.renderIKGuides(world, arm, pose).get("constraints")).get(0);

        Line first = line(arc.get(0));
        Line last = line(arc.get(17));
        // radius 0.6 of the 1 block segment, from the reference (+Z) at 0 to -Y at 90 degrees
        assertVector(5, 0, 0.6, first.start(), EPS);
        double step = Math.PI / 2 / 18;
        assertVector(5, -0.6 * Math.sin(step), 0.6 * Math.cos(step), first.end(), EPS);
        assertVector(5, -0.6, 0, last.end(), EPS);
        assertEquals(Material.LIGHT_BLUE_CONCRETE, first.material());
        assertEquals(0.02f, first.thickness(), 1e-6f);

        Line spokeStart = line(arc.get("spokeStart"));
        Line spokeEnd = line(arc.get("spokeEnd"));
        assertVector(5, 0, 0, spokeStart.start(), EPS);
        assertVector(5, 0, 0.6, spokeStart.end(), EPS);
        assertVector(5, 0, 0, spokeEnd.start(), EPS);

        // the spoke at the max angle points where the segment points with the hinge at max
        ((IKHingeJointState3D) pose.getSegments().get(0)).setAngle(Math.PI / 2);
        Vector segment = IKTestSupport.endEffector(arm, pose).subtract(pose.getPosition()).normalize();
        Vector spoke = spokeEnd.end().clone().subtract(spokeEnd.start()).normalize();
        assertVector(segment, spoke, EPS);
    }

    @Test
    @DisplayName("a hinge whose reference lies along its axis still gets a perpendicular arc")
    void hingeReferenceAlongAxis() {
        // axis +Y, reference +Y: falls back to +Y, which is also the axis, so to +X
        IKChain3D yHinge = new IKChain3D(List.of(new IKChainSegment3D(1.0,
            new IKHingeJoint3D(0, 1, new Vector(0, 1, 0), new Vector(0, 2, 0)))));
        RenderGroup yArc = (RenderGroup) children(IKDebugRenderer.renderIKGuides(world, yHinge, restPose(yHinge)).get("constraints")).get(0);
        assertVector(0.6, 0, 0, line(yArc.get(0)).start(), EPS);

        // axis +X, reference +X: falls back to +Y
        IKChain3D xHinge = new IKChain3D(List.of(new IKChainSegment3D(1.0,
            new IKHingeJoint3D(0, 1, new Vector(1, 0, 0), new Vector(3, 0, 0)))));
        RenderGroup xArc = (RenderGroup) children(IKDebugRenderer.renderIKGuides(world, xHinge, restPose(xHinge)).get("constraints")).get(0);
        assertVector(0, 0.6, 0, line(xArc.get(0)).start(), EPS);
    }

    @Test
    @DisplayName("a swing-twist guide draws the swing cone, the twist range and the current twist")
    void swingTwistGuide() {
        IKChain3D arm = chain(2.0, new IKSwingTwistJoint3D(0.5, -0.2, 0.4, new Vector(0, 1, 0)));
        IKChainPose3D pose = restPose(arm);
        RenderGroup guide = (RenderGroup) children(IKDebugRenderer.renderIKGuides(world, arm, pose).get("constraints")).get(0);

        // the cone rim: 12 points, 2 blocks out, 0.5 rad off the joint's +Z, joined into a closed ring
        RenderGroup ring = (RenderGroup) guide.get("swingRing");
        for (int index = 0; index < 12; index++) {
            Line edge = line(ring.get(index));
            double angle = 2 * Math.PI * index / 12;
            assertVector(2 * Math.cos(angle) * Math.sin(0.5), 2 * Math.sin(angle) * Math.sin(0.5), 2 * Math.cos(0.5), edge.start(), EPS);
            assertVector(line(ring.get((index + 1) % 12)).start(), edge.end(), EPS);
            assertEquals(Material.CYAN_CONCRETE, edge.material());
        }

        // spokes from the joint to every fourth rim point
        for (int spoke = 0; spoke < 3; spoke++) {
            Line line = line(guide.get("swingSpoke" + spoke));
            assertVector(0, 0, 0, line.start(), EPS);
            assertVector(line(ring.get(spoke * 4)).start(), line.end(), EPS);
        }

        // twist range: radius 0.3, 0.8 of the way along the segment, from -0.2 to 0.4 rad off the reference (+Y)
        RenderGroup twist = (RenderGroup) guide.get("twistArc");
        assertVector(0.3 * Math.sin(0.2), 0.3 * Math.cos(0.2), 1.6, line(twist.get(0)).start(), EPS);
        assertVector(-0.3 * Math.sin(0.4), 0.3 * Math.cos(0.4), 1.6, line(twist.get(9)).end(), EPS);
        assertVector(0, 0, 1.6, line(twist.get("spokeStart")).start(), EPS);
        assertEquals(Material.PINK_CONCRETE, line(twist.get(0)).material());

        // with no twist the indicator points along the reference
        Line indicator = line(guide.get("twistIndicator"));
        assertVector(0, 0, 1.6, indicator.start(), EPS);
        assertVector(0, 0.4, 1.6, indicator.end(), EPS);
        assertEquals(Material.MAGENTA_CONCRETE, indicator.material());
        assertEquals(0.0175f, indicator.thickness(), 1e-6f);
    }

    @Test
    @DisplayName("the twist indicator turns with the joint's twist and stays inside the drawn range")
    void twistIndicatorFollowsTwist() {
        IKChain3D arm = chain(2.0, new IKSwingTwistJoint3D(0.5, -0.2, 0.4, new Vector(0, 1, 0)));
        IKChainPose3D pose = restPose(arm);
        ((IKSwingTwistJointState3D) pose.getSegments().get(0)).setTwist(0.3);
        Line indicator = line(((RenderGroup) children(IKDebugRenderer.renderIKGuides(world, arm, pose).get("constraints")).get(0)).get("twistIndicator"));

        assertVector(-0.4 * Math.sin(0.3), 0.4 * Math.cos(0.3), 1.6, indicator.end(), EPS);
    }

    @Test
    @DisplayName("a swung joint's guides centre on where the segment actually points")
    void swungJointGuides() {
        IKChain3D arm = chain(2.0, new IKSwingTwistJoint3D(0.5, -0.2, 0.4, new Vector(0, 1, 0)));
        IKChainPose3D pose = restPose(arm);
        ((IKSwingTwistJointState3D) pose.getSegments().get(0)).setSwingY(0.4);
        RenderGroup guide = (RenderGroup) children(IKDebugRenderer.renderIKGuides(world, arm, pose).get("constraints")).get(0);

        Vector direction = IKTestSupport.endEffector(arm, pose).normalize();
        assertVector(direction.clone().multiply(1.6), line(((RenderGroup) guide.get("twistArc")).get("spokeStart")).start(), EPS);
        assertVector(direction.clone().multiply(1.6), line(guide.get("twistIndicator")).start(), EPS);
    }

    @Test
    @DisplayName("a zero-length joint borrows the next segment's length for its guide; a trailing one gets none")
    void zeroLengthSegments() {
        IKChain3D chain = new IKChain3D(List.of(
            new IKChainSegment3D(0.0, hinge(0, 1)),
            new IKChainSegment3D(1.5, hinge(0, 1)),
            new IKChainSegment3D(0.0, hinge(0, 1))
        ));
        RenderItem constraints = IKDebugRenderer.renderIKGuides(world, chain, restPose(chain)).get("constraints");
        assertEquals(List.of(0, 1), keys(constraints));

        Line first = line(((RenderGroup) children(constraints).get(0)).get(0));
        assertEquals(0.9, first.start().length(), EPS);
    }

    @Test
    @DisplayName("zero-length lines are left empty instead of spawning a display")
    void degenerateLines() {
        IKChain3D arm = chain(1.0, hinge(0.5, 0.5));
        RenderGroup guides = IKDebugRenderer.renderIKGuides(world, arm, restPose(arm), null, style(0.0, true, true));

        for (RenderItem axis : children(guides.get(new Pair<>("axes", 0))).values()) assertSame(EmptyRenderItem.INSTANCE, axis);
        RenderGroup arc = (RenderGroup) children(guides.get("constraints")).get(0);
        for (int index = 0; index < 18; index++) assertSame(EmptyRenderItem.INSTANCE, arc.get(index));
        // the spokes still have length
        assertNotSame(EmptyRenderItem.INSTANCE, arc.get("spokeStart"));
        assertEquals(0.6, line(arc.get("spokeEnd")).end().length(), EPS);
    }

    @Test
    @DisplayName("lines are built without touching the world")
    void noEntitiesSpawned() {
        IKChain3D leg = IKTestSupport.leg();
        IKDebugRenderer.renderIKGuides(world, leg, restPose(leg), new IKTarget3D(new Vector(), new Quaternionf()));
        org.mockito.Mockito.verifyNoInteractions(world);
    }

    @Test
    @DisplayName("each line's update sets its block every time, so a changed colour shows up")
    void updateSetsBlock() {
        IKChain3D arm = chain(1.0, hinge(0, 1));
        RenderItem axis = ((RenderGroup) IKDebugRenderer.renderIKGuides(world, arm, restPose(arm)).get(new Pair<>("axes", 1))).get(0);
        @SuppressWarnings("unchecked")
        Consumer<BlockDisplay> update = ((RenderEntity<BlockDisplay>) axis).getUpdate();
        BlockDisplay display = mock(BlockDisplay.class);
        update.accept(display);
        update.accept(display);
        verify(display, org.mockito.Mockito.times(2)).setBlock(any());
    }
}
