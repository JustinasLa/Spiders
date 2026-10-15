package net.tfminecraft.spiders.spider.components.rendering;

import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.ContactInfo;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.LineSegment;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.inverse_kinematics.IKDebugRenderer;
import net.tfminecraft.spiders.utilities.maths.Maths;
import net.tfminecraft.spiders.utilities.rendering.RenderEntity;
import net.tfminecraft.spiders.utilities.rendering.RenderGroup;
import net.tfminecraft.spiders.utilities.rendering.RenderUtilities;
import org.bukkit.Material;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

public final class SpiderDebugRenderEntities {
    private SpiderDebugRenderEntities() {
    }

    public static RenderGroup spiderDebugRenderEntities(SpiderBody spider, PointDetector pointDetector, SpiderOptions options) {
        RenderGroup group = new RenderGroup();

        float scale = (float) options.getBodyPlan().getScale();

        List<Leg> legs = spider.getLegs();
        for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
            Leg leg = legs.get(legIndex);
            // Render IK guides
            if (options.getDebug().getIkGuides()) group.set(new Pair<>("legIKGuides", legIndex), IKDebugRenderer.renderIKGuides(
                spider.getWorld(),
                leg.getIk().getChain(),
                leg.getIk().getPose()
            ));
        }

        for (int legIndex = 0; legIndex < legs.size(); legIndex++) {
            Leg leg = legs.get(legIndex);
            // Render scan bars
            if (options.getDebug().getScanBars()) group.set(new Pair<>("scanBar", legIndex), RenderUtilities.renderLine(
                spider.getWorld(),
                leg.getScanLine(),
                null,
                .05f * scale,
                1,
                it -> {
                    it.setBrightness(new Display.Brightness(15, 15));
                },
                it -> {
                    Material material = leg.isPrimary() ? Material.GOLD_BLOCK : Material.IRON_BLOCK;
                    it.setBlock(material.createBlockData());
                }
            ));

            // Render trigger capsule
            if (options.getDebug().getTriggerZones()) group.set(new Pair<>("triggerZone", legIndex), RenderUtilities.renderLine(
                spider.getWorld(),
                leg.getTriggerZone().line(),
                null,
                (float) (2 * leg.getTriggerZone().getRadius()) * scale,
//            thickness = .07f * scale,
                1,
                it -> {
                    it.setBrightness(new Display.Brightness(15, 15));
                },
                it -> {
                    Material material = leg.isUncomfortable() ? Material.RED_STAINED_GLASS : Material.CYAN_STAINED_GLASS;
                    it.setBlock(material.createBlockData());
                }
            ));

            // Render end effector
            if (options.getDebug().getEndEffectors()) group.set(new Pair<>("endEffector", legIndex), RenderUtilities.renderBlock(
                spider.getWorld(),
                leg.getEndEffector(),
                it -> {
                    it.setTeleportDuration(1);
                    it.setBrightness(new Display.Brightness(15, 15));
                },
                it -> {
                    float size = (pointDetector.getSelectedLeg().containsValue(leg) ? .2f : .15f) * scale;
                    it.setTransformation(Geometry.centredTransform(size, size, size));
                    if (leg.isDisabled()) it.setBlock(Material.BLACK_CONCRETE.createBlockData());
                    else if (leg.isGrounded()) it.setBlock(Material.DIAMOND_BLOCK.createBlockData());
                    else if (leg.getTouchingGround()) it.setBlock(Material.LAPIS_BLOCK.createBlockData());
                    else it.setBlock(Material.REDSTONE_BLOCK.createBlockData());
                }
            ));

            // Render target position
            if (options.getDebug().getTargetPositions()) group.set(new Pair<>("targetPosition", legIndex), RenderUtilities.renderBlock(
                leg.getTarget().getPosition().toLocation(spider.getWorld()),
                it -> {
                    it.setTeleportDuration(1);
                    it.setBrightness(new Display.Brightness(15, 15));

                    float size = 0.2f * scale;
                    it.setTransformation(Geometry.centredTransform(size, size, size));
                },
                it -> {
                    Material material = leg.getTarget().isGrounded() ? Material.LIME_STAINED_GLASS : Material.RED_STAINED_GLASS;
                    it.setBlock(material.createBlockData());
                }
            ));
        }

