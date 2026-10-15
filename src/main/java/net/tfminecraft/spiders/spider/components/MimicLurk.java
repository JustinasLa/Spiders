package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.utilities.Core;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Dormant mimics aren't quite still. Near one you may hear it breathe; turn your back and it turns to
 * face you; it lifts its lid on a mouthful of teeth and red eyes while you look away, and snaps shut
 * when you look back. Stand beside it long enough and it takes you.
 */
final class MimicLurk {
    private MimicLurk() {
    }

    private static final int TICKS = 5;
    private static final int SCAN_TICKS = 40;
    private static final double RANGE = 12.0;
    private static final double TURN_RANGE = 10.0;
    private static final double BREATH_RANGE = 6.0;
    private static final double PEEK_RANGE = 6.0;
    private static final double LINGER_RANGE = 2.2;
    private static final int LINGER_TICKS = 20 * 8;
    private static final int PEEK_MAX_TICKS = 20 * 3;
    /** The vanilla lid takes about this long to swing shut. */
    private static final int CLOSE_TICKS = 12;

    private static final class Lurker {
        final Block block;
        int turnCooldown = 40;
        int breathCooldown = ThreadLocalRandom.current().nextInt(20, 100);
        int peekCooldown = ThreadLocalRandom.current().nextInt(60, 200);
        /** Ticks since the lid lifted, or -1 while shut. */
        int peekTicks = -1;
        /** Ticks until the mouth inside is cleared away after the lid drops, or -1. */
        int closingTicks = -1;
        int lingerTicks = 0;
        final List<BlockDisplay> mouth = new ArrayList<>();

        Lurker(Block block) {
            this.block = block;
        }
    }

    private static final Map<Block, Lurker> lurkers = new HashMap<>();
    private static int tick = 0;

    static void setup() {
        Bukkit.getScheduler().runTaskTimer(Core.getCurrentPlugin(), MimicLurk::update, TICKS, TICKS);
        Core.onPluginShutdown(() -> {
            for (Lurker lurker : lurkers.values()) {
                if (lurker.peekTicks >= 0 && lurker.block.getState(false) instanceof Chest chest) chest.close();
                clearMouth(lurker);
            }
            lurkers.clear();
        });
    }

    /** The chest is waking or going: drop it, and anything drawn inside it. */
    static void forget(Block block) {
        Lurker lurker = lurkers.remove(block);
        if (lurker != null) clearMouth(lurker);
    }

    private static void update() {
        if (tick % SCAN_TICKS == 0) scan();
        tick += TICKS;

        Iterator<Lurker> it = lurkers.values().iterator();
        List<Pair> wakes = new ArrayList<>();
        while (it.hasNext()) {
            Lurker lurker = it.next();
            Block block = lurker.block;
            if (!block.getWorld().isChunkLoaded(block.getX() >> 4, block.getZ() >> 4) || !MimicChests.isMimic(block)) {
                clearMouth(lurker);
                it.remove();
                continue;
            }
            Player wake = lurk(lurker);
            if (wake != null) wakes.add(new Pair(block, wake));
        }
        for (Pair wake : wakes) {
            forget(wake.block);
            MimicChests.wake(wake.block, wake.player);
        }
    }

    private record Pair(Block block, Player player) {
    }

