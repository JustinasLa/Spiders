package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.LegStepEvent;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.MimicPose;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.Triple;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.ecs.Quadruple;
import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;

/**
 * A chest that, when touched, cracks its lid on a row of fangs and red eyes, springs at whoever woke it,
 * unfolds on four cracking legs and hunts them for a while, biting. Then it stops, cocks its head at
 * them, and gallops [Mimic.FLEE_DISTANCE] blocks away to become a chest somewhere else, leaving its
 * prey hearing it behind them for a while.
 *
 * @param threat who it hunts and then flees from, in its own world; defaults to the waker's position.
 */
public class Mimic implements ECSComponent {
    private final MimicPose pose;
    private final UUID waker;
    private final Function<SpiderBody, Vector> threat;
    /** Who it is after: the waker, or whoever last hit it. */
    private UUID prey;
    /** Woken by someone it can go after; console wakes just stand up. */
    private final boolean hunts;

    private MimicState state;
    private int stateTicks = 0;
    /** 0 = sitting as a chest, 1 = standing. */
    private double rise = 0.0;
    private float lidAngle = 0f;

    private int chompTick = -1;
    private int nextChomp = ThreadLocalRandom.current().nextInt(20, 40);
    private int idleTicks = 0;

    private int huntLength = HUNT_TICKS;
    private int biteCooldown = 0;
    private int thrashTicks = 0;
    private int nextCry = ThreadLocalRandom.current().nextInt(30, 60);
    private int nextHeartbeat = 0;
    private int hitCooldown = 0;
    private int bites = 0;

    /** Sudden jerks of the whole chest, decaying each tick, and a slow head tilt while it stares. */
    private float twitchYaw = 0f;
    private float twitchPitch = 0f;
    private float twitchRoll = 0f;
    private float tilt = 0f;

    /** Last place the threat was seen; the mimic keeps running from it if the player leaves. */
    private Vector lastThreat = null;
    /** Turn off the straight escape line, used to get round whatever it's stuck on. */
    private double detour = 0.0;
    private Vector fleeDirection = null;
    private Vector progressCheck = null;
    private int stuckChecks = 0;

    private Block restCell = null;
    private BlockFace restFacing = BlockFace.SOUTH;

    public Mimic(MimicPose pose, UUID waker, Function<SpiderBody, Vector> threat) {
        this.pose = pose;
        this.waker = waker;
        this.prey = waker;
        this.threat = threat;
        this.hunts = waker != null || threat != null;
        this.state = hunts ? MimicState.AMBUSH : MimicState.WAKING;
    }

    public Mimic(MimicPose pose, UUID waker) {
        this(pose, waker, null);
    }

    public MimicPose getPose() {
        return pose;
    }

    public UUID getWaker() {
        return waker;
    }

    public MimicState getState() {
        return state;
    }

    public int getStateTicks() {
        return stateTicks;
    }

    public double getRise() {
        return rise;
    }

    public float getLidAngle() {
        return lidAngle;
    }

    public int getIdleTicks() {
        return idleTicks;
    }

    public int getBites() {
        return bites;
    }

    public Block getRestCell() {
        return restCell;
    }

    public BlockFace getRestFacing() {
        return restFacing;
    }

    private void enter(MimicState state) {
        this.state = state;
        stateTicks = 0;
    }

    /** Pick a spot to sit down in; false if there's nowhere nearby to become a chest. */
    public boolean beginSettling(SpiderBody body) {
        Block cell = MimicChests.restCellNear(body.getWorld(), body.getPosition());
        if (cell == null) return false;
        restCell = cell;
        restFacing = cardinalFacing(body.forwardDirection());
        enter(MimicState.SETTLING);
        return true;
    }

