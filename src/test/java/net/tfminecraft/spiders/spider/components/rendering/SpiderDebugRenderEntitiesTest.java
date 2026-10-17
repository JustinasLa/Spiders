package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.ContactInfo;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.LegTarget;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderLegIK;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderDebugOptions;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.testing.RenderTestSupport;
import net.tfminecraft.spiders.utilities.Capsule;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.LineSegment;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChain3D;
import net.tfminecraft.spiders.utilities.rendering.RenderGroup;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiderDebugRenderEntitiesTest {
    private static final float SCALE = 2f;

    private RenderTestSupport server;
    private World world;
    private final SpiderBody spider = mock(SpiderBody.class);
    private final SpiderOptions options = mock(SpiderOptions.class);
    private final SpiderDebugOptions debug = new SpiderDebugOptions();
    private final PointDetector pointDetector = new PointDetector();
    private final List<Leg> legs = new ArrayList<>();
    private final Vector contactAcceleration = new Vector();

    @BeforeEach
    void setup() {
        server = new RenderTestSupport();
        world = server.world;
        BodyPlan plan = mock(BodyPlan.class);
        when(plan.getScale()).thenReturn((double) SCALE);
        when(options.getBodyPlan()).thenReturn(plan);
        when(options.getDebug()).thenReturn(debug);
        when(spider.getWorld()).thenReturn(world);
        when(spider.getLegs()).thenReturn(legs);
        when(spider.getPosition()).thenReturn(new Vector(0.0, 64.0, 0.0));
        when(spider.getOrientation()).thenReturn(new Quaternionf());
        when(spider.getPreferredOrientation()).thenReturn(new Quaternionf());
        when(spider.getContactAcceleration()).thenReturn(contactAcceleration);
    }

    @AfterEach
    void close() {
        server.close();
    }

    private record LegState(boolean primary, boolean uncomfortable, boolean disabled, boolean grounded, boolean touching, boolean targetGrounded) {
    }

    private Leg leg(int index, LegState state) {
        SpiderLegIK ik = mock(SpiderLegIK.class);
        when(ik.getChain()).thenReturn(mock(IKChain3D.class));
        Leg leg = mock(Leg.class);
        when(leg.getIk()).thenReturn(ik);
        when(leg.getScanLine()).thenReturn(new LineSegment(new Vector(index, 70.0, 0.0), new Vector(index, 60.0, 0.0)));
        when(leg.getTriggerZone()).thenReturn(new Capsule(new Vector(index, 61.0, 1.0), new Vector(index, 61.0, 2.0), .25));
        when(leg.getEndEffector()).thenReturn(new Vector(index, 60.0, 3.0));
        when(leg.getTarget()).thenReturn(new LegTarget(new Vector(index, 60.0, 4.0), state.targetGrounded(), index));
        when(leg.isPrimary()).thenReturn(state.primary());
        when(leg.isUncomfortable()).thenReturn(state.uncomfortable());
        when(leg.isDisabled()).thenReturn(state.disabled());
        when(leg.isGrounded()).thenReturn(state.grounded());
        when(leg.getTouchingGround()).thenReturn(state.touching());
        legs.add(leg);
        return leg;
    }

    private RenderGroup render() {
        return SpiderDebugRenderEntities.spiderDebugRenderEntities(spider, pointDetector, options);
    }

    /** Submits one item of the group alone and returns the display it spawned. */
    private BlockDisplay draw(RenderGroup group, Object key) {
        RenderItem item = group.get(key);
        assertNotNull(item, () -> "no " + key);
        int before = server.spawned.size();
        item.submit(key);
        assertEquals(before + 1, server.spawned.size(), () -> key + " spawned one display");
        return (BlockDisplay) server.spawned.getLast();
    }

    private static Transformation transformationOf(BlockDisplay display) {
        ArgumentCaptor<Transformation> transformation = ArgumentCaptor.forClass(Transformation.class);
        verify(display, atLeastOnce()).setTransformation(transformation.capture());
        return transformation.getValue();
    }

    private static Matrix4f matrixOf(BlockDisplay display) {
        ArgumentCaptor<Matrix4f> matrix = ArgumentCaptor.forClass(Matrix4f.class);
        verify(display, atLeastOnce()).setTransformationMatrix(matrix.capture());
        return matrix.getValue();
    }

    private static void assertClose(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < 1e-5, () -> "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("each leg shows its scan bar, trigger zone, end effector and target, coloured by the leg's state")
    void legGuides() {
        Leg first = leg(0, new LegState(true, true, true, false, false, true));
        leg(1, new LegState(false, false, false, true, false, false));
        leg(2, new LegState(false, false, false, false, true, false));
        leg(3, new LegState(false, false, false, false, false, false));
        pointDetector.getSelectedLeg().put(mock(Player.class), first);

        RenderGroup group = render();

        for (int index = 0; index < 4; index++) assertInstanceOf(RenderGroup.class, group.get(new Pair<>("legIKGuides", index)));

        BlockDisplay scan0 = draw(group, new Pair<>("scanBar", 0));
        assertEquals(Material.GOLD_BLOCK, RenderTestSupport.blockOf(scan0));
        verify(scan0).setBrightness(new Display.Brightness(15, 15));
        assertEquals(new Location(world, 0.0, 70.0, 0.0), scan0.getLocation());
        assertClose(new Vector3f(.1f, 0f, 0f), matrixOf(scan0).transformDirection(new Vector3f(1f, 0f, 0f)).absolute());
        assertEquals(Material.IRON_BLOCK, RenderTestSupport.blockOf(draw(group, new Pair<>("scanBar", 1))));

        BlockDisplay zone0 = draw(group, new Pair<>("triggerZone", 0));
        assertEquals(Material.RED_STAINED_GLASS, RenderTestSupport.blockOf(zone0));
        verify(zone0).setBrightness(new Display.Brightness(15, 15));
        // as thick as the capsule (2 * radius), scaled with the spider
        assertClose(new Vector3f(0f, 1f, 0f), matrixOf(zone0).transformDirection(new Vector3f(0f, 1f, 0f)).absolute());
        assertEquals(Material.CYAN_STAINED_GLASS, RenderTestSupport.blockOf(draw(group, new Pair<>("triggerZone", 1))));

        BlockDisplay effector0 = draw(group, new Pair<>("endEffector", 0));
        assertEquals(new Location(world, 0.0, 60.0, 3.0), effector0.getLocation());
        verify(effector0).setTeleportDuration(1);
        verify(effector0).setBrightness(new Display.Brightness(15, 15));
        assertEquals(Geometry.centredTransform(.2f * SCALE, .2f * SCALE, .2f * SCALE), transformationOf(effector0));
        assertEquals(Material.BLACK_CONCRETE, RenderTestSupport.blockOf(effector0));
        BlockDisplay effector1 = draw(group, new Pair<>("endEffector", 1));
        assertEquals(Geometry.centredTransform(.15f * SCALE, .15f * SCALE, .15f * SCALE), transformationOf(effector1));
        assertEquals(Material.DIAMOND_BLOCK, RenderTestSupport.blockOf(effector1));
        assertEquals(Material.LAPIS_BLOCK, RenderTestSupport.blockOf(draw(group, new Pair<>("endEffector", 2))));
        assertEquals(Material.REDSTONE_BLOCK, RenderTestSupport.blockOf(draw(group, new Pair<>("endEffector", 3))));

        BlockDisplay target0 = draw(group, new Pair<>("targetPosition", 0));
        assertEquals(new Location(world, 0.0, 60.0, 4.0), target0.getLocation());
        verify(target0).setTeleportDuration(1);
        verify(target0).setBrightness(new Display.Brightness(15, 15));
        assertEquals(Geometry.centredTransform(.2f * SCALE, .2f * SCALE, .2f * SCALE), transformationOf(target0));
        assertEquals(Material.LIME_STAINED_GLASS, RenderTestSupport.blockOf(target0));
        assertEquals(Material.RED_STAINED_GLASS, RenderTestSupport.blockOf(draw(group, new Pair<>("targetPosition", 1))));
    }

    @Test
    @DisplayName("the facing marker sits ahead of the body, red while galloping and green otherwise")
    void directionMarker() {
        when(options.getGallop()).thenReturn(true);
        BlockDisplay galloping = draw(render(), "direction");
        assertEquals(Material.REDSTONE_BLOCK, RenderTestSupport.blockOf(galloping));
        verify(galloping).setTeleportDuration(1);
        verify(galloping).setInterpolationDuration(1);
        verify(galloping).setBrightness(new Display.Brightness(15, 15));
        assertEquals(new Location(world, 0.0, 64.0, 0.0), galloping.getLocation());
        assertClose(new Vector3f(0f, 0f, SCALE), matrixOf(galloping).transformPosition(new Vector3f(.5f, .5f, .5f)));

        when(options.getGallop()).thenReturn(false);
        server.close();
        server = new RenderTestSupport();
        when(spider.getWorld()).thenReturn(server.world);
        assertEquals(Material.EMERALD_BLOCK, RenderTestSupport.blockOf(draw(render(), "direction")));
    }

    @Test
    @DisplayName("the preferred orientation is drawn as three diamond axes: forward twice as long as right and up")
    void preferredAxes() {
        RenderGroup group = render();
        float thickness = .025f * SCALE;

        BlockDisplay forward = draw(group, "preferredForwards");
        assertEquals(Material.DIAMOND_BLOCK, RenderTestSupport.blockOf(forward));
        verify(forward).setTeleportDuration(1);
        verify(forward).setInterpolationDuration(1);
        assertClose(new Vector3f(thickness / 2, thickness / 2, thickness + 2f * SCALE), matrixOf(forward).transformPosition(new Vector3f(1f, 1f, 1f)));
        assertClose(new Vector3f(thickness + SCALE, thickness / 2, thickness / 2), matrixOf(draw(group, "preferredRight")).transformPosition(new Vector3f(1f, 1f, 1f)));
        assertClose(new Vector3f(thickness / 2, thickness + SCALE, thickness / 2), matrixOf(draw(group, "preferredUp")).transformPosition(new Vector3f(1f, 1f, 1f)));
    }

    private void contact(Vector supportAxis, Vector centerOfPressure, List<Vector> polygon, Vector centreOfMass) {
        when(spider.getContact()).thenReturn(new ContactInfo(supportAxis, centerOfPressure, polygon, centreOfMass));
    }

    @Test
    @DisplayName("the stance shows its support polygon, centre of mass and the push from the centre of pressure")
    void stance() {
        List<Vector> polygon = List.of(new Vector(0.0, 60.0, 0.0), new Vector(1.0, 60.0, 0.0), new Vector(0.0, 60.0, 1.0));
        contact(new Vector(0.0, 1.0, 0.0), new Vector(.3, 60.0, .3), polygon, new Vector(.3, 64.0, .3));

        RenderGroup group = render();

        for (int index = 0; index < 3; index++) {
            BlockDisplay edge = draw(group, new Pair<>("polygon", index));
            assertEquals(polygon.get(index).toLocation(world), edge.getLocation());
            assertEquals(Material.EMERALD_BLOCK, RenderTestSupport.blockOf(edge));
            verify(edge).setInterpolationDuration(0);
            verify(edge).setBrightness(new Display.Brightness(15, 15));
        }
        assertNull(group.get(new Pair<>("polygon", 3)));
        // the last edge closes the polygon
        assertClose(new Vector3f(0f, 0f, -1f), matrixOf((BlockDisplay) server.spawned.getLast()).transformPosition(new Vector3f(.5f, .5f, 1f)));

        BlockDisplay centre = draw(group, "centreOfMass");
        assertEquals(new Location(world, .3, 64.0, .3), centre.getLocation());
        assertEquals(Geometry.centredTransform(.1f * SCALE, .1f * SCALE, .1f * SCALE), transformationOf(centre));
        verify(centre).setTeleportDuration(1);
        verify(centre).setBrightness(new Display.Brightness(15, 15));
        assertEquals(Material.LAPIS_BLOCK, RenderTestSupport.blockOf(centre), "a vertical support axis");

        BlockDisplay push = draw(group, "acceleration");
        assertEquals(new Location(world, .3, 60.0, .3), push.getLocation());
        verify(push).setBrightness(new Display.Brightness(15, 15));
        assertEquals(Material.BLACK_CONCRETE, RenderTestSupport.blockOf(push), "no contact acceleration");
    }

    @Test
    @DisplayName("a tilted support axis turns the centre of mass red and any contact acceleration turns the push white")
    void unbalancedStance() {
        contact(new Vector(.5, 1.0, 0.0), new Vector(0.0, 60.0, 0.0), List.of(), new Vector(0.0, 64.0, 0.0));
        contactAcceleration.setY(1.0);

        RenderGroup group = render();

        assertEquals(Material.REDSTONE_BLOCK, RenderTestSupport.blockOf(draw(group, "centreOfMass")));
        assertEquals(Material.WHITE_CONCRETE, RenderTestSupport.blockOf(draw(group, "acceleration")));
        assertNull(group.get(new Pair<>("polygon", 0)));
    }

    @Test
    @DisplayName("parts of the stance that weren't worked out are left out")
    void partialStance() {
        contact(new Vector(0.0, 1.0, 0.0), null, null, null);
        RenderGroup noneKnown = render();
        assertNull(noneKnown.get(new Pair<>("polygon", 0)));
        assertNull(noneKnown.get("centreOfMass"));
        assertNull(noneKnown.get("acceleration"));

        contact(new Vector(0.0, 1.0, 0.0), null, null, new Vector(0.0, 64.0, 0.0));
        RenderGroup noPressure = render();
        assertNotNull(noPressure.get("centreOfMass"));
        assertNull(noPressure.get("acceleration"));
    }

    @Test
    @DisplayName("without a stance only the leg and body guides are drawn")
    void noStance() {
        leg(0, new LegState(false, false, false, false, false, false));
        RenderGroup group = render();
        assertNotNull(group.get("direction"));
        assertNotNull(group.get("preferredUp"));
        assertNull(group.get("centreOfMass"));
        assertNull(group.get("acceleration"));
    }

    @Test
    @DisplayName("each guide is drawn only when its debug option is on")
    void everyGuideCanBeTurnedOff() {
        leg(0, new LegState(false, false, false, false, false, false));
        contact(new Vector(0.0, 1.0, 0.0), new Vector(0.0, 60.0, 0.0), List.of(new Vector(), new Vector(1.0, 0.0, 0.0)), new Vector(0.0, 64.0, 0.0));
        debug.setScanBars(false);
        debug.setTriggerZones(false);
        debug.setEndEffectors(false);
        debug.setTargetPositions(false);
        debug.setIkGuides(false);
        debug.setLegPolygons(false);
        debug.setCentreOfMass(false);
        debug.setNormalForce(false);
        debug.setOrientation(false);
        debug.setPreferredOrientation(false);

        RenderGroup group = render();
        group.submit("debug");

        assertTrue(server.spawned.isEmpty());
        for (Object key : List.of(new Pair<>("legIKGuides", 0), new Pair<>("scanBar", 0), new Pair<>("triggerZone", 0), new Pair<>("endEffector", 0),
            new Pair<>("targetPosition", 0), "direction", "preferredForwards", "preferredRight", "preferredUp", new Pair<>("polygon", 0), "centreOfMass", "acceleration")) {
            assertNull(group.get(key), () -> key + " drawn while off");
        }
    }
}
