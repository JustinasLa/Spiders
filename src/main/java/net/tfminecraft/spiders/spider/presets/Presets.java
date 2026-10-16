package net.tfminecraft.spiders.spider.presets;

import net.tfminecraft.spiders.spider.configuration.BodyPlan;
import net.tfminecraft.spiders.spider.configuration.LegPlan;
import net.tfminecraft.spiders.spider.configuration.SegmentPlan;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.DisplayModel;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKChain3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKHingeJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKJoint3D;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKSwingTwistJoint3D;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Material;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class Presets {
    private Presets() {}

    private static final double ROOT_MAX_SWING = 0.3 * Math.PI;
    private static final double ROOT_TWIST = 0.075 * Math.PI;
    private static final double KNEE_LEEWAY = 0.65 * Math.PI;

    private static final float PED_HIP_ROTATION = -(Maths.toRadians(20f));

    public static IKJoint3D hipJoint() {
        return new IKSwingTwistJoint3D(
            ROOT_MAX_SWING,
            -ROOT_TWIST,
            ROOT_TWIST,
            Maths.upVector()
        );
    }

    public static IKJoint3D kneeJoint(double leeway, boolean inverted) {
        return kneeJoint(
            inverted ? -leeway : 0.0,
            inverted ? 0.0 : leeway
        );
    }

    public static IKJoint3D kneeJoint(double leeway) {
        return kneeJoint(leeway, false);
    }

    public static IKJoint3D kneeJoint() {
        return kneeJoint(KNEE_LEEWAY);
    }

    private static IKJoint3D kneeJoint(double min, double max) {
        return new IKHingeJoint3D(
            min,
            max,
            new Vector(1, 0, 0),
            IKChain3D.getCHAIN_AXIS()
        );
    }

    private static List<SegmentPlan> equalLength(int segmentCount, double length) {
        List<SegmentPlan> segments = new ArrayList<>(segmentCount);
        for (int index = 0; index < segmentCount; index++) {
            IKJoint3D joint = index == 0 ? hipJoint() : kneeJoint();
            segments.add(new SegmentPlan(length, joint, DisplayModel.empty()));
        }
        return segments;
    }

    private static void addLegPair(BodyPlan plan, Vector root, Vector rest, List<SegmentPlan> segments, float hipRotation) {
        Quaternionf orientation = new Quaternionf().rotateX(hipRotation);
        List<LegPlan> legs = new ArrayList<>(plan.getLegs());
        legs.add(new LegPlan(new Vector(root.getX(), root.getY(), root.getZ()), new Vector(rest.getX(), rest.getY(), rest.getZ()), segments, orientation));
        plan.setLegs(legs);
        List<SegmentPlan> mirrored = new ArrayList<>(segments.size());
        for (SegmentPlan segment : segments) mirrored.add(segment.clone());
        legs = new ArrayList<>(plan.getLegs());
        legs.add(new LegPlan(new Vector(-root.getX(), root.getY(), root.getZ()), new Vector(-rest.getX(), rest.getY(), rest.getZ()), mirrored, orientation));
        plan.setLegs(legs);
    }

    // The shape can also be built and posed without a server's sound registry.
    public static BodyPlan octopodBodyPlan(int segmentCount, double segmentLength) {
        BodyPlan plan = new BodyPlan();
        addLegPair(plan, new Vector(.0, .0, .1), new Vector(1.0, .0, 1.6), equalLength(segmentCount, 1.1 * segmentLength), PED_HIP_ROTATION);
        addLegPair(plan, new Vector(.0, .0, .0), new Vector(1.3, .0, 0.4), equalLength(segmentCount, 1.0 * segmentLength), PED_HIP_ROTATION);
        addLegPair(plan, new Vector(.0, .0, -.1), new Vector(1.3, .0, -0.9), equalLength(segmentCount, 1.1 * segmentLength), PED_HIP_ROTATION);
        addLegPair(plan, new Vector(.0, .0, -.2), new Vector(1.1, .0, -2.5), equalLength(segmentCount, 1.6 * segmentLength), PED_HIP_ROTATION);
        ApplyLegModels.applyLineLegModel(plan, Material.NETHERITE_BLOCK.createBlockData());
        return plan;
    }


    public static SpiderOptions defaultPreset() {
        return Cinderweaver.cinderweaver();
    }
}
