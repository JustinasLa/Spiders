package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import net.tfminecraft.spiders.utilities.rendering.RenderUtilities;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Draws a spider as one display per model piece. Each spider keeps its tree of displays between ticks;
 * it is rebuilt only when the layout changes (body plan, models, piece lists, legs), otherwise each tick
 * just moves the parts and submits the same {@link net.tfminecraft.spiders.utilities.rendering.RenderEntity}s.
 */
public final class RenderSpiderEntities {
    private RenderSpiderEntities() {
    }

    /**
     * Each rendered spider's tree. Not weak: the handles inside reference the spider, so {@link #endFrame()}
     * drops the trees of spiders that stopped being rendered instead.
     */
    private static final Map<SpiderBody, Retained> retained = new HashMap<>();

    /** Places and submits the spider's displays, rebuilding its tree first if the layout changed. */
    public static void render(SpiderBody spider, Cloak cloak, SpiderOptions options) {
        Retained tree = prepare(spider, cloak, options);
        for (Entry entry : tree.entries) entry.entity.submit(entry.handle);
    }

    /** Forgets the trees of spiders not rendered since the last call. */
    public static void endFrame() {
        retained.values().removeIf(it -> !it.rendered);
        for (Retained it : retained.values()) it.rendered = false;
    }

    /** The spider's tree, reused when its layout is unchanged, with every part moved to this tick's pose. */
    static Retained prepare(SpiderBody spider, Cloak cloak, SpiderOptions options) {
        Retained tree = retained.get(spider);
        if (tree == null || !tree.shape.matches(spider, options)) {
            tree = build(spider, options);
            retained.put(spider, tree);
        }
        tree.cloak = cloak;
        tree.options = options;
        tree.rendered = true;
        tree.place(spider);
        return tree;
    }

    private static Retained build(SpiderBody spider, SpiderOptions options) {
        Retained tree = new Retained(Shape.of(spider, options));
        World world = spider.getWorld();

        // handles match what RenderGroup.submit(spider) produced: (parent handle, key) at every level
        tree.add(new Part(world, -1, -1), new Pair<>(spider, spider), options.getBodyPlan().getBodyModel());

        List<Leg> legs = spider.getLegs();
        for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
            List<IKChainWorldNode3D> nodes = legs.get(legIndex).getIk().getNodes();
            for (int segmentIndex = 0; segmentIndex < nodes.size() - 1; segmentIndex++) {
                SegmentPlan segmentPlan = segmentPlan(options, legIndex, segmentIndex);
                if (segmentPlan == null) continue;
                tree.add(new Part(world, legIndex, segmentIndex), new Pair<>(spider, new Pair<>(legIndex, segmentIndex)), segmentPlan.getModel());
            }
        }

