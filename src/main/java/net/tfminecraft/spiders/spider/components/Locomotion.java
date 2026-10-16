package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.ecs.Quadruple;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public class Locomotion implements ECSComponent {
    public static final int DEFAULT_PRIORITY = 0;
    public static final int LASER_PRIORITY = 10;
    public static final int RIDER_PRIORITY = 20;
    public static final int POUNCE_PRIORITY = 30;

    private Vector walkVelocity = null;
    private Vector faceDirection = null;
    private Vector pushVelocity = null;

    private int walkPriority = Integer.MIN_VALUE;
    private int facePriority = Integer.MIN_VALUE;

    public Vector getWalkVelocity() {
        return walkVelocity;
    }

    public Vector getFaceDirection() {
        return faceDirection;
    }

    public Vector getPushVelocity() {
        return pushVelocity;
    }

    public void walk(Vector velocity, int priority) {
        if (priority < walkPriority) return;
        walkPriority = priority;
        walkVelocity = velocity.clone();
    }

    public void walk(Vector velocity) {
        walk(velocity, DEFAULT_PRIORITY);
    }

    public void face(Vector direction, int priority) {
        if (priority < facePriority) return;
        facePriority = priority;
        faceDirection = direction.clone();
    }

    public void face(Vector direction) {
        face(direction, DEFAULT_PRIORITY);
    }

    public void push(Vector velocity) {
        Vector accumulated = pushVelocity;
        if (accumulated == null) pushVelocity = velocity.clone();
        else accumulated.add(velocity);
    }

    public void clear() {
        walkVelocity = null;
        faceDirection = null;
        pushVelocity = null;
        walkPriority = Integer.MIN_VALUE;
        facePriority = Integer.MIN_VALUE;
    }

    public static void setupLocomotion(ECS app) {
        app.onPostTick(ecs -> {
            for (Quadruple<ECSEntity, SpiderBody, SpiderOptions, Locomotion> it : app.query(ECSEntity.class, SpiderBody.class, SpiderOptions.class, Locomotion.class)) {
                ECSEntity entity = it.first();
                SpiderBody spider = it.second();
                SpiderOptions options = it.third();
                Locomotion locomotion = it.fourth();

                HitDetector hitDetector = entity.query(HitDetector.class);

                Vector targetVelocity = new Vector(0.0, 0.0, 0.0);
                Vector walkVelocity = locomotion.getWalkVelocity();
                if (walkVelocity != null) targetVelocity.add(walkVelocity);
                Vector pushVelocity = locomotion.getPushVelocity();
                if (pushVelocity != null) targetVelocity.add(pushVelocity);

                // no steering mid-air
                if (spider.getLeapTicks() == 0) walkAt(spider, targetVelocity, hitDetector, options.getGait());

                Vector faceDirection = locomotion.getFaceDirection();
                rotateTowards(spider, faceDirection != null ? faceDirection : Maths.rotate(Maths.forwardVector(), spider.getPreferredOrientation()), options.getGait());

                locomotion.clear();
            }
        });
    }

    public static void rotateTowards(SpiderBody spider, Vector targetVector, Gait gait) {
        // no direction to face (e.g. standing on the point it was sent to): keep the current heading
        if (!(targetVector.lengthSquared() > 1e-12) || !Double.isFinite(targetVector.lengthSquared())) {
            targetVector = Maths.rotate(Maths.forwardVector(), spider.getPreferredOrientation());
        }
        Quaternionf facing = new Quaternionf().rotationTo(Maths.forwardVector().toVector3f(), targetVector.toVector3f());

        Vector3f relativeEuler = Geometry.getYXZRelative(facing, spider.getPreferredOrientation());

        // clamp pitch
        relativeEuler.x = Maths.clamp(relativeEuler.x, -gait.getPreferredPitchLeeway(), gait.getPreferredPitchLeeway());

        // clamp roll
        relativeEuler.z = 0f;

        // clamp yaw if uncomfortable
        boolean anyUncomfortable = false;
        for (Leg it : spider.getLegs()) {
            if (it.isUncomfortable() && !it.isMoving()) {
                anyUncomfortable = true;
                break;
            }
        }
        if (anyUncomfortable) relativeEuler.y = 0f;

        Quaternionf targetOrientation = new Quaternionf(spider.getPreferredOrientation())
            .mul(new Quaternionf().rotationYXZ(relativeEuler.y, relativeEuler.x, relativeEuler.z));

        // smooth target
        Quaternionf easedTarget = new Quaternionf(spider.getOrientation()).slerp(targetOrientation, 1f - gait.getRotationLerp());

        // world-space delta → angular velocity (premultiplied: orientation = Rot(ω) * orientation)
        Quaternionf desiredDelta = new Quaternionf(easedTarget).mul(new Quaternionf(spider.getOrientation()).invert());
        AxisAngle4f axisAngle = new AxisAngle4f().set(desiredDelta);
        Vector3f desiredOmega;
        if (axisAngle.angle < 1e-8f) {
            desiredOmega = new Vector3f();
        } else {
            desiredOmega = new Vector3f(axisAngle.x, axisAngle.y, axisAngle.z).mul(axisAngle.angle);
        }

        spider.setRotatingYaw(desiredOmega.lengthSquared() > 0.001f * 0.001f);

        int groundedLegs = 0;
        for (Leg it : spider.getLegs()) if (it.isGrounded()) groundedLegs++;
        float maxAcceleration = gait.getRotateAcceleration() * groundedLegs / spider.getLegs().size();
        Maths.moveTowards(spider.getRotationalVelocity(), desiredOmega, maxAcceleration);
    }

    public static void walkAt(SpiderBody spider, Vector targetVelocity, HitDetector hitDetector, Gait gait) {
        double acceleration = gait.getMoveAcceleration();// * body.legs.filter { it.isGrounded() }.size / body.legs.size

        // preserve up motion, steer only within the perpendicular plane
        Vector up = Maths.rotate(Maths.upVector(), spider.getPreferredOrientation());
        Vector target = targetVelocity.clone()
            .subtract(up.clone().multiply(targetVelocity.dot(up)))
            .add(up.clone().multiply(spider.getVelocity().dot(up)));

        boolean isUncomfortable = false; //  && !it.targetOutsideComfortZone
        for (Leg it : spider.getLegs()) {
            if (it.isUncomfortable() && !it.isMoving()) {
                isUncomfortable = true;
                break;
            }
        }
        if (isUncomfortable) {
            Maths.moveTowards(spider.getVelocity(), target.clone().multiply(gait.getUncomfortableSpeedMultiplier()), acceleration);

            Vector intent = targetVelocity.clone().subtract(up.clone().multiply(targetVelocity.dot(up)));
            spider.setWalking(intent.lengthSquared() > 1e-8);
        } else {
            Maths.moveTowards(spider.getVelocity(), target, acceleration);

            Vector motion = spider.getVelocity().clone().subtract(up.clone().multiply(spider.getVelocity().dot(up)));
            spider.setWalking(motion.lengthSquared() > 1e-8);
        }

        if (hitDetector != null && hitDetector.getStunned() && targetVelocity.isZero()) spider.setWalking(false);
    }

}
