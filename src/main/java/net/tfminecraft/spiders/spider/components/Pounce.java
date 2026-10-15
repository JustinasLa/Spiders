package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.Gait;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.ecs.Quintuple;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;

import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * Gallop at a target, then leap at it. [target] returns null once the target is gone;
 * [onEnd] hears how it ended and, after a landing, how far from the target it came down.
 */
public class Pounce implements ECSComponent {
    public enum Phase { CHASE, AIRBORNE }
    public enum Outcome { LANDED, TARGET_LOST, GAVE_UP, STOPPED }

    private final Supplier<Vector> target;
    private final BiConsumer<Outcome, Double> onEnd;

    private Phase phase = Phase.CHASE;
    private int ticks = 0;
    /** Gallop was off before the pounce and is switched off again afterwards. */
    private boolean restoreWalk = false;
    private Vector lastTarget = null;

    public Pounce(Supplier<Vector> target, BiConsumer<Outcome, Double> onEnd) {
        this.target = target;
        this.onEnd = onEnd;
    }

    public Pounce(Supplier<Vector> target) {
        this(target, (outcome, landedDistance) -> { });
    }

    public Supplier<Vector> getTarget() {
        return target;
    }

    public BiConsumer<Outcome, Double> getOnEnd() {
        return onEnd;
    }

    public Phase getPhase() {
        return phase;
    }

    public int getTicks() {
        return ticks;
    }

    public boolean getRestoreWalk() {
        return restoreWalk;
    }

    /** Advances one tick; returns how the pounce ended, or null while it goes on. */
    public Outcome update(SpiderBody body, SpiderOptions options, Locomotion locomotion) {
        ticks++;
        return switch (phase) {
            case CHASE -> chase(body, options, locomotion);
            case AIRBORNE -> body.getLeapTicks() > 0 || !hasLanded(body) && ticks < MAX_LANDING_TICKS ? null : Outcome.LANDED;
        };
    }

    private Outcome chase(SpiderBody body, SpiderOptions options, Locomotion locomotion) {
        Vector target = this.target.get();
        if (target == null) return Outcome.TARGET_LOST;
        lastTarget = target;
        if (ticks > options.getPounce().getGiveUpTicks()) return Outcome.GAVE_UP;

        if (!options.getGallop()) {
            options.setGallop(true);
            restoreWalk = true;
        }

        // ground plane: the floor, or the wall or ceiling a climbing spider is on
        Vector up = options.getClimbing() ? Maths.rotate(Maths.upVector(), body.getPreferredOrientation()) : Maths.upVector();
        Vector offset = target.clone().subtract(body.getPosition());
        Vector along = offset.subtract(up.clone().multiply(offset.dot(up)));
        double distance = along.length();
        Vector direction = distance > 1e-6 ? along.multiply(1 / distance) : body.forwardDirection();

        Gait gait = options.getGallopGait();
        double scale = options.getBodyPlan().getScale();
        double lift = options.getPounce().getLeapLift() * Math.sqrt(scale);
        Flight flight = Flight.of(lift, gait);
        double leapSpeed = gait.getMaxSpeed() * options.getPounce().getLeapBoost();
        double range = Math.min(options.getPounce().getLeapRange() * scale, leapSpeed * flight.reachPerSpeed);

        boolean footing = groundedLegs(body) * 2 >= body.getLegs().size();
        if (footing && distance <= range) {
            // land on the target rather than overshoot it
            double speed = distance / flight.reachPerSpeed;
            body.leap(direction.clone().multiply(speed).add(up.clone().multiply(lift)), flight.ticks);
            phase = Phase.AIRBORNE;
            ticks = 0;
            return null;
        }

        locomotion.face(direction, Locomotion.POUNCE_PRIORITY);
        locomotion.walk(direction.clone().multiply(gait.getMaxSpeed()), Locomotion.POUNCE_PRIORITY);
        return null;
    }

    private static int groundedLegs(SpiderBody body) {
        int count = 0;
        for (Leg leg : body.getLegs()) if (leg.isGrounded()) count++;
        return count;
    }

    private boolean hasLanded(SpiderBody body) {
        return body.getOnGround() || groundedLegs(body) * 2 >= body.getLegs().size();
    }

    /** Put the gait back and report how it ended. */
    public void finish(SpiderOptions options, SpiderBody body, Outcome outcome) {
        if (restoreWalk && options != null) options.setGallop(false);
        restoreWalk = false;
        Double landedDistance = null;
        if (outcome == Outcome.LANDED && body != null) {
            Vector current = target.get();
            Vector it = current != null ? current : lastTarget;
            if (it != null) landedDistance = new Vector(it.getX() - body.getPosition().getX(), 0.0, it.getZ() - body.getPosition().getZ()).length();
        }
        onEnd.accept(outcome, landedDistance);
    }

    /** A leap launched with [lift], stepped like [SpiderBody] steps it (gravity, then air drag). */
    private static final class Flight {
        final int ticks;
        final double reachPerSpeed;

        Flight(int ticks, double reachPerSpeed) {
            this.ticks = ticks;
            this.reachPerSpeed = reachPerSpeed;
        }

        static Flight of(double lift, Gait gait) {
            double keep = 1 - gait.getAirDragCoefficient();
            double rise = lift;
            double height = 0.0;
            double speed = 1.0;
            double reach = 0.0;
            int ticks = 0;
            // until it falls back to the height it left from
            while (ticks < MAX_FLIGHT_TICKS) {
                rise = (rise - gait.getGravityAcceleration()) * keep;
                speed *= keep;
                height += rise;
                reach += speed;
                ticks++;
                if (height <= 0) break;
            }
            return new Flight(ticks, reach);
        }
    }

    public static final int MAX_LANDING_TICKS = 100;
    public static final int MAX_FLIGHT_TICKS = 200;

    public static void stopPounce(ECSEntity entity, Outcome outcome) {
        Pounce pounce = entity.query(Pounce.class);
        if (pounce == null) return;
        entity.removeComponent(Pounce.class);
        pounce.finish(entity.query(SpiderOptions.class), entity.query(SpiderBody.class), outcome);
    }

    public static void stopPounce(ECSEntity entity) {
        stopPounce(entity, Outcome.STOPPED);
    }

    public static void setupPounce(ECS app) {
        app.onTick(ecs -> {
            for (Quintuple<ECSEntity, SpiderBody, SpiderOptions, Locomotion, Pounce> row : app.query(ECSEntity.class, SpiderBody.class, SpiderOptions.class, Locomotion.class, Pounce.class)) {
                Outcome outcome = row.fifth().update(row.second(), row.third(), row.fourth());
                if (outcome != null) stopPounce(row.first(), outcome);
            }
        });
    }
}
