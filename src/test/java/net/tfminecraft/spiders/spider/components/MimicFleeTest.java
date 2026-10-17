package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;

/** After the stare: the flight, standing about, and settling back down into a chest. */
class MimicFleeTest {
    private MimicServer server;
    private MimicDice dice;
    private Player victim;

    @BeforeEach
    void start() {
        server = new MimicServer();
        dice = new MimicDice();
    }

    @AfterEach
    void stop() {
        dice.close();
        server.close();
    }

    /** Woken by a player at (.5, 12.5), hunted them, lost them, stared, and has just turned to run. */
    private MimicRig fleeing() {
        MimicRig rig = new MimicRig(server, dice);
        victim = rig.prey(.5, 12.5);
        rig.wake(victim.getUniqueId(), null);
        rig.tickUntil(MimicState.HUNTING, 80);
        rig.tick(Mimic.HUNT_TICKS + 1);
        assertEquals(MimicState.STARING, rig.mimic.getState());
        rig.tickUntil(MimicState.FLEEING, Mimic.STARE_TICKS);
        return rig;
    }

    /** Woken from the console, and now standing about. */
    private MimicRig awake() {
        MimicRig rig = new MimicRig(server, dice).wake(null, null);
        rig.tickUntil(MimicState.AWAKE, Mimic.WAKE_TICKS);
        return rig;
    }

    private static long chunk(int x, int z) {
        return (((long) x) << 32) | (((long) z) & 0xffffffffL);
    }

    /** The way it asked its legs to walk, flattened and normalised. */
    private static Vector heading(MimicRig rig) {
        return rig.locomotion.getWalkVelocity().clone().setY(0).normalize();
    }

    /** Step its body along the way it is walking. */
    private static void follow(MimicRig rig) {
        rig.body.getPosition().add(rig.locomotion.getWalkVelocity());
    }

    @Test
    @DisplayName("it gallops dead away from its prey, crying out, keeping the ground ahead loaded")
    void gallopsAway() {
        MimicRig rig = fleeing();
        // the chunk it stands in, before the first step of the flight
        assertEquals(List.of(chunk(0, 0)), rig.mimic.wantedChunks(rig.body));
        rig.tick();
        assertTrue(rig.options.getGallop());
        assertEquals(new Vector(0, 0, -rig.options.getGait().getMaxSpeed()), rig.locomotion.getWalkVelocity());
        assertEquals(new Vector(0, 0, -1), rig.locomotion.getFaceDirection());
        // and the one 12 blocks ahead
        assertEquals(List.of(chunk(0, 0), chunk(0, -1)), rig.mimic.wantedChunks(rig.body));
        for (int i = 0; i < 120; i++) {
            follow(rig);
            rig.tick();
        }
        assertEquals(MimicState.FLEEING, rig.mimic.getState());
        assertEquals(new Vector(0, 0, -1), heading(rig), "veered off");
        verify(rig.blocks.world, atLeastOnce()).playSound(any(org.bukkit.Location.class), eq("tfmc_spiders:" + MimicTerror.SHRIEK), any(org.bukkit.SoundCategory.class), eq(4f), anyFloat());
    }

    @Test
    @DisplayName("stuck, it tries ever wider detours, alternating sides; going again, it runs straight")
    void detoursWhenStuck() {
        MimicRig rig = fleeing();
        Vector away = new Vector(0, 0, -1);
        // two-second checks: the first sets a mark, then each without progress widens the detour
        double[] detours = {Math.PI / 4, -Math.PI / 4, Math.PI / 2, -Math.PI / 2, 3 * Math.PI / 4, -3 * Math.PI / 4};
        rig.tick(2 * Mimic.PROGRESS_CHECK_TICKS);
        for (double detour : detours) {
            rig.tick();
            assertEquals(away.clone().rotateAroundY(detour), heading(rig), "detour " + detour);
            rig.tick(Mimic.PROGRESS_CHECK_TICKS - 1);
        }
        // on the move again: straight away once more, and it stays straight
        for (int i = 0; i < 3 * Mimic.PROGRESS_CHECK_TICKS; i++) {
            rig.body.getPosition().add(new Vector(0, 0, -.2));
            rig.tick();
        }
        assertEquals(away.clone().rotateAroundY(0), heading(rig));
    }