        return tree;
    }

    private static SegmentPlan segmentPlan(SpiderOptions options, int legIndex, int segmentIndex) {
        List<LegPlan> legPlans = options.getBodyPlan().getLegs();
        if (legIndex < 0 || legIndex >= legPlans.size()) return null;
        List<SegmentPlan> segments = legPlans.get(legIndex).getSegments();
        if (segments == null || segmentIndex < 0 || segmentIndex >= segments.size()) return null;
        return segments.get(segmentIndex);
    }

    /** A spider's displays: its parts, one entry per piece, and the cloak and options of the latest tick. */
    static final class Retained {
        final Shape shape;
        final List<Part> parts = new ArrayList<>();
        final List<Entry> entries = new ArrayList<>();
        Cloak cloak;
        SpiderOptions options;
        boolean rendered;

        Retained(Shape shape) {
            this.shape = shape;
        }

        private void add(Part part, Object modelHandle, DisplayModel model) {
            parts.add(part);
            List<BlockDisplayModelPiece> pieces = model.getPieces();
            for (int index = 0; index < pieces.size(); index++) entries.add(new Entry(this, part, new Pair<>(modelHandle, index), pieces.get(index)));
        }

        private void place(SpiderBody spider) {
            for (Part part : parts) {
                if (part.legIndex < 0) {
                    part.place(spider.getPosition(), spider.getOrientation());
                    continue;
                }
                List<IKChainWorldNode3D> nodes = spider.getLegs().get(part.legIndex).getIk().getNodes();
                part.place(nodes.get(part.segmentIndex).getPosition(), nodes.get(part.segmentIndex + 1).getWorldRotation());
            }
        }
    }

    /** Where one model is drawn: the body ({@code legIndex} -1) or one leg segment. */
    static final class Part {
        final int legIndex;
        final int segmentIndex;
        final Vector origin = new Vector();
        /** Shared by the part's displays; moved in place each tick. */
        final Location location;
        final Matrix4f parent = new Matrix4f();

        Part(World world, int legIndex, int segmentIndex) {
            this.legIndex = legIndex;
            this.segmentIndex = segmentIndex;
            this.location = new Location(world, 0.0, 0.0, 0.0);
        }

        private void place(Vector position, Quaternionf rotation) {
            origin.copy(position);
            location.setX(position.getX());
            location.setY(position.getY());
            location.setZ(position.getZ());
            parent.rotation(rotation);
        }
    }

    /** One piece's display: its handle and the render entity that draws it, made once. */
    static final class Entry {
        final Object handle;
        final BlockDisplayModelPiece piece;
        final RenderItem entity;
        private final Retained tree;
        private final Part part;
        private final Matrix4f transform = new Matrix4f();
        private final Vector4f centre = new Vector4f();
        private final Vector piecePosition = new Vector();

        Entry(Retained tree, Part part, Object handle, BlockDisplayModelPiece piece) {
            this.tree = tree;
            this.part = part;
            this.handle = handle;
            this.piece = piece;
            // the shape check rebuilds the entry when the piece's item model changes, so the display type stays right
            this.entity = piece.getItemModel() != null
                ? RenderUtilities.renderItem(part.location, Entry::init, this::updateItem)
                : RenderUtilities.renderBlock(part.location, Entry::init, this::updateBlock);
        }

        private static void init(Display display) {
            display.setTeleportDuration(1);
            display.setInterpolationDuration(1);
        }

        private void updateBlock(BlockDisplay it) {
            transform.set(part.parent).mul(piece.getTransform());
            RenderUtilities.interpolateTransform(it, transform);

            Pair<BlockData, Display.Brightness> cloaked = null;
            if (piece.getTags().contains("cloak")) {
                transform.transform(centre.set(.5f, .5f, .5f, 1f));
                piecePosition.copy(part.origin);
                piecePosition.setX(piecePosition.getX() + centre.x);
                piecePosition.setY(piecePosition.getY() + centre.y);
                piecePosition.setZ(piecePosition.getZ() + centre.z);

                cloaked = tree.cloak.getPiece(piece, part.location.getWorld(), piecePosition, piece.getBlock(), piece.getBrightness(), tree.options.getCloak());
            }

            if (cloaked != null) {
                it.setBlock(cloaked.first());
                it.setBrightness(cloaked.second());
            } else {
                it.setBlock(piece.getBlock());
                it.setBrightness(piece.getBrightness());
            }
        }

        /** Item models are drawn centred on the display, so shift them into the piece's unit cube. */
        private void updateItem(ItemDisplay it) {
            NamespacedKey itemModel = piece.getItemModel();
            if (itemModel == null) return;
            RenderUtilities.interpolateTransform(it, transform.set(part.parent).mul(piece.getTransform()).translate(.5f, .5f, .5f));
            it.setBrightness(piece.getBrightness());
            float data = piece.getItemModelData();
            Pair<NamespacedKey, Float> shown = shownItems.get(it);
            if (shown == null || !shown.first().equals(itemModel) || Float.compare(shown.second(), data) != 0) {
                shownItems.put(it, new Pair<>(itemModel, data));
                it.setItemStack(modelItem(itemModel, data));
            }
        }
    }

    /**
     * The identities and counts a tree's layout follows from, in walk order. A piece's block, brightness,
     * transform and item model data are edited in place (blinking, mimic pose) and read at submit time,
     * so only which pieces there are and which of them are items is compared.
     */
    static final class Shape {
        private final List<Object> recorded = new ArrayList<>();
        private boolean recording;
        private int cursor;
        private boolean same;

        static Shape of(SpiderBody spider, SpiderOptions options) {
            Shape shape = new Shape();
            shape.recording = true;
            shape.same = true;
            shape.walk(spider, options);
            shape.recording = false;
            return shape;
        }

        /** Whether the spider's layout is still the recorded one; allocates nothing. */
        boolean matches(SpiderBody spider, SpiderOptions options) {
            cursor = 0;
            same = true;
            walk(spider, options);
            return same && cursor == recorded.size();
        }

        private void walk(SpiderBody spider, SpiderOptions options) {
            BodyPlan plan = options.getBodyPlan();
            identity(plan);
            DisplayModel body = plan.getBodyModel();
            identity(body);
            pieces(body.getPieces());

            List<Leg> legs = spider.getLegs();
            identity(legs);
            count(legs.size());
            for (int legIndex = 0; legIndex < legs.size() && same; legIndex++) {
                // empty before the leg's first solve
                List<IKChainWorldNode3D> nodes = legs.get(legIndex).getIk().getNodes();
                count(nodes.size());
                for (int segmentIndex = 0; segmentIndex < nodes.size() - 1 && same; segmentIndex++) {
                    SegmentPlan segmentPlan = segmentPlan(options, legIndex, segmentIndex);
                    identity(segmentPlan);
                    if (segmentPlan == null) continue;
                    identity(segmentPlan.getModel());
                    pieces(segmentPlan.getModel().getPieces());
                }
            }
        }

        /** The list, each piece in it and whether (and as what) it is drawn as an item, which picks the display type. */
        private void pieces(List<BlockDisplayModelPiece> pieces) {
            identity(pieces);
            count(pieces.size());
            for (int index = 0; index < pieces.size() && same; index++) {
                BlockDisplayModelPiece piece = pieces.get(index);
                identity(piece);
                equal(piece.getItemModel());
            }
        }

        private void identity(Object value) {
            if (recording) recorded.add(value);
            else same = same && cursor < recorded.size() && recorded.get(cursor++) == value;
        }

        private void equal(Object value) {
            if (recording) recorded.add(value);
            else same = same && cursor < recorded.size() && Objects.equals(recorded.get(cursor++), value);
        }

        private void count(int value) {
            if (recording) recorded.add(value);
            else same = same && cursor < recorded.size() && recorded.get(cursor++) instanceof Integer it && it == value;
        }
    }

    private static final WeakHashMap<ItemDisplay, Pair<NamespacedKey, Float>> shownItems = new WeakHashMap<>();

    private static ItemStack modelItem(NamespacedKey model, float data) {
        ItemStack stack = new ItemStack(Material.PAPER);
        stack.editMeta(meta -> {
            meta.setItemModel(model);
            CustomModelDataComponent component = meta.getCustomModelDataComponent();
            component.setFloats(List.of(data));
            meta.setCustomModelDataComponent(component);
        });
        return stack;
    }
}
