package net.tfminecraft.spiders.spider.components;

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
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

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
 * A chest that stands up when touched, screeches, gallops [Mimic.FLEE_DISTANCE] blocks away from
 * whoever woke it, then settles back into a chest.
 *
 * @param threat where the mimic is fleeing from, in its own world; defaults to the waker's position.
 */
public class Mimic implements ECSComponent {
    private final MimicPose pose;
    private final UUID waker;
    private final Function<SpiderBody, Vector> threat;

    private MimicState state = MimicState.WAKING;
    private int stateTicks = 0;
    /** 0 = sitting as a chest, 1 = standing. */
    private double rise = 0.0;
    private float lidAngle = 0f;

    private int chompTick = -1;
    private int nextChomp = ThreadLocalRandom.current().nextInt(20, 40);
    private int idleTicks = 0;

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
        this.threat = threat;
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
        Mountable mountable = entity.query(Mountable.class);
        Player rider = mountable != null ? mountable.getRider() : null;
        float lidTarget = 0f;

        switch (state) {
            case WAKING -> {
                rise = smoothstep((double) stateTicks / WAKE_TICKS);
                // the lid bursts open, then relaxes
                lidTarget = stateTicks < 5 ? Maths.toRadians(75f) : Maths.toRadians(35f);
                // already turning its back on whoever woke it
                Vector threatPosition = threatPosition(body);
                if (threatPosition != null && locomotion != null) locomotion.face(escapeDirection(body, threatPosition), PRIORITY);
                if (stateTicks >= WAKE_TICKS) {
                    if (threatPosition(body) != null && rider == null) {
                        options.setGallop(true);
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
                }
            }

            case AWAKE -> {
                rise = 1.0;
                lidTarget = Maths.toRadians(22f + 4f * (float) Math.sin(Core.getCurrentTick() * .15f));

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

        float rate = lidTarget < lidAngle ? .45f : .3f;
        lidAngle = Maths.moveTowards(lidAngle, lidTarget, rate);
        float tongueOut = coerceIn((lidAngle - Maths.toRadians(12f)) / Maths.toRadians(20f), 0f, 1f);
        pose.apply(lidAngle, tongueOut, (float) Math.sin(Core.getCurrentTick() * .3f) * .25f);
    }

    /** Snap shut and spring open again every so often while awake. */
    private float updateChomp(SpiderBody body, float lidTarget) {
        if (chompTick < 0) {
            if (state != MimicState.AWAKE || --nextChomp > 0) return lidTarget;
            chompTick = 0;
            nextChomp = ThreadLocalRandom.current().nextInt(40, 120);
        }
        chompTick++;
        if (chompTick == 3) {
            Overloads.playSound(body.getWorld(), body.getPosition(), Sound.BLOCK_CHEST_CLOSE, .6f, 1.3f);
            Overloads.playSound(body.getWorld(), body.getPosition(), Sound.ENTITY_EVOKER_FANGS_ATTACK, .25f, 1.6f);
        }
        if (chompTick > 8) chompTick = -1;
        return chompTick <= 3 ? 0f : Maths.toRadians(55f);
    }

    private Vector threatPosition(SpiderBody body) {
        Vector seen;
        if (threat != null) {
            seen = threat.apply(body);
        } else {
            Player player = waker != null ? Bukkit.getPlayer(waker) : null;
            seen = player != null && Objects.equals(player.getWorld(), body.getWorld()) && !player.isDead() ? player.getLocation().toVector() : null;
        }
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

    public static final int WAKE_TICKS = 14;
    public static final int CROUCH_TICKS = 14;
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

        Core.onPluginShutdown(() -> {
            for (Pair<ECSEntity, SpiderBody> row : app.query(ECSEntity.class, SpiderBody.class)) {
                ECSEntity entity = row.first();
                SpiderBody body = row.second();
                if (entity.getScheduledForRemoval() || entity.query(Mimic.class) == null) continue;
                if (!MimicChests.restNow(body)) Core.getCurrentPlugin().getLogger().warning(
                    "A waking mimic at " + body.getPosition().getBlockX() + ", " + body.getPosition().getBlockY() + ", " + body.getPosition().getBlockZ() + " had no room to become a chest.");
            }
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
