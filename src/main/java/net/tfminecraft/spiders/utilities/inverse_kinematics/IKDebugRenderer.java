package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.LineSegment;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.rendering.EmptyRenderItem;
import net.tfminecraft.spiders.utilities.rendering.RenderGroup;
import net.tfminecraft.spiders.utilities.rendering.RenderItem;
import net.tfminecraft.spiders.utilities.rendering.RenderUtilities;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

public final class IKDebugRenderer {
    private IKDebugRenderer() {
    }

    public static RenderGroup renderIKGuides(
        World world,
        IKChain3D chain,
        IKChainPose3D pose,
        IKTarget3D target
    ) {
        return renderIKGuides(world, chain, pose, target, new IKDebugStyle());
    }

    static RenderGroup renderIKGuides(
        World world,
        IKChain3D chain,
        IKChainPose3D pose,
        IKTarget3D target,
        IKDebugStyle style
    ) {
        RenderGroup out = new RenderGroup();
        List<IKChainWorldNode3D> nodes = chain.getWorldNodes(pose);

        if (style.getDrawConstraints()) {
            out.set("constraints", renderConstraints(world, chain, pose, nodes, style));
        }

        if (style.getDrawNodeAxes()) {
            for (int index = 0; index < nodes.size(); index++) {
                IKChainWorldNode3D node = nodes.get(index);
                out.set(new Pair<>("axes", index), renderAxes(
                    world,
                    node.getPosition(),
                    node.getWorldRotation(),
                    style.getAxisLength(),
                    0.015f
                ));
            }
        }

        Quaternionf targetOrientation = target != null ? target.getOrientation() : null;
        if (targetOrientation != null) out.set("target", renderAxes(
            world,
            target.getPosition(),
            targetOrientation,
            style.getTargetAxisLength(),
            0.015f
        ));

        return out;
    }

    public static RenderGroup renderIKGuides(World world, IKChain3D chain, IKChainPose3D pose) {
        return renderIKGuides(world, chain, pose, null);
    }

    private static RenderGroup renderConstraints(
        World world,
        IKChain3D chain,
        IKChainPose3D pose,
        List<IKChainWorldNode3D> nodes,
        IKDebugStyle style
    ) {
        RenderGroup out = new RenderGroup();
        List<IKChainSegment3D> segments = chain.getSegments();
        for (int index = 0; index < segments.size(); index++) {
            IKChainSegment3D segment = segments.get(index);
            IKChainWorldNode3D parent = nodes.get(index);
            IKChainWorldNode3D child = nodes.get(index + 1);

            // Use the next segment's length if length is 0.
            double guideLength;
            if (segment.getLength() > 1e-9) {
                guideLength = segment.getLength();
            } else if (index + 1 < segments.size()) {
                guideLength = segments.get(index + 1).getLength();
            } else {
                guideLength = 0.0;
            }
            if (guideLength <= 1e-9) continue;

            switch (segment.getJoint()) {
                case IKHingeJoint3D joint -> out.set(index, addHingeGuide(
                    world,
                    guideLength,
                    parent,
                    joint,
                    style
                ));

                case IKSwingTwistJoint3D joint -> out.set(index, addSwingTwistGuide(
                    world,
                    guideLength,
                    parent,
                    child,
                    joint,
                    style
                ));
            }
        }
        return out;
    }

    private static RenderGroup addHingeGuide(
        World world,
        double length,
        IKChainWorldNode3D parent,
        IKHingeJoint3D joint,
        IKDebugStyle style
    ) {
        WorldArc arc = arc(
            parent.getPosition(),
            parent.getWorldRotation(),
            joint.getAxis(),
            joint.getReference(),
            style.hingeArcRadius(length),
            joint.getMinAngle(),
            joint.getMaxAngle(),
            style.getHingeArcSteps()
        );

        return renderArc(world, arc, style.getHingeColor(), .02f);
    }