    public void update(ECSEntity entity, SpiderBody body, SpiderOptions options, Locomotion locomotion) {
        stateTicks++;
        if (biteCooldown > 0) biteCooldown--;
        if (hitCooldown > 0) hitCooldown--;
        Mountable mountable = entity.query(Mountable.class);
        Player rider = mountable != null ? mountable.getRider() : null;
        Player victim = victim();
        World world = body.getWorld();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float lidTarget = 0f;
        float lidRate = .3f;
        float sway = (float) Math.sin(Core.getCurrentTick() * .3f) * .25f;
        float stretch = 0f;
        float shiver = 0f;
        float tiltTarget = 0f;
        float thrash = 0f;

        switch (state) {
            case AMBUSH -> {
                rise = 0.0;
                // the lid lifts a crack on its fangs and eyes, and it breathes; then nothing, for a moment
                lidTarget = Maths.toRadians(10f);
                shiver = .012f;
                if (stateTicks == 1) {
                    MimicTerror.play(world, body.getPosition(), MimicTerror.CREAK, .9f, .55f);
                    MimicTerror.play(world, body.getPosition(), MimicTerror.BREATH, 1.2f, .85f);
                }
                if (stateTicks == AMBUSH_TICKS / 2 && victim != null) MimicTerror.heartbeat(victim, 1.0);
                Vector from = threatPosition(body);
                if (from != null && locomotion != null) locomotion.face(towards(body, from), PRIORITY);
                if (stateTicks >= AMBUSH_TICKS) {
                    enter(MimicState.LUNGE);
                    lunge(body, options, from, victim);
                }
            }

            case LUNGE -> {
                rise = 0.0;
                // mouth flung wide, stretched towards its prey, tongue lashing
                lidTarget = Maths.toRadians(85f);
                lidRate = .8f;
                stretch = .2f;
                sway = (float) Math.sin(stateTicks * 1.7f) * .6f;
                if (stateTicks == SECOND_SHRIEK_TICKS) MimicTerror.screech(world, body.getPosition(), 1.15f);
                Vector from = threatPosition(body);
                if (biteCooldown <= 0 && inReach(body, from)) bite(entity, body, victim);
                boolean landed = stateTicks > 3 && body.getLeapTicks() == 0;
                if (landed || stateTicks > LUNGE_MAX_TICKS) {
                    kick(.3f);
                    enter(MimicState.WAKING);
                }
            }

            case WAKING -> {
                double t = (double) stateTicks / WAKE_TICKS;
                if (hunts) {
                    // it unfolds in jerks, a joint snapping at each
                    rise = stutter(t);
                    if (stateTicks % (WAKE_TICKS / 4) == 1 && stateTicks < WAKE_TICKS) {
                        MimicTerror.play(world, body.getPosition(), MimicTerror.CRACK, 1.4f, .8f + random.nextFloat() * .4f);
                        kick(.35f);
                    }
                    shiver = .01f;
                } else {
                    rise = smoothstep(t);
                }
                // the lid bursts open, then relaxes
                lidTarget = stateTicks < 5 ? Maths.toRadians(75f) : Maths.toRadians(35f);
                Vector from = threatPosition(body);
                if (from != null && locomotion != null) locomotion.face(towards(body, from), PRIORITY);
                if (stateTicks >= WAKE_TICKS) {
                    if (from != null && rider == null) {
                        startHunt(world, body, options, HUNT_TICKS);
                    } else {
                        enter(MimicState.AWAKE);
                    }
                }
            }

            case HUNTING -> {
                rise = 1.0;
                Vector seen = liveThreat(body);
                Vector from = threatPosition(body);
                double distance = from != null ? horizontalDistance(body.getPosition(), from) : Double.MAX_VALUE;
                if (rider != null) {
                    Pounce.stopPounce(entity);
                    options.setGallop(false);
                    enter(MimicState.AWAKE);
                } else if (stateTicks > huntLength || seen == null || distance > LOSE_DISTANCE) {
                    Pounce.stopPounce(entity);
                    options.setGallop(false);
                    MimicTerror.play(world, body.getPosition(), MimicTerror.GROWL, 2f, .8f);
                    enter(MimicState.STARING);
                } else {
                    if (thrashTicks > 0) {
                        // shaking its head like a dog with a rat
                        thrashTicks--;
                        thrash = (float) Math.sin(thrashTicks * 1.9) * .5f * thrashTicks / THRASH_TICKS;
                        if (locomotion != null) locomotion.face(towards(body, from), PRIORITY);
                    } else if (entity.query(Pounce.class) == null) {
                        entity.addComponent(new Pounce(() -> liveThreat(body)));
                    }
                    if (biteCooldown <= 0 && inReach(body, from)) bite(entity, body, victim);

                    if (distance < FRENZY_DISTANCE) {
                        // snapping as it closes in
                        int cycle = stateTicks % 6;
                        lidTarget = cycle < 2 ? 0f : Maths.toRadians(80f);
                        lidRate = .9f;
                        if (cycle == 1 && thrashTicks == 0) MimicTerror.chomp(world, mouth(body), .7f);
                    } else {
                        lidTarget = Maths.toRadians(45f + 25f * (float) Math.sin(Core.getCurrentTick() * .9f));
                    }
                    sway = (float) Math.sin(Core.getCurrentTick() * .8f) * .55f;
                    shiver = .008f;
                    if (random.nextFloat() < .04f) kick(.2f);

                    if (--nextCry <= 0) {
                        if (random.nextFloat() < .3f) MimicTerror.screech(world, body.getPosition(), 1.05f + random.nextFloat() * .2f);
                        else MimicTerror.shriek(world, body.getPosition(), .9f + random.nextFloat() * .3f);
                        nextCry = random.nextInt(50, 110);
                    }
                    if (victim != null) {
                        if (--nextHeartbeat <= 0) {
                            MimicTerror.heartbeat(victim, distance);
                            nextHeartbeat = MimicTerror.heartbeatInterval(distance);
                        }
                        if (distance < DARKEN_DISTANCE) MimicTerror.darken(victim);
                    }
                }
            }

            case STARING -> {
                rise = 1.0;
                // it stops dead, cocks its head at them, and slowly lets its mouth close
                Vector from = threatPosition(body);
                if (from != null && locomotion != null) locomotion.face(towards(body, from), PRIORITY);
                tiltTarget = Maths.toRadians(34f);
                lidTarget = Maths.toRadians(40f - 30f * Math.min(1f, (float) stateTicks / STARE_TICKS));
                shiver = .005f;
                if (random.nextFloat() < .07f) kick(.25f);
                if (stateTicks == STARE_TICKS - 10) MimicTerror.play(world, body.getPosition(), MimicTerror.GULP, 1.2f, .7f);
                if (victim != null && stateTicks % 20 == 0) MimicTerror.heartbeat(victim, from != null ? horizontalDistance(body.getPosition(), from) : 20);
                if (stateTicks >= STARE_TICKS) {
                    if (from != null && rider == null) {
                        options.setGallop(true);
                        MimicTerror.shriek(world, body.getPosition(), .75f);
                        // it's gone, but they'll keep hearing it
                        if (victim != null) MimicHaunt.haunt(victim, HAUNT_TICKS);
                        enter(MimicState.FLEEING);
                    } else {
                        enter(MimicState.AWAKE);
                    }
                }
            }

            case FLEEING -> {
                rise = 1.0;
                // the lid flaps as it runs
                lidTarget = Maths.toRadians(30f + 18f * (float) Math.sin(Core.getCurrentTick() * .9f));
                Vector from = threatPosition(body);
                Vector direction = from != null ? escapeDirection(body, from) : null;
                fleeDirection = direction;
                boolean escaped = from == null || horizontalDistance(body.getPosition(), from) >= FLEE_DISTANCE;
                if (rider != null || escaped || direction == null || !groundAheadExists(body, direction)) {
                    fleeDirection = null;
                    options.setGallop(false);
                    if (rider != null || !beginSettling(body)) enter(MimicState.AWAKE);
                } else {
                    if (locomotion != null) locomotion.face(direction, PRIORITY);
                    if (locomotion != null) locomotion.walk(direction.clone().multiply(options.getGait().getMaxSpeed()), PRIORITY);
                    checkProgress(body);
                    // its cries carry back as it goes
                    if (--nextCry <= 0) {
                        MimicTerror.play(world, body.getPosition(), MimicTerror.SHRIEK, 4f, .65f + random.nextFloat() * .15f);
                        nextCry = random.nextInt(60, 140);
                    }
                }
            }

            case AWAKE -> {
                rise = 1.0;
                lidTarget = Maths.toRadians(22f + 4f * (float) Math.sin(Core.getCurrentTick() * .15f));
                if (random.nextFloat() < .015f) kick(.2f);

                idleTicks = body.isWalking() || rider != null ? 0 : idleTicks + 1;
                if (idleTicks >= IDLE_SLEEP_TICKS && !beginSettling(body)) idleTicks = IDLE_SLEEP_TICKS / 2;
            }

            case SETTLING -> {
                rise = 1.0;
                lidTarget = Maths.toRadians(6f);
                if (rider != null) {
                    enter(MimicState.AWAKE);
                } else {
                    boolean aligned = steerToRestCell(body, options, locomotion);
                    if (aligned || stateTicks > SETTLE_TIMEOUT_TICKS) enter(MimicState.CROUCHING);
                }
            }

            case CROUCHING -> {
                rise = 1.0 - smoothstep((double) stateTicks / CROUCH_TICKS);
                lidTarget = 0f;
                steerToRestCell(body, options, locomotion);
                if (stateTicks >= CROUCH_TICKS + 3 && !MimicChests.rest(entity, body, this)) {
                    // the spot was taken while it sat down: stand back up
                    lastThreat = null;
                    enter(MimicState.WAKING);
                }
            }
        }

        applyMimicRise(options, rise);
        lidTarget = updateChomp(body, lidTarget);

        float rate = lidTarget < lidAngle ? Math.max(.45f, lidRate) : lidRate;
        lidAngle = Maths.moveTowards(lidAngle, lidTarget, rate);
        float tongueOut = coerceIn((lidAngle - Maths.toRadians(12f)) / Maths.toRadians(20f), 0f, 1f);

        twitchYaw *= TWITCH_DECAY;
        twitchPitch *= TWITCH_DECAY;
        twitchRoll *= TWITCH_DECAY;
        tilt = Maths.moveTowards(tilt, tiltTarget, Maths.toRadians(2.5f));
        pose.apply(lidAngle, tongueOut, sway, bodyPose(stretch, shiver, thrash));
        drool(body);
    }