    @Test
    @DisplayName("250 blocks off it stops to become a chest where it stands, facing the way it ran")
    void settlesOnceFarEnough() {
        MimicRig rig = fleeing();
        rig.moveTo(.5, -237.5);
        rig.body.getOrientation().set(new Quaternionf().rotationY((float) Math.PI));
        rig.tick();
        assertEquals(MimicState.SETTLING, rig.mimic.getState());
        assertFalse(rig.options.getGallop());
        assertSame(rig.blocks.block(0, 1, -238), rig.mimic.getRestCell());
        assertEquals(BlockFace.NORTH, rig.mimic.getRestFacing());
        assertEquals(List.of(chunk(0, -15)), rig.mimic.wantedChunks(rig.body));
    }

    @Test
    @DisplayName("far enough but over a drop, it just stands")
    void nowhereToSettleAfterFleeing() {
        MimicRig rig = fleeing();
        rig.moveTo(.5, -250);
        rig.body.getPosition().setY(30);
        rig.tick();
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
    }

    @Test
    @DisplayName("it never runs into terrain that hasn't been generated")
    void stopsAtTheEdgeOfTheWorld() {
        MimicRig rig = fleeing();
        rig.tick();
        rig.blocks.chunksGenerated = false;
        assertEquals(1, rig.mimic.wantedChunks(rig.body).size(), "no ticket for ungenerated ground");
        rig.tick();
        assertEquals(MimicState.SETTLING, rig.mimic.getState());
    }

    @Test
    @DisplayName("mounted while fleeing, it stops and stands")
    void riderStopsTheFlight() {
        MimicRig rig = fleeing();
        rig.ride();
        rig.tick();
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        assertFalse(rig.options.getGallop());
    }

    @Test
    @DisplayName("standing right where its prey was, it runs the way it faces, or north if it faces straight up")
    void fleeingFromRightHere() {
        MimicRig rig = fleeing();
        rig.moveTo(.5, 12.5);
        rig.body.getOrientation().set(new Quaternionf().rotationY((float) Math.PI / 2));
        rig.tick();
        assertEquals(new Vector(1, 0, 0), heading(rig));
        rig.body.getOrientation().set(new Quaternionf().rotationX((float) -Math.PI / 2));
        rig.tick();
        assertEquals(new Vector(0, 0, 1), heading(rig));
    }

    @Test
    @DisplayName("left alone for twenty seconds it settles where it stands; moving or ridden, it doesn't")
    void idleThenSettle() {
        MimicRig rig = awake();
        rig.tick(100);
        assertEquals(100, rig.mimic.getIdleTicks());
        rig.body.setWalking(true);
        rig.tick();
        assertEquals(0, rig.mimic.getIdleTicks());
        rig.body.setWalking(false);
        rig.tick(50);
        rig.ride();
        rig.tick();
        assertEquals(0, rig.mimic.getIdleTicks());
        rig.dismount();
        rig.tick(Mimic.IDLE_SLEEP_TICKS - 1);
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        rig.tick();
        assertEquals(MimicState.SETTLING, rig.mimic.getState());
        assertSame(rig.blocks.block(0, 1, 0), rig.mimic.getRestCell());
        assertEquals(BlockFace.SOUTH, rig.mimic.getRestFacing());
    }

    @Test
    @DisplayName("with nowhere to sit it waits another ten seconds before looking again")
    void nowhereToSit() {
        MimicRig rig = awake();
        rig.body.getPosition().setY(30);
        rig.tick(Mimic.IDLE_SLEEP_TICKS);
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        assertEquals(Mimic.IDLE_SLEEP_TICKS / 2, rig.mimic.getIdleTicks());
    }