    private static RenderGroup addSwingTwistGuide(
        World world,
        double length,
        IKChainWorldNode3D parent,
        IKChainWorldNode3D child,
        IKSwingTwistJoint3D joint,
        IKDebugStyle style
    ) {
        Vector axisWorld = Maths.rotate(IKChain3D.getCHAIN_AXIS(), child.getWorldRotation());
        Vector axisInParent = Maths.rotate(axisWorld.clone(), new Quaternionf(parent.getWorldRotation()).invert());

        Quaternionf swingRotation = IKChain3D.quaternionFromTo(IKChain3D.getCHAIN_AXIS(), axisInParent, new Vector(1, 0, 0));
        Quaternionf swingBasis = new Quaternionf(parent.getWorldRotation()).mul(swingRotation);

        double coneLength = style.swingConeLength(length);
        double sinSwing = Math.sin(joint.getMaxSwing());
        double cosSwing = Math.cos(joint.getMaxSwing());

        float thickness = .02f;
        float twistIndicatorThickness = .0175f;

        List<Vector> rim = new ArrayList<>();
        for (int step = 0; step < style.getConeRingSteps(); step++) {
            double angle = 2 * Math.PI * step / style.getConeRingSteps();
            Vector3f offset = new Vector3f(
                (float) (Math.cos(angle) * sinSwing),
                (float) (Math.sin(angle) * sinSwing),
                (float) cosSwing
            ).mul((float) coneLength);

            rim.add(localToWorld(parent.getPosition(), parent.getWorldRotation(), offset));
        }

        RenderGroup out = new RenderGroup();

        out.set("swingRing", renderRing(world, rim, style.getSwingColor(), thickness));

        for (int spoke = 0; spoke < style.getConeSpokes(); spoke++) {
            Vector tip = rim.get(spoke * style.getConeRingSteps() / style.getConeSpokes());
            out.set("swingSpoke" + spoke, renderLine(world, parent.getPosition(), tip, style.getSwingColor(), thickness));
        }

        Vector3f ringCenterOffset = new Vector3f(0f, 0f, (float) (length * style.getTwistArcDistanceFraction()));

        WorldArc twistArc = arc(
            parent.getPosition(),
            swingBasis,
            IKChain3D.getCHAIN_AXIS(),
            joint.getReferenceAxis(),
            style.getTwistArcRadius(),
            joint.getMinTwist(),
            joint.getMaxTwist(),
            style.getTwistArcSteps(),
            ringCenterOffset
        );

        out.set("twistArc", renderArc(world, twistArc, style.getTwistArcColor(), thickness));

        Quaternionf indicatorRotation = new Quaternionf(child.getWorldRotation())
            .mul(IKChain3D.quaternionFromTo(IKChain3D.getCHAIN_AXIS(), joint.getReferenceAxis(), new Vector(1, 0, 0)));

        Vector ringCenterWorld = parent.getPosition().clone()
            .add(axisWorld.clone().multiply(length * style.getTwistArcDistanceFraction()));
        Vector indicatorTip = ringCenterWorld.clone()
            .add(
                Maths.rotate(IKChain3D.getCHAIN_AXIS(), indicatorRotation)
                    .multiply(style.getTwistIndicatorLength())
            );

        out.set("twistIndicator", renderLine(world, ringCenterWorld, indicatorTip, style.getTwistIndicatorColor(), twistIndicatorThickness));
        return out;
    }

    private static Vector3f orthogonalize(Vector3f vector, Vector3f axis) {
        Vector3f out = new Vector3f(vector);
        out.sub(new Vector3f(axis).mul(vector.dot(axis)));

        if (out.lengthSquared() < 1e-10f) {
            // `vector` was parallel to `axis`; fall back to any perpendicular axis
            out.set(0f, 1f, 0f);
            out.sub(new Vector3f(axis).mul(out.dot(axis)));

            if (out.lengthSquared() < 1e-10f) {
                out.set(1f, 0f, 0f);
            }
        }

        return out.normalize();
    }