        // Render spider direction
        if (options.getDebug().getOrientation()) group.set("direction", RenderUtilities.renderBlock(
            spider.getPosition().toLocation(spider.getWorld()),
            it -> {
                it.setTeleportDuration(1);
                it.setInterpolationDuration(1);
                it.setBrightness(new Display.Brightness(15, 15));
            },
            it -> {
                it.setBlock(options.getGallop() ? Material.REDSTONE_BLOCK.createBlockData() : Material.EMERALD_BLOCK.createBlockData());

                float size = .1f * scale;
                float displacement = 1f * scale;
                Matrix4f transform = new Matrix4f()
                    .rotate(spider.getOrientation())
                    .translate(Maths.forwardVector().toVector3f().mul(displacement))
                    .scale(size, size, size)
                    .translate(-.5f, -.5f, -.5f);

                RenderUtilities.interpolateTransform(it, transform);
            }
        ));

        // Render preferred orientation
        if (options.getDebug().getPreferredOrientation()) {
            float thickness = .025f * scale;
            group.set("preferredForwards", renderEntity(spider, spider.getPreferredOrientation(), Maths.forwardVector(), thickness, 2.0f * scale, Material.DIAMOND_BLOCK));
            group.set("preferredRight"   , renderEntity(spider, spider.getPreferredOrientation(), Maths.rightVector()  , thickness, 1.0f * scale, Material.DIAMOND_BLOCK));
            group.set("preferredUp"      , renderEntity(spider, spider.getPreferredOrientation(), Maths.upVector()     , thickness, 1.0f * scale, Material.DIAMOND_BLOCK));
        }


        ContactInfo stance = spider.getContact();
        if (stance == null) return group;
        if (options.getDebug().getLegPolygons() && stance.getContactPolygon() != null) {
            List<Vector> points = stance.getContactPolygon();//.map { it.toLocation(spider.world)}
            for (int i = 0; i < points.size(); i++) {
                Vector a = points.get(i);
                Vector b = points.get((i + 1) % points.size());

                group.set(new Pair<>("polygon", i), RenderUtilities.renderLine(
                    spider.getWorld(),
                    new LineSegment(a, b),
                    null,
                    .05f * scale,
                    0,
                    it -> it.setBrightness(new Display.Brightness(15, 15)),
                    it -> it.setBlock(Material.EMERALD_BLOCK.createBlockData())
                ));
            }
        }

        if (options.getDebug().getCentreOfMass() && stance.getCentreOfMass() != null) group.set("centreOfMass", RenderUtilities.renderBlock(
            spider.getWorld(),
            stance.getCentreOfMass(),
            it -> {
                it.setTeleportDuration(1);
                it.setBrightness(new Display.Brightness(15, 15));

                float size = 0.1f * scale;
                it.setTransformation(Geometry.centredTransform(size, size, size));
            },
            it -> {
                Material material = Geometry.horizontalLength(stance.getSupportAxis()) == .0 ? Material.LAPIS_BLOCK : Material.REDSTONE_BLOCK;
                it.setBlock(material.createBlockData());
            }
        ));


        if (options.getDebug().getNormalForce() && stance.getCentreOfMass() != null && stance.getCenterOfPressure() != null) group.set("acceleration", RenderUtilities.renderLine(
            spider.getWorld(),
            new LineSegment(stance.getCenterOfPressure(), stance.getCentreOfMass()),
            null,
            .02f * scale,
            1,
            it -> it.setBrightness(new Display.Brightness(15, 15)),
            it -> {
                Material material = spider.getContactAcceleration().isZero() ? Material.BLACK_CONCRETE : Material.WHITE_CONCRETE;
                it.setBlock(material.createBlockData());
            }
        ));

        return group;
    }

    private static RenderEntity<BlockDisplay> renderEntity(SpiderBody spider, Quaternionf orientation, Vector direction, float thickness, float length, Material material) {
        Vector3f mTranslation = new Vector3f(-1f, -1f, -1f).add(direction.toVector3f()).mul(.5f);
        Vector3f mScale = new Vector3f(thickness, thickness, thickness).add(direction.toVector3f().mul(length));

        return RenderUtilities.renderBlock(
            spider.getWorld(),
            spider.getPosition(),
            it -> {
                it.setBlock(material.createBlockData());
                it.setTeleportDuration(1);
                it.setInterpolationDuration(1);
            },
            it -> {
                Matrix4f transform = new Matrix4f()
                    .rotate(orientation)
                    .scale(mScale)
                    .translate(mTranslation);

                RenderUtilities.interpolateTransform(it, transform);
            }
        );
    }

    private static Vector upVector(Vector vector) {
        return vector.getX() + vector.getZ() != 0.0 ? Maths.upVector() : Maths.forwardVector();
    }
}
