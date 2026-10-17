package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderLegIK;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.CloakOptions;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.testing.RenderTestSupport;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.rendering.RenderEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RenderSpiderEntitiesTest {
    private RenderTestSupport server;
    private World world;
    private final Cloak cloak = mock(Cloak.class);
    private final CloakOptions cloakOptions = mock(CloakOptions.class);
    private final SpiderOptions options = mock(SpiderOptions.class);
    private final BodyPlan plan = mock(BodyPlan.class);
    private final DisplayModel bodyModel = new DisplayModel(new ArrayList<>());
    private SpiderBody spider;
    private final List<Leg> legs = new ArrayList<>();
    private final List<LegPlan> legPlans = new ArrayList<>();

    @BeforeEach
    void setup() {
        server = new RenderTestSupport();
        world = server.world;
        when(options.getBodyPlan()).thenReturn(plan);
        when(options.getCloak()).thenReturn(cloakOptions);
        when(plan.getBodyModel()).thenReturn(bodyModel);
        when(plan.getLegs()).thenReturn(legPlans);
        spider = SpiderBody.create(world, new Vector(1.0, 64.0, 2.0));
        spider.setLegs(legs);
    }

    @AfterEach
    void close() {
        // two frames without this spider forget its tree
        RenderSpiderEntities.endFrame();
        RenderSpiderEntities.endFrame();
        server.close();
    }

    private static BlockDisplayModelPiece piece(Material material, Matrix4f transform, String... tags) {
        return new BlockDisplayModelPiece(RenderTestSupport.blockData(material), transform, new Display.Brightness(3, 7), new ArrayList<>(List.of(tags)));
    }

    private void render() {
        RenderSpiderEntities.render(spider, cloak, options);
    }

    private static Matrix4f appliedMatrix(Display display) {
        ArgumentCaptor<Matrix4f> matrix = ArgumentCaptor.forClass(Matrix4f.class);
        verify(display).setTransformationMatrix(matrix.capture());
        return matrix.getValue();
    }

    @Test
    @DisplayName("each body piece is drawn as a block display at the spider, turned with it, in the piece's block and light")
    void bodyPiecesDrawnAtTheSpider() {
        Matrix4f transform = new Matrix4f().translation(.25f, 0f, 0f).scale(.5f);
        BlockDisplayModelPiece piece = piece(Material.STONE, transform);
        bodyModel.getPieces().add(piece);
        spider.getOrientation().rotateY(1f);

        render();

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        assertEquals(new Location(world, 1.0, 64.0, 2.0), display.getLocation());
        verify(display).setTeleportDuration(1);
        verify(display).setInterpolationDuration(1);
        verify(display).setBlock(piece.getBlock());
        verify(display).setBrightness(piece.getBrightness());
        assertEquals(new Matrix4f().rotation(new Quaternionf().rotateY(1f)).mul(transform), appliedMatrix(display));
        verifyNoInteractions(cloak);
    }

    @Test
    @DisplayName("a moved spider moves its displays; nothing new is spawned")
    void displaysFollowTheSpider() {
        bodyModel.getPieces().add(piece(Material.STONE, new Matrix4f()));
        render();
        spider.getPosition().setX(10.0);
        render();

        assertEquals(1, server.spawned.size());
        assertEquals(10.0, server.spawned.getFirst().getLocation().getX());
    }

    @Test
    @DisplayName("a cloak piece asks the cloak for its look at the centre of the piece and wears what it gets")
    void cloakPiecesAskTheCloak() {
        BlockDisplayModelPiece piece = piece(Material.STONE, new Matrix4f().translation(1f, 2f, 3f), "cloak");
        bodyModel.getPieces().add(piece);
        BlockData disguise = RenderTestSupport.blockData(Material.MOSS_BLOCK);
        Display.Brightness dark = new Display.Brightness(0, 15);
        when(cloak.getPiece(any(), any(), any(), any(), any(), any())).thenReturn(new Pair<>(disguise, dark));

        render();

        ArgumentCaptor<Vector> position = ArgumentCaptor.forClass(Vector.class);
        verify(cloak).getPiece(same(piece), same(world), position.capture(), same(piece.getBlock()), same(piece.getBrightness()), same(cloakOptions));
        assertEquals(new Vector(2.5, 66.5, 5.5), position.getValue());
        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        verify(display).setBlock(disguise);
        verify(display).setBrightness(dark);
        verify(display, never()).setBlock(piece.getBlock());
    }

    @Test
    @DisplayName("a cloak piece the cloak leaves alone keeps its own block")
    void uncloakedPieceKeepsItsBlock() {
        BlockDisplayModelPiece piece = piece(Material.STONE, new Matrix4f(), "cloak");
        bodyModel.getPieces().add(piece);

        render();

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        verify(display).setBlock(piece.getBlock());
        verify(display).setBrightness(piece.getBrightness());
    }

    private record ItemStacks(MockedConstruction<ItemStack> construction, List<ItemMeta> metas, List<CustomModelDataComponent> components) implements AutoCloseable {
        @Override
        public void close() {
            construction.close();
        }
    }

    /** {@code new ItemStack(...)} without a server: each stack edits a fresh meta mock. */
    @SuppressWarnings("unchecked")
    private static ItemStacks itemStacks() {
        List<ItemMeta> metas = new ArrayList<>();
        List<CustomModelDataComponent> components = new ArrayList<>();
        MockedConstruction<ItemStack> construction = mockConstruction(ItemStack.class, (stack, context) -> {
            assertEquals(List.of(Material.PAPER), context.arguments());
            doAnswer(call -> {
                ItemMeta meta = mock(ItemMeta.class);
                CustomModelDataComponent component = mock(CustomModelDataComponent.class);
                when(meta.getCustomModelDataComponent()).thenReturn(component);
                ((Consumer<ItemMeta>) call.getArgument(0)).accept(meta);
                metas.add(meta);
                components.add(component);
                return true;
            }).when(stack).editMeta(any(Consumer.class));
        });
        return new ItemStacks(construction, metas, components);
    }

    @Test
    @DisplayName("an item-model piece is an item display filling the piece's cube, given a new item only when its model or data changes")
    void itemPieces() {
        NamespacedKey fang = new NamespacedKey("tfmc", "fang");
        NamespacedKey claw = new NamespacedKey("tfmc", "claw");
        BlockDisplayModelPiece piece = piece(Material.STONE, new Matrix4f());
        piece.setItemModel(fang);
        piece.setItemModelData(2f);
        bodyModel.getPieces().add(piece);

        try (ItemStacks stacks = itemStacks()) {
            render();
            ItemDisplay display = server.spawned(ItemDisplay.class).getFirst();
            assertEquals(1, stacks.construction().constructed().size());
            verify(display).setItemStack(stacks.construction().constructed().getFirst());
            verify(stacks.metas().getFirst()).setItemModel(fang);
            verify(stacks.components().getFirst()).setFloats(List.of(2f));
            verify(stacks.metas().getFirst()).setCustomModelDataComponent(stacks.components().getFirst());
            verify(display).setBrightness(piece.getBrightness());
            assertEquals(new Matrix4f().translation(.5f, .5f, .5f), appliedMatrix(display));

            render();
            assertEquals(1, stacks.construction().constructed().size(), "unchanged item, no new stack");

            piece.setItemModelData(3f);
            render();
            assertEquals(2, stacks.construction().constructed().size());
            verify(stacks.components().getLast()).setFloats(List.of(3f));

            piece.setItemModel(claw); // rebuilds the tree, but the same display is reused
            render();
            assertEquals(3, stacks.construction().constructed().size());
            verify(stacks.metas().getLast()).setItemModel(claw);
            verify(display, times(3)).setItemStack(any());
            assertEquals(1, server.spawned.size());
        }
    }

    @Test
    @DisplayName("an item display whose piece just lost its item model is left alone until the tree is rebuilt")
    @SuppressWarnings("unchecked")
    void itemDisplayWithoutModelUntouched() {
        BlockDisplayModelPiece piece = piece(Material.STONE, new Matrix4f());
        piece.setItemModel(new NamespacedKey("tfmc", "fang"));
        bodyModel.getPieces().add(piece);
        RenderSpiderEntities.Retained tree = RenderSpiderEntities.prepare(spider, cloak, options);

        piece.setItemModel(null);
        ItemDisplay display = mock(ItemDisplay.class);
        ((RenderEntity<ItemDisplay>) tree.entries.getFirst().entity).getUpdate().accept(display);

        verifyNoInteractions(display);
    }

    private Leg leg(List<IKChainWorldNode3D> nodes) {
        SpiderLegIK ik = mock(SpiderLegIK.class);
        when(ik.getNodes()).thenReturn(nodes);
        Leg leg = mock(Leg.class);
        when(leg.getIk()).thenReturn(ik);
        return leg;
    }

    private static List<IKChainWorldNode3D> nodes(int count) {
        List<IKChainWorldNode3D> nodes = new ArrayList<>();
        for (int i = 0; i < count; i++) nodes.add(new IKChainWorldNode3D(new Vector(i, 60.0, 0.0), new Quaternionf().rotateX(i), new Quaternionf()));
        return nodes;
    }

    private LegPlan legPlan(List<SegmentPlan> segments) {
        LegPlan legPlan = mock(LegPlan.class);
        when(legPlan.getSegments()).thenReturn(segments);
        return legPlan;
    }

    private static SegmentPlan segment(BlockDisplayModelPiece... pieces) {
        return new SegmentPlan(1.0, null, new DisplayModel(new ArrayList<>(List.of(pieces))));
    }

    @Test
    @DisplayName("only segments the body plan describes are drawn: extra legs, extra nodes and legs without segments draw nothing")
    void segmentsWithoutAPlanAreSkipped() {
        // leg 0: three nodes but one planned segment; leg 1: segments missing; leg 2: no plan at all
        legs.add(leg(nodes(3)));
        legs.add(leg(nodes(2)));
        legs.add(leg(nodes(2)));
        legPlans.add(legPlan(List.of(segment(piece(Material.STONE, new Matrix4f())))));
        legPlans.add(legPlan(null));

        RenderSpiderEntities.Retained tree = RenderSpiderEntities.prepare(spider, cloak, options);

        assertEquals(2, tree.parts.size(), "the body and leg 0's first segment");
        assertEquals(0, tree.parts.get(1).legIndex);
        assertEquals(0, tree.parts.get(1).segmentIndex);
        assertEquals(1, tree.entries.size());
        assertEquals(new Pair<>(new Pair<>(spider, new Pair<>(0, 0)), 0), tree.entries.getFirst().handle);
        assertSame(tree, RenderSpiderEntities.prepare(spider, cloak, options));
    }

    @Test
    @DisplayName("leg segments are drawn on their nodes")
    void segmentsDrawnOnNodes() {
        legs.add(leg(nodes(2)));
        legPlans.add(legPlan(List.of(segment(piece(Material.IRON_BLOCK, new Matrix4f())))));

        render();

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        assertEquals(new Location(world, 0.0, 60.0, 0.0), display.getLocation());
        assertEquals(new Matrix4f().rotation(new Quaternionf().rotateX(1f)), appliedMatrix(display));
        assertEquals(Material.IRON_BLOCK, RenderTestSupport.blockOf(display));
    }

    @Test
    @DisplayName("a layout that grows or shrinks between ticks is rebuilt")
    void layoutCountChangesRebuild() {
        legs.add(leg(nodes(2)));
        legPlans.add(legPlan(List.of(segment(piece(Material.STONE, new Matrix4f())))));
        RenderSpiderEntities.Retained one = RenderSpiderEntities.prepare(spider, cloak, options);

        legs.add(leg(nodes(2)));
        RenderSpiderEntities.Retained two = RenderSpiderEntities.prepare(spider, cloak, options);
        assertTrue(one != two);

        legs.removeLast();
        RenderSpiderEntities.Retained back = RenderSpiderEntities.prepare(spider, cloak, options);
        assertTrue(two != back);
        assertSame(back, RenderSpiderEntities.prepare(spider, cloak, options));
        verify(cloak, never()).getPiece(any(), any(), any(), any(), any(), eq(cloakOptions));
    }
}