    @Test
    @DisplayName("awake, it snaps its lid shut and open now and then, and twitches")
    void chompsWhileAwake() {
        dice.nextFloat = 0f;
        MimicRig rig = awake();
        // the first snap is due 20 ticks after it was made (counting the tick it stood up)
        rig.tick(18);
        assertTrue(rig.mimic.getLidAngle() > Maths.toRadians(20f));
        rig.tick();
        assertEquals(0f, rig.mimic.getLidAngle(), 1e-6f, "lid snapped shut");
        rig.tick();
        assertEquals(0, rig.heard(MimicTerror.CHOMP));
        rig.tick();
        assertEquals(1, rig.heard(MimicTerror.CHOMP));
        rig.tick(3);
        assertTrue(rig.mimic.getLidAngle() > Maths.toRadians(40f), "springs open after the snap");
        rig.tick(40);
        assertEquals(1, rig.heard(MimicTerror.CHOMP));
        rig.tick(10);
        assertEquals(2, rig.heard(MimicTerror.CHOMP));
    }

    @Test
    @DisplayName("settling, it walks onto the middle of its cell, squares up, sits down and is a chest again")
    void settlesIntoAChest() {
        MimicRig rig = awake();
        rig.moveTo(.9, .5);
        rig.tick(Mimic.IDLE_SLEEP_TICKS);
        assertEquals(MimicState.SETTLING, rig.mimic.getState());
        rig.tick();
        // a slow shuffle back onto the cell's centre, turned to face south
        assertEquals(new Vector(-.6 * rig.options.getGait().getMaxSpeed(), 0, 0), rig.locomotion.getWalkVelocity());
        assertEquals(BlockFace.SOUTH.getDirection(), rig.locomotion.getFaceDirection());
        assertEquals(MimicState.SETTLING, rig.mimic.getState());

        rig.moveTo(.5, .5);
        rig.tick();
        assertEquals(MimicState.CROUCHING, rig.mimic.getState());
        assertEquals(new Vector(), rig.locomotion.getWalkVelocity());
        double rise = 1;
        for (int i = 0; i < Mimic.CROUCH_TICKS; i++) {
            rig.tick();
            assertTrue(rig.mimic.getRise() <= rise);
            rise = rig.mimic.getRise();
        }
        assertEquals(0.0, rise, 1e-9);
        assertFalse(rig.entity.getScheduledForRemoval());
        rig.tick(3);
        assertTrue(rig.entity.getScheduledForRemoval());
        Block chest = rig.blocks.block(0, 1, 0);
        assertEquals(Material.CHEST, chest.getType());
        assertTrue(MimicChests.isMimic(chest));
        assertEquals(BlockFace.SOUTH, ((org.bukkit.block.data.type.Chest) chest.getBlockData()).getFacing());
        verify(rig.blocks.world).playSound(any(org.bukkit.Location.class), eq(Sound.BLOCK_CHEST_CLOSE), eq(.8f), eq(.7f));
    }

    @Test
    @DisplayName("if it can't square up in four seconds it sits down anyway")
    void settleTimesOut() {
        MimicRig rig = awake();
        rig.tick(Mimic.IDLE_SLEEP_TICKS);
        // turned away from the facing it chose, and nothing turns it back
        rig.body.getOrientation().set(new Quaternionf().rotationY((float) Math.PI / 2));
        rig.tick(Mimic.SETTLE_TIMEOUT_TICKS);
        assertEquals(MimicState.SETTLING, rig.mimic.getState());
        rig.tick();
        assertEquals(MimicState.CROUCHING, rig.mimic.getState());

        MimicRig upright = awake();
        upright.tick(Mimic.IDLE_SLEEP_TICKS);
        // pointing straight up, it can't tell which way it faces
        upright.body.getOrientation().set(new Quaternionf().rotationX((float) -Math.PI / 2));
        upright.tick(Mimic.SETTLE_TIMEOUT_TICKS);
        assertEquals(MimicState.SETTLING, upright.mimic.getState());
    }