    /** Snap shut and spring open again every so often while awake. */
    private float updateChomp(SpiderBody body, float lidTarget) {
        if (chompTick < 0) {
            if (state != MimicState.AWAKE || --nextChomp > 0) return lidTarget;
            chompTick = 0;
            nextChomp = ThreadLocalRandom.current().nextInt(40, 120);
        }
        chompTick++;
        if (chompTick == 3) MimicTerror.chomp(body.getWorld(), mouth(body), .6f);
        if (chompTick > 8) chompTick = -1;
        return chompTick <= 3 ? 0f : Maths.toRadians(55f);
    }

    /** Hit by someone: a scream of pain, and if they're fair game it turns on them. */
    public void onHit(ECSEntity entity, SpiderBody body, Player attacker) {
        if (hitCooldown > 0) return;
        hitCooldown = 12;
        kick(.4f);
        MimicTerror.shriek(body.getWorld(), body.getPosition(), .7f + ThreadLocalRandom.current().nextFloat() * .2f);
        if (threat != null || !MimicTerror.isPrey(attacker)) return;
        if (state != MimicState.AWAKE && state != MimicState.FLEEING && state != MimicState.SETTLING && state != MimicState.STARING) return;
        Mountable mountable = entity.query(Mountable.class);
        if (mountable != null && mountable.getRider() != null) return;
        SpiderOptions options = entity.query(SpiderOptions.class);
        if (options == null) return;
        prey = attacker.getUniqueId();
        lastThreat = null;
        fleeDirection = null;
        startHunt(body.getWorld(), body, options, HUNT_TICKS / 2);
    }

