package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.utilities.BlockDisplayModelPiece;
import net.tfminecraft.spiders.utilities.DisplayModel;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Display;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.List;

public final class ApplyLegModels {
    private ApplyLegModels() {}


    private static DisplayModel createDefaultModel(BlockData block, double length, double thickness) {
        return new DisplayModel(new ArrayList<>(List.of(new BlockDisplayModelPiece(
            block,
            new Matrix4f()
                .scale((float) thickness, (float) thickness, (float) length)
                .translate(-.5f, -.5f, .0f),
            new Display.Brightness(0, 15),
            new ArrayList<>(List.of("cloak"))
        ))));
    }

    public static void applyEmptyLegModel(BodyPlan bodyPlan) {
        for (LegPlan leg : bodyPlan.getLegs()) {
            for (SegmentPlan segment : leg.getSegments()) {
                segment.setModel(DisplayModel.empty());
            }
        }
    }

    public static void applyLineLegModel(BodyPlan bodyPlan, BlockData block) {
        double rootThickness = 1.0 / 16 * 4.5;
        double tipThickness = 1.0 / 16 * 1.5;

        for (LegPlan leg : bodyPlan.getLegs()) {
            List<SegmentPlan> segments = leg.getSegments();
            for (int index = 0; index < segments.size(); index++) {
                SegmentPlan segment = segments.get(index);
                double fraction = (double) index / (segments.size() - 1);
                double thickness = rootThickness + (tipThickness - rootThickness) * fraction;
                segment.setModel(createDefaultModel(block, segment.getLength(), thickness));
            }
        }
    }

    public static void applyBotLegModel(BodyPlan bodyPlan) {
        for (LegPlan leg : bodyPlan.getLegs()) {
            List<SegmentPlan> visibleSegments = new ArrayList<>();
            for (SegmentPlan segment : leg.getSegments()) {
                if (segment.getLength() > 0.0) visibleSegments.add(segment);
            }

            int modelIndex = 0;
            for (SegmentPlan segment : leg.getSegments()) {
                if (segment.getLength() <= 0.0) {
                    segment.setModel(DisplayModel.empty());
                    continue;
                }

                DisplayModel model;
                if (modelIndex == 0) model = SpiderLegModel.getBASE();
                else if (modelIndex == visibleSegments.size() - 2) model = SpiderLegModel.getTIBIA();
                else if (modelIndex == visibleSegments.size() - 1) model = SpiderLegModel.getTIP();
                else model = SpiderLegModel.getFEMUR();

                segment.setModel(model.clone().scale(1f, 1f, (float) segment.getLength()));
                modelIndex++;
            }
        }
    }
}
