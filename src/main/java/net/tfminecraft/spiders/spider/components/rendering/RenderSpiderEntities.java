package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChainWorldNode3D;
import net.tfminecraft.spiders.utilities.rendering.RenderEntity;
import net.tfminecraft.spiders.utilities.rendering.RenderGroup;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import net.tfminecraft.spiders.utilities.rendering.RenderUtilities;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector4f;

import java.util.List;
import java.util.Objects;
import java.util.WeakHashMap;

public final class RenderSpiderEntities {
    private RenderSpiderEntities() {
    }

    public static RenderGroup renderSpider(SpiderBody spider, Cloak cloak, SpiderOptions options) {
        RenderGroup group = new RenderGroup();

        Matrix4f transform = new Matrix4f().rotate(spider.getOrientation());
        group.set(spider, renderModel(spider, cloak, spider.getPosition(), options.getBodyPlan().getBodyModel(), transform, options));


        List<Leg> legs = spider.getLegs();
        for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
            Leg leg = legs.get(legIndex);
            List<IKChainWorldNode3D> positions = leg.getIk().getNodes();
            List<Quaternionf> rotations = leg.getIk().getSegmentModelRotations();

            for (int segmentIndex = 0; segmentIndex < rotations.size(); segmentIndex++) {
                SegmentPlan segmentPlan = segmentPlan(options, legIndex, segmentIndex);
                if (segmentPlan == null) continue;

                Vector parent = positions.get(segmentIndex).getPosition();

                Matrix4f segmentTransform = new Matrix4f().rotate(rotations.get(segmentIndex));
                group.set(new Pair<>(legIndex, segmentIndex), renderModel(spider, cloak, parent, segmentPlan.getModel(), segmentTransform, options));
            }
        }

        return group;
    }

    private static SegmentPlan segmentPlan(SpiderOptions options, int legIndex, int segmentIndex) {
        List<LegPlan> legPlans = options.getBodyPlan().getLegs();
        if (legIndex < 0 || legIndex >= legPlans.size()) return null;
        List<SegmentPlan> segments = legPlans.get(legIndex).getSegments();
        if (segments == null || segmentIndex < 0 || segmentIndex >= segments.size()) return null;
        return segments.get(segmentIndex);
    }

    private static RenderGroup renderModel(
        SpiderBody spider,
        Cloak cloak,
        Vector position,
        DisplayModel model,
        Matrix4f transformation,
        SpiderOptions options
    ) {
        RenderGroup group = new RenderGroup();

        List<BlockDisplayModelPiece> pieces = model.getPieces();
        for (int index = 0; index < pieces.size(); index++) {
            BlockDisplayModelPiece piece = pieces.get(index);
            group.set(index, renderModelPiece(spider, cloak, position, piece, transformation, options));
        }

        return group;
    }


    private static RenderItem renderModelPiece(
        SpiderBody spider,
        Cloak cloak,
        Vector position,
        BlockDisplayModelPiece piece,
        Matrix4f transformation,
        SpiderOptions options
    ) {
        NamespacedKey itemModel = piece.getItemModel();
        if (itemModel != null) return renderItemPiece(spider, position, piece, itemModel, transformation);
        return RenderUtilities.renderBlock(
            position.toLocation(spider.getWorld()),
            it -> {
                it.setTeleportDuration(1);
                it.setInterpolationDuration(1);
            },
            it -> {
                Matrix4f transform = new Matrix4f(transformation).mul(piece.getTransform());
                RenderUtilities.interpolateTransform(it, transform);

                Pair<BlockData, Display.Brightness> cloaked = null;
                if (piece.getTags().contains("cloak")) {
                    Vector4f relative = transform.transform(new Vector4f(.5f, .5f, .5f, 1f));
                    Vector piecePosition = position.clone();
                    piecePosition.setX(piecePosition.getX() + relative.x);
                    piecePosition.setY(piecePosition.getY() + relative.y);
                    piecePosition.setZ(piecePosition.getZ() + relative.z);

                    cloaked = cloak.getPiece(piece, spider.getWorld(), piecePosition, piece.getBlock(), piece.getBrightness(), options.getCloak());
                }

                if (cloaked != null) {
                    it.setBlock(cloaked.first());
                    it.setBrightness(cloaked.second());
                } else {
                    it.setBlock(piece.getBlock());
                    it.setBrightness(piece.getBrightness());
                }
            }
        );
    }

    /** Item models are drawn centred on the display, so shift them into the piece's unit cube. */
    private static RenderEntity<ItemDisplay> renderItemPiece(
        SpiderBody spider,
        Vector position,
        BlockDisplayModelPiece piece,
        NamespacedKey itemModel,
        Matrix4f transformation
    ) {
        return RenderUtilities.renderItem(
            position.toLocation(spider.getWorld()),
            it -> {
                it.setTeleportDuration(1);
                it.setInterpolationDuration(1);
            },
            it -> {
                RenderUtilities.interpolateTransform(it, new Matrix4f(transformation).mul(piece.getTransform()).translate(.5f, .5f, .5f));
                it.setBrightness(piece.getBrightness());
                Pair<NamespacedKey, Float> shown = new Pair<>(itemModel, piece.getItemModelData());
                if (!Objects.equals(shownItems.get(it), shown)) {
                    shownItems.put(it, shown);
                    it.setItemStack(modelItem(itemModel, piece.getItemModelData()));
                }
            }
        );
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