    /** Find the dormant mimics around everyone who can be frightened. */
    private static void scan() {
        Set<Chunk> chunks = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!MimicTerror.isPrey(player)) continue;
            World world = player.getWorld();
            int cx = player.getLocation().getBlockX() >> 4;
            int cz = player.getLocation().getBlockZ() >> 4;
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (world.isChunkLoaded(cx + dx, cz + dz)) chunks.add(world.getChunkAt(cx + dx, cz + dz));
            }
        }
        Set<Block> found = new HashSet<>();
        for (Chunk chunk : chunks) {
            for (BlockState state : chunk.getTileEntities(block -> block.getType() == Material.CHEST, false)) {
                if (state instanceof Chest chest && MimicChests.isMimic(chest)) found.add(state.getBlock());
            }
        }
        for (Block block : found) lurkers.computeIfAbsent(block, Lurker::new);
        lurkers.entrySet().removeIf(entry -> {
            if (found.contains(entry.getKey())) return false;
            clearMouth(entry.getValue());
            return true;
        });
    }

    /** One step of a dormant mimic's patience; returns someone to wake for, if it has run out. */
    private static Player lurk(Lurker lurker) {
        Block block = lurker.block;
        Vector centre = block.getLocation().toCenterLocation().toVector();
        lurker.turnCooldown -= TICKS;
        lurker.breathCooldown -= TICKS;
        lurker.peekCooldown -= TICKS;
        if (lurker.closingTicks >= 0 && (lurker.closingTicks -= TICKS) < 0) clearMouth(lurker);

        Player nearest = null;
        double nearestDistance = RANGE;
        for (Player player : block.getWorld().getPlayers()) {
            if (!MimicTerror.isPrey(player)) continue;
            double distance = player.getEyeLocation().toVector().distance(centre);
            if (distance < nearestDistance) {
                nearest = player;
                nearestDistance = distance;
            }
        }
        if (nearest == null) {
            if (lurker.peekTicks >= 0) shut(lurker);
            lurker.lingerTicks = 0;
            return null;
        }

        Vector eye = nearest.getEyeLocation().toVector();
        double gaze = nearest.getEyeLocation().getDirection().dot(centre.clone().subtract(eye).normalize());
        boolean watched = gaze > Math.cos(Math.toRadians(40));
        boolean unseen = gaze < Math.cos(Math.toRadians(80));
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (lurker.peekTicks >= 0) {
            lurker.peekTicks += TICKS;
            if (watched || lurker.peekTicks > PEEK_MAX_TICKS || nearestDistance > PEEK_RANGE + 2) shut(lurker);
        } else if (unseen && nearestDistance < PEEK_RANGE && lurker.peekCooldown <= 0 && lurker.closingTicks < 0 && random.nextFloat() < .35f) {
            peek(lurker);
            lurker.peekCooldown = random.nextInt(20 * 20, 20 * 45);
        }

        if (lurker.peekTicks < 0 && lurker.closingTicks < 0 && unseen && nearestDistance < TURN_RANGE && lurker.turnCooldown <= 0) {
            if (turnTowards(block, eye)) {
                MimicTerror.play(block.getWorld(), centre, MimicTerror.CREAK, .35f, 1.3f + random.nextFloat() * .3f);
                lurker.turnCooldown = random.nextInt(60, 140);
            }
        }

        if (nearestDistance < BREATH_RANGE && lurker.breathCooldown <= 0 && random.nextFloat() < .3f) {
            MimicTerror.play(block.getWorld(), centre, MimicTerror.BREATH, .5f, .8f + random.nextFloat() * .25f);
            lurker.breathCooldown = random.nextInt(80, 200);
        }

        boolean lingering = nearestDistance < LINGER_RANGE && (nearest.getGameMode() == GameMode.SURVIVAL || nearest.getGameMode() == GameMode.ADVENTURE);
        lurker.lingerTicks = lingering ? lurker.lingerTicks + TICKS : 0;
        if (lurker.lingerTicks == LINGER_TICKS - 40) {
            MimicTerror.play(block.getWorld(), centre, MimicTerror.GROWL, .45f, 1.2f);
        }
        return lurker.lingerTicks >= LINGER_TICKS ? nearest : null;
    }

    /** Turn the chest's front to face whoever is near. False if it already does. */
    private static boolean turnTowards(Block block, Vector eye) {
        if (!(block.getBlockData() instanceof Directional directional)) return false;
        Vector toward = eye.clone().subtract(block.getLocation().toCenterLocation().toVector()).setY(0);
        if (toward.lengthSquared() < 1e-6) return false;
        BlockFace face = Mimic.cardinalFacing(toward);
        if (directional.getFacing() == face) return false;
        directional.setFacing(face);
        // same block, new facing: the block entity, its tag and its contents stay
        block.setBlockData(directional, false);
        return true;
    }

    /** Lift the lid on a black mouth, a row of fangs and two red eyes. */
    private static void peek(Lurker lurker) {
        Block block = lurker.block;
        if (!(block.getState(false) instanceof Chest chest)) return;
        BlockFace facing = block.getBlockData() instanceof Directional directional ? directional.getFacing() : BlockFace.SOUTH;
        float yaw = switch (facing) {
            case WEST -> (float) Math.toRadians(-90);
            case NORTH -> (float) Math.PI;
            case EAST -> (float) Math.toRadians(90);
            default -> 0f;
        };
        float px = 1f / 16;
        Location corner = block.getLocation();
        // the chest's base is 1–15 px across and 10 px tall; its open top shows from above
        lurker.mouth.add(part(corner, yaw, Material.BLACK_CONCRETE, 1.5f * px, 10.02f * px, 1.5f * px, 13 * px, .05f * px, 13 * px, false));
        for (float x : new float[]{5.2f, 10.8f}) {
            lurker.mouth.add(part(corner, yaw, Material.RED_CONCRETE, (x - 1.2f) * px, 10.05f * px, 6f * px, 2.4f * px, .12f * px, 1.6f * px, true));
            lurker.mouth.add(part(corner, yaw, Material.BLACK_CONCRETE, (x - .2f) * px, 10.1f * px, 6.1f * px, .4f * px, .12f * px, 1.4f * px, false));
        }
        for (float x = 2.6f; x <= 13.4f; x += 1.8f) {
            float height = (1.6f + (float) ThreadLocalRandom.current().nextDouble(1.4)) * px;
            lurker.mouth.add(part(corner, yaw, Material.BONE_BLOCK, (x - .5f) * px, 10f * px, 13.4f * px, 1f * px, height, 1f * px, false));
        }
        chest.open();
        World world = block.getWorld();
        Vector centre = block.getLocation().toCenterLocation().toVector();
        MimicTerror.play(world, centre, MimicTerror.BREATH, .7f, .7f);
        world.playSound(block.getLocation().toCenterLocation(), org.bukkit.Sound.BLOCK_CHEST_OPEN, SoundCategory.BLOCKS, .5f, .7f);
        lurker.peekTicks = 0;
    }

    /** Snap the lid down; the mouth inside is cleared once it has closed. */
    private static void shut(Lurker lurker) {
        if (lurker.block.getState(false) instanceof Chest chest) chest.close();
        MimicTerror.play(lurker.block.getWorld(), lurker.block.getLocation().toCenterLocation().toVector(), MimicTerror.CHOMP, .5f, 1.2f);
        lurker.peekTicks = -1;
        lurker.closingTicks = CLOSE_TICKS;
    }

    private static BlockDisplay part(Location corner, float yaw, Material material, float x, float y, float z, float width, float height, float depth, boolean glow) {
        BlockData data = material.createBlockData();
        Matrix4f transform = new Matrix4f().translate(.5f, 0f, .5f).rotateY(yaw).translate(-.5f, 0f, -.5f)
            .translate(x, y, z).scale(width, height, depth);
        return corner.getWorld().spawn(corner, BlockDisplay.class, display -> {
            display.setPersistent(false);
            display.setBlock(data);
            display.setTransformationMatrix(transform);
            if (glow) display.setBrightness(new Display.Brightness(15, 15));
        });
    }

    private static void clearMouth(Lurker lurker) {
        for (BlockDisplay display : lurker.mouth) display.remove();
        lurker.mouth.clear();
        lurker.closingTicks = -1;
    }
}