    private static RenderItem renderLine(
        World world,
        Vector start,
        Vector end,
        Material material,
        float thickness
    ) {
        if (start.distanceSquared(end) <= 1.0e-12) return EmptyRenderItem.INSTANCE;

        return RenderUtilities.renderLine(
            world,
            new LineSegment(start, end),
            null,
            thickness,
            1,
            it -> it.setBrightness(new Display.Brightness(15, 15)),
            it -> it.setBlock(material.createBlockData())
        );
    }

    private static RenderGroup renderArc(
        World world,
        WorldArc arc,
        Material material,
        float thickness
    ) {
        RenderGroup out = new RenderGroup();
        for (int index = 0; index < arc.points().size() - 1; index++) {
            out.set(index, renderLine(world, arc.points().get(index), arc.points().get(index + 1), material, thickness));
        }

        out.set("spokeStart", renderLine(world, arc.pivot(), arc.points().get(0), material, thickness));
        out.set("spokeEnd", renderLine(world, arc.pivot(), arc.points().get(arc.points().size() - 1), material, thickness));

        return out;
    }

    private static RenderGroup renderRing(
        World world,
        List<Vector> points,
        Material material,
        float thickness
    ) {
        RenderGroup out = new RenderGroup();
        for (int index = 0; index < points.size(); index++) {
            Vector next = points.get((index + 1) % points.size());
            out.set(index, renderLine(world, points.get(index), next, material, thickness));
        }
        return out;
    }

    private static RenderGroup renderAxes(
        World world,
        Vector position,
        Quaternionf rotation,
        double length,
        float thickness
    ) {
        List<Pair<Vector, Material>> axes = List.of(
            new Pair<>(new Vector(1.0, 0.0, 0.0), Material.RED_CONCRETE),
            new Pair<>(new Vector(0.0, 1.0, 0.0), Material.LIME_CONCRETE),
            new Pair<>(new Vector(0.0, 0.0, 1.0), Material.BLUE_CONCRETE)
        );

        RenderGroup out = new RenderGroup();
        for (int index = 0; index < axes.size(); index++) {
            Vector axis = axes.get(index).first();
            Material block = axes.get(index).second();
            Vector tip = position.clone().add(Maths.rotate(axis.clone(), rotation).multiply(length));
            out.set(index, renderLine(world, position, tip, block, thickness));
        }
        return out;
    }

    private record WorldArc(
        Vector pivot,
        List<Vector> points
    ) {
    }

    private static WorldArc arc(
        Vector origin,
        Quaternionf rotation,
        Vector axis,
        Vector forward,
        double radius,
        double minAngle,
        double maxAngle,
        int steps,
        Vector3f centerOffset
    ) {
        Vector3f axis3f = axis.toVector3f().normalize();
        Vector3f forward3f = orthogonalize(forward.toVector3f(), axis3f);
        Vector3f side3f = new Vector3f(axis3f).cross(forward3f).normalize();

        List<Vector> points = new ArrayList<>();
        for (int step = 0; step <= steps; step++) {
            float angle = (float) (minAngle + (maxAngle - minAngle) * step / steps);
            float cosine = (float) Math.cos(angle);
            float sine = (float) Math.sin(angle);
            float scale = (float) radius;

            Vector3f point = new Vector3f(forward3f).mul(cosine * scale)
                .add(new Vector3f(side3f).mul(sine * scale))
                .add(centerOffset);

            points.add(localToWorld(origin, rotation, point));
        }

        return new WorldArc(
            localToWorld(origin, rotation, centerOffset),
            points
        );
    }

    private static WorldArc arc(
        Vector origin,
        Quaternionf rotation,
        Vector axis,
        Vector forward,
        double radius,
        double minAngle,
        double maxAngle,
        int steps
    ) {
        return arc(origin, rotation, axis, forward, radius, minAngle, maxAngle, steps, new Vector3f());
    }

    private static Vector localToWorld(Vector origin, Quaternionf rotation, Vector3f local) {
        Vector3f rotated = new Vector3f(local).rotate(rotation);
        return new Vector(
            origin.getX() + rotated.x,
            origin.getY() + rotated.y,
            origin.getZ() + rotated.z
        );
    }
}