    private void startHunt(World world, SpiderBody body, SpiderOptions options, int ticks) {
        options.setGallop(true);
        huntLength = ticks;
        tilt = 0f;
        MimicTerror.screech(world, body.getPosition(), 1f);
        enter(MimicState.HUNTING);
    }

    /** Springs from the floor at its prey's face, still folded up as a chest. */
    private void lunge(SpiderBody body, SpiderOptions options, Vector from, Player victim) {
        World world = body.getWorld();
        Vector centre = body.getPosition();
        MimicTerror.screech(world, centre, 1f);
        MimicTerror.play(world, centre, MimicTerror.CHOMP, 1.5f, .7f);
        world.spawnParticle(Particle.BLOCK, centre.toLocation(world), 30, .35, .3, .35, 0.0, chestBlock());
        if (victim != null && victim.getEyeLocation().toVector().distance(centre) < JUMP_SCARE_DISTANCE) {
            // their head is wrenched round to look straight into its mouth as it comes
            MimicTerror.stareInto(victim, centre);
            MimicTerror.jumpScare(victim);
        }
        if (from == null) return;

        Vector direction = towards(body, from);
        double distance = horizontalDistance(centre, from);
        double gravity = options.getGait().getGravityAcceleration();
        int flight = (int) Math.max(4, Math.round(2 * LUNGE_LIFT / gravity));
        double reach = coerceIn(distance - .9, .4, LUNGE_RANGE);
        body.leap(direction.multiply(reach / flight).add(new Vector(0.0, LUNGE_LIFT, 0.0)), flight);
    }