    @Test
    @DisplayName("mounted while settling, it stands back up")
    void riderStopsTheSettling() {
        MimicRig rig = awake();
        rig.moveTo(.9, .5);
        rig.tick(Mimic.IDLE_SLEEP_TICKS);
        rig.ride();
        rig.tick();
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
    }

    @Test
    @DisplayName("if its spot is taken as it sits, it stands back up")
    void spotTakenWhileCrouching() {
        MimicRig rig = awake();
        rig.tick(Mimic.IDLE_SLEEP_TICKS + 1);
        assertEquals(MimicState.CROUCHING, rig.mimic.getState());
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) rig.blocks.set(x, 1, z, MimicWorld.WATER);
        rig.tick(Mimic.CROUCH_TICKS + 3);
        assertEquals(MimicState.WAKING, rig.mimic.getState());
        assertFalse(rig.entity.getScheduledForRemoval());
        rig.tickUntil(MimicState.AWAKE, Mimic.WAKE_TICKS);
    }

    private int drool(MimicRig rig) {
        return (int) org.mockito.Mockito.mockingDetails(rig.blocks.world).getInvocations().stream()
            .filter(it -> it.getMethod().getName().equals("spawnParticle") && it.getArguments()[0] == Particle.FALLING_DUST).count();
    }

    @Test
    @DisplayName("blood-red drool falls from its open mouth, now and then")
    void drool() {
        MimicRig rig = fleeing();
        rig.tick(10);
        assertTrue(drool(rig) > 0);
        int before = drool(rig);
        dice.nextInt = 1;
        rig.tick(10);
        assertEquals(before, drool(rig), "only one roll in three drools");
    }

    @Test
    @DisplayName("sitting down, it doesn't drool even with its mouth open")
    void noDroolWhileSittingDown() {
        // its first snap lands just as its idle time runs out
        dice.range = (origin, bound) -> origin == 20 ? Mimic.IDLE_SLEEP_TICKS - 4 : origin;
        MimicRig rig = awake();
        rig.tick(Mimic.IDLE_SLEEP_TICKS - 1);
        int before = drool(rig);
        boolean openSettling = false;
        boolean openCrouching = false;
        for (int i = 0; i < 4; i++) {
            rig.tick();
            boolean open = rig.mimic.getLidAngle() >= Maths.toRadians(25f);
            if (open && rig.mimic.getState() == MimicState.SETTLING) openSettling = true;
            if (open && rig.mimic.getState() == MimicState.CROUCHING) openCrouching = true;
        }
        assertTrue(openSettling && openCrouching, "the snap didn't overlap the settling");
        assertEquals(before, drool(rig));
    }

    @Test
    @DisplayName("without legs to steer it still hunts, stares, flees and settles")
    void legless() {
        MimicRig rig = new MimicRig(server, dice);
        victim = rig.prey(.5, 2.0);
        rig.legless = true;
        rig.wake(victim.getUniqueId(), null);
        rig.tickUntil(MimicState.HUNTING, 80);
        rig.tick(Mimic.THRASH_TICKS);
        assertTrue(rig.mimic.getBites() > 0);
        rig.tickUntil(MimicState.STARING, Mimic.HUNT_TICKS + 2);
        rig.tickUntil(MimicState.FLEEING, Mimic.STARE_TICKS);
        rig.tick();
        rig.moveTo(.5, -250);
        rig.tickUntil(MimicState.SETTLING, 2);
        rig.tickUntil(MimicState.CROUCHING, Mimic.SETTLE_TIMEOUT_TICKS + 2);
        rig.tick(Mimic.CROUCH_TICKS + 3);
        assertTrue(rig.entity.getScheduledForRemoval());
        assertNull(rig.locomotion.getWalkVelocity());
    }
}