    private void bite(ECSEntity entity, SpiderBody body, Player victim) {
        biteCooldown = BITE_COOLDOWN;
        thrashTicks = THRASH_TICKS;
        bites++;
        Pounce.stopPounce(entity);
        kick(.25f);
        if (victim != null) MimicTerror.bite(victim, mouth(body), BITE_DAMAGE);
        else MimicTerror.chomp(body.getWorld(), mouth(body), 1.5f);
    }

    /** A sudden jerk of the whole chest, in a random direction. */
    private void kick(float size) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        twitchYaw += (random.nextFloat() * 2 - 1) * size;
        twitchPitch += (random.nextFloat() * 2 - 1) * size * .6f;
        twitchRoll += (random.nextFloat() * 2 - 1) * size * .8f;
    }

    /** Shiver, twitches, the stare's head tilt, a thrash, a lunge's stretch and slow breathing, about the chest's centre. */
    private Matrix4f bodyPose(float stretch, float shiver, float thrash) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        float breathe = 1f + .018f * (float) Math.sin(Core.getCurrentTick() * .11f);
        Matrix4f transform = new Matrix4f();
        if (shiver > 0) transform.translate((random.nextFloat() * 2 - 1) * shiver, (random.nextFloat() * 2 - 1) * shiver * .5f, (random.nextFloat() * 2 - 1) * shiver);
        transform.rotateY(twitchYaw + thrash).rotateX(twitchPitch).rotateZ(twitchRoll + tilt);
        float squash = breathe / (float) Math.sqrt(1 + stretch);
        return transform.scale(squash, squash, breathe * (1 + stretch));
    }

    private static BlockData blood;
    private static BlockData chest;

    private static BlockData chestBlock() {
        if (chest == null) chest = Material.CHEST.createBlockData();
        return chest;
    }

    /** Blood-red drool from the lip while the mouth hangs open. */
    private void drool(SpiderBody body) {
        if (lidAngle < Maths.toRadians(25f) || state == MimicState.SETTLING || state == MimicState.CROUCHING) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (random.nextInt(3) != 0) return;
        if (blood == null) blood = Material.REDSTONE_BLOCK.createBlockData();
        Vector forward = body.forwardDirection();
        Vector up = Maths.rotate(Maths.upVector(), body.getOrientation());
        Vector side = forward.clone().crossProduct(up);
        Vector lip = body.getPosition().clone()
            .add(forward.multiply(MimicShape.HALF))
            .add(up.multiply(MimicShape.MOUTH))
            .add(side.multiply((random.nextDouble() * 2 - 1) * MimicShape.HALF));
        body.getWorld().spawnParticle(Particle.FALLING_DUST, lip.toLocation(body.getWorld()), 1, 0.0, 0.0, 0.0, 0.0, blood);
    }

    /** Just in front of the lid. */
    private static Vector mouth(SpiderBody body) {
        return body.getPosition().clone().add(body.forwardDirection().multiply(MimicShape.HALF + .1));
    }

    /** Close enough to bite: the prey's feet are given, so aim at their middle. */
    private static boolean inReach(SpiderBody body, Vector target) {
        if (target == null) return false;
        return horizontalDistance(mouth(body), target) < BITE_REACH && Math.abs(target.getY() + .9 - body.getPosition().getY()) < 1.9;
    }

    /** Along the ground towards {@code target}, or its own heading if it's right on top of it. */
    private static Vector towards(SpiderBody body, Vector target) {
        Vector toward = target.clone().subtract(body.getPosition()).setY(0);
        if (toward.lengthSquared() < 1e-6) toward = body.forwardDirection().setY(0);
        if (toward.lengthSquared() < 1e-6) toward = new Vector(0, 0, 1);
        return toward.normalize();
    }

    /** Its prey, if online and fair game. */
    private Player victim() {
        Player player = prey != null ? Bukkit.getPlayer(prey) : null;
        return MimicTerror.isPrey(player) ? player : null;
    }

    /** Where its prey is right now, or null if they're gone. */
    private Vector liveThreat(SpiderBody body) {
        if (threat != null) return threat.apply(body);
        Player player = victim();
        return player != null && Objects.equals(player.getWorld(), body.getWorld()) ? player.getLocation().toVector() : null;
    }

    private Vector threatPosition(SpiderBody body) {
        Vector seen = liveThreat(body);
        if (seen != null) lastThreat = seen.clone();
        return lastThreat;
    }

    /** Straight away from the threat across the ground, turned by [detour] while working round an obstacle. */
    private Vector escapeDirection(SpiderBody body, Vector from) {
        Vector away = body.getPosition().clone().subtract(from).setY(0);
        if (away.lengthSquared() < 1e-6) away.copy(body.forwardDirection().setY(0));
        if (away.lengthSquared() < 1e-6) away.copy(new Vector(0, 0, 1));
        return away.normalize().rotateAroundY(detour);
    }

    /** Every two seconds: if it has barely moved, try a wider detour, alternating sides. */
    private void checkProgress(SpiderBody body) {
        if (stateTicks % PROGRESS_CHECK_TICKS != 0) return;
        Vector previous = progressCheck;
        progressCheck = body.getPosition().clone();
        if (previous == null) return;
        if (horizontalDistance(previous, body.getPosition()) < MIN_PROGRESS) {
            stuckChecks++;
            int side = stuckChecks % 2 == 1 ? 1 : -1;
            detour = side * DETOUR_STEP * Math.min((stuckChecks + 1) / 2, 3);
        } else if (stuckChecks > 0) {
            stuckChecks = 0;
            detour = 0.0;
        }
    }

    private Vector pointAhead(SpiderBody body, Vector direction) {
        return body.getPosition().clone().add(direction.clone().multiply(LOOK_AHEAD));
    }

    /** It runs on through chunks the plugin keeps loaded for it, but never into terrain that hasn't been generated. */
    private boolean groundAheadExists(SpiderBody body, Vector direction) {
        Vector ahead = pointAhead(body, direction);
        return body.getWorld().isChunkGenerated(((int) Math.floor(ahead.getX())) >> 4, ((int) Math.floor(ahead.getZ())) >> 4);
    }

    /** Chunks to keep loaded: the one it stands in and, while fleeing, the one it's running into. */
    public List<Long> wantedChunks(SpiderBody body) {
        List<Long> chunks = new ArrayList<>();
        chunks.add(chunkKey(body.getPosition()));
        Vector direction = fleeDirection;
        if (state == MimicState.FLEEING && direction != null && groundAheadExists(body, direction)) {
            chunks.add(chunkKey(pointAhead(body, direction)));
        }
        return chunks;
    }

    /** Walk onto the middle of the rest cell and turn square to it. True once lined up. */
    private boolean steerToRestCell(SpiderBody body, SpiderOptions options, Locomotion locomotion) {
        Block cell = restCell;
        if (cell == null) return true;
        Vector offset = new Vector(cell.getX() + .5 - body.getPosition().getX(), 0.0, cell.getZ() + .5 - body.getPosition().getZ());
        double distance = offset.length();
        double speed = Math.min(distance * .4, options.getGait().getMaxSpeed() * .6);
        if (locomotion != null) locomotion.walk(distance > 1e-3 ? offset.normalize().multiply(speed) : offset, SETTLE_PRIORITY);
        if (locomotion != null) locomotion.face(restFacing.getDirection(), SETTLE_PRIORITY);

        Vector forward = body.forwardDirection().setY(0);
        boolean square = forward.lengthSquared() > 1e-6 && forward.normalize().dot(restFacing.getDirection()) > (float) Math.cos(Maths.toRadians(5f));
        return distance < .1 && square;
    }

    /** The lid cracks and it breathes for this long before it springs. */
    public static final int AMBUSH_TICKS = 12;
    public static final int LUNGE_MAX_TICKS = 25;
    public static final double LUNGE_LIFT = .3;
    /** Furthest the lunge carries it towards its prey. */
    public static final double LUNGE_RANGE = 3.0;
    /** The waker sees the jump scare if their eyes are this close when it springs. */
    public static final double JUMP_SCARE_DISTANCE = 10.0;
    public static final int SECOND_SHRIEK_TICKS = 6;
    public static final int WAKE_TICKS = 16;
    public static final int CROUCH_TICKS = 14;
    public static final int HUNT_TICKS = 20 * 10;
    public static final int STARE_TICKS = 50;
    /** It gives up the hunt if its prey gets this far away. */
    public static final double LOSE_DISTANCE = 48.0;
    public static final double FRENZY_DISTANCE = 7.0;
    public static final double DARKEN_DISTANCE = 24.0;
    public static final double BITE_REACH = 1.5;
    /** Before armour; a bite never takes the last heart. */
    public static final double BITE_DAMAGE = 3.0;
    public static final int BITE_COOLDOWN = 26;
    public static final int THRASH_TICKS = 12;
    /** How long its prey keeps hearing it after it runs. */
    public static final int HAUNT_TICKS = 20 * 120;
    public static final float TWITCH_DECAY = .72f;
    public static final double FLEE_DISTANCE = 250.0;
    public static final int PROGRESS_CHECK_TICKS = 40;
    public static final double MIN_PROGRESS = 1.5;
    public static final double DETOUR_STEP = Math.PI / 4;
    public static final double LOOK_AHEAD = 12.0;
    public static final int IDLE_SLEEP_TICKS = 20 * 20;
    public static final int SETTLE_TIMEOUT_TICKS = 20 * 4;
    /** Above default steering, below staff tools, riders and pounces. */
    public static final int PRIORITY = 5;
    public static final int SETTLE_PRIORITY = 15;

    /** Body height follows [rise]: chest-on-the-floor at 0, standing at 1. */
    public static void applyMimicRise(SpiderOptions options, double rise) {
        double resting = MimicShape.RESTING_HEIGHT;
        options.getWalkGait().getStationary().setBodyHeight(Maths.lerp(resting, MimicShape.STANDING_HEIGHT, rise));
        options.getWalkGait().getMoving().setBodyHeight(Maths.lerp(resting, MimicShape.STANDING_HEIGHT, rise));
        options.getGallopGait().getStationary().setBodyHeight(Maths.lerp(resting, MimicShape.STANDING_HEIGHT, rise));
        options.getGallopGait().getMoving().setBodyHeight(Maths.lerp(resting, MimicShape.GALLOP_HEIGHT, rise));
    }

    private static long chunkKey(Vector position) {
        int x = ((int) Math.floor(position.getX())) >> 4;
        int z = ((int) Math.floor(position.getZ())) >> 4;
        return (((long) x) << 32) | (((long) z) & 0xffffffffL);
    }

    /** Plugin chunk tickets held for running mimics, so they aren't stranded where no player is. */
    private static final class MimicChunkTickets {
        private MimicChunkTickets() {
        }

        private static final Map<World, Set<Long>> held = new LinkedHashMap<>();

        static void sync(ECS app) {
            Map<World, Set<Long>> wanted = new LinkedHashMap<>();
            for (Triple<ECSEntity, SpiderBody, Mimic> row : app.query(ECSEntity.class, SpiderBody.class, Mimic.class)) {
                if (row.first().getScheduledForRemoval()) continue;
                SpiderBody body = row.second();
                wanted.computeIfAbsent(body.getWorld(), world -> new LinkedHashSet<>()).addAll(row.third().wantedChunks(body));
            }
            Set<World> worlds = new LinkedHashSet<>(held.keySet());
            worlds.addAll(wanted.keySet());
            for (World world : worlds) {
                Set<Long> have = held.computeIfAbsent(world, w -> new LinkedHashSet<>());
                Set<Long> want = wanted.getOrDefault(world, Set.of());
                Set<Long> toAdd = new LinkedHashSet<>(want);
                toAdd.removeAll(have);
                for (long key : toAdd) world.addPluginChunkTicket((int) (key >> 32), (int) key, Core.getCurrentPlugin());
                Set<Long> toRemove = new LinkedHashSet<>(have);
                toRemove.removeAll(want);
                for (long key : toRemove) world.removePluginChunkTicket((int) (key >> 32), (int) key, Core.getCurrentPlugin());
                have.retainAll(want);
                have.addAll(want);
            }
        }
    }

    private static double horizontalDistance(Vector a, Vector b) {
        return new Vector(a.getX() - b.getX(), 0.0, a.getZ() - b.getZ()).length();
    }

    /** Stands up in four snaps rather than one smooth rise. */
    public static double stutter(double t) {
        double x = coerceIn(t, 0.0, 1.0) * 4;
        int step = (int) Math.min(3, Math.floor(x));
        return (step + smoothstep((x - step) * 2.5)) / 4;
    }

    public static double smoothstep(double t) {
        double x = coerceIn(t, 0.0, 1.0);
        return x * x * (3 - 2 * x);
    }

    public static BlockFace cardinalFacing(Vector forward) {
        BlockFace best = null;
        double bestValue = 0.0;
        for (BlockFace face : List.of(BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST)) {
            double value = face.getDirection().dot(forward);
            if (best == null || Double.compare(bestValue, value) < 0) {
                best = face;
                bestValue = value;
            }
        }
        return best;
    }

    public static void setupMimic(ECS app) {
        Events.addEventListener(new MimicChestListener());
        MimicLurk.setup();
        MimicHaunt.setup();

        Core.onPluginShutdown(() -> {
            for (Pair<ECSEntity, SpiderBody> row : app.query(ECSEntity.class, SpiderBody.class)) {
                ECSEntity entity = row.first();
                SpiderBody body = row.second();
                if (entity.getScheduledForRemoval() || entity.query(Mimic.class) == null) continue;
                if (!MimicChests.restNow(body)) Core.getCurrentPlugin().getLogger().warning(
                    "A waking mimic at " + body.getPosition().getBlockX() + ", " + body.getPosition().getBlockY() + ", " + body.getPosition().getBlockZ() + " had no room to become a chest.");
            }
        });

        // hit it, and it screams; hit it while it isn't busy, and it comes for you
        app.onEvent(SpiderHitEvent.class, event -> {
            Mimic mimic = event.getEntity().query(Mimic.class);
            if (mimic == null) return;
            Player attacker = event.getSource() instanceof Player player ? player
                : event.getSource() instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter ? shooter : null;
            mimic.onHit(event.getEntity(), event.getSpider(), attacker);
        });

        // wooden legs drumming the ground at a gallop
        app.onEvent(LegStepEvent.class, event -> {
            if (event.getEntity().query(Mimic.class) == null) return;
            SpiderOptions options = event.getEntity().query(SpiderOptions.class);
            if (options == null || !options.getGallop()) return;
            MimicTerror.play(event.getSpider().getWorld(), event.getLeg().getEndEffector(), MimicTerror.SKITTER, .5f, .85f + ThreadLocalRandom.current().nextFloat() * .3f);
        });

        app.onTick(ecs -> {
            for (Quadruple<ECSEntity, SpiderBody, SpiderOptions, Mimic> row : app.query(ECSEntity.class, SpiderBody.class, SpiderOptions.class, Mimic.class)) {
                ECSEntity entity = row.first();
                if (entity.getScheduledForRemoval()) continue;
                row.fourth().update(entity, row.second(), row.third(), entity.query(Locomotion.class));
            }
            MimicChunkTickets.sync(app);
        });
    }

    // Kotlin's coerceIn (keeps -0.0 and NaN as they are, unlike Math.clamp)
    private static float coerceIn(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private static double coerceIn(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }
}
