package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/** Hit it and it screams; hit it while it isn't busy, and it comes for you. */
class MimicHitTest {
    private MimicServer server;
    private MimicDice dice;

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

    private MimicRig awake() {
        MimicRig rig = new MimicRig(server, dice).wake(null, null);
        rig.tickUntil(MimicState.AWAKE, Mimic.WAKE_TICKS);
        return rig;
    }

    private static void hit(MimicRig rig, Player attacker) {
        rig.mimic.onHit(rig.entity, rig.body, attacker);
    }

    @Test
    @DisplayName("hit while standing about, it screams and hunts whoever hit it, for half as long")
    void hitWhileIdle() {
        MimicRig rig = awake();
        Player attacker = rig.prey(.5, 12.5);
        hit(rig, attacker);
        assertEquals(1, rig.heard(MimicTerror.SHRIEK));
        assertEquals(MimicState.HUNTING, rig.mimic.getState());
        assertTrue(rig.options.getGallop());
        rig.tick();
        assertEquals(new Vector(.5, MimicRig.FLOOR, 12.5), rig.entity.query(Pounce.class).getTarget().get());
        rig.tick(Mimic.HUNT_TICKS / 2 - 1);
        assertEquals(MimicState.HUNTING, rig.mimic.getState());
        rig.tick();
        assertEquals(MimicState.STARING, rig.mimic.getState());
    }

    @Test
    @DisplayName("blows in quick succession only make it scream once")
    void hitCooldown() {
        MimicRig rig = awake();
        hit(rig, null);
        hit(rig, null);
        assertEquals(1, rig.heard(MimicTerror.SHRIEK));
        rig.tick(11);
        hit(rig, null);
        assertEquals(1, rig.heard(MimicTerror.SHRIEK));
        rig.tick();
        hit(rig, null);
        assertEquals(2, rig.heard(MimicTerror.SHRIEK));
    }

    @Test
    @DisplayName("struck by something that isn't a player, or by a spectator, it only screams")
    void notFairGame() {
        MimicRig rig = awake();
        hit(rig, null);
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        rig.tick(12);
        Player spectator = rig.prey(.5, 5);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        hit(rig, spectator);
        assertEquals(2, rig.heard(MimicTerror.SHRIEK));
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
    }

    @Test
    @DisplayName("set on a target by staff, a blow doesn't distract it")
    void stickToItsTarget() {
        MimicRig rig = new MimicRig(server, dice).wake(null, spider -> new Vector(.5, 1, 30));
        Player attacker = rig.prey(.5, 5);
        hit(rig, attacker);
        assertEquals(MimicState.AMBUSH, rig.mimic.getState());
        assertEquals(1, rig.heard(MimicTerror.SHRIEK));
    }

    @Test
    @DisplayName("busy waking, lunging, hunting or sitting down, it doesn't turn on whoever hit it")
    void busy() {
        MimicRig waking = new MimicRig(server, dice).wake(null, null);
        Player attacker = waking.prey(.5, 5);
        hit(waking, attacker);
        assertEquals(MimicState.WAKING, waking.mimic.getState());

        MimicRig crouching = awake();
        crouching.tick(Mimic.IDLE_SLEEP_TICKS + 1);
        assertEquals(MimicState.CROUCHING, crouching.mimic.getState());
        hit(crouching, crouching.prey(.5, 5));
        assertEquals(MimicState.CROUCHING, crouching.mimic.getState());
    }

    @Test
    @DisplayName("hit while settling, staring or fleeing, it turns on them")
    void turnsOnThem() {
        MimicRig settling = awake();
        settling.moveTo(.9, .5);
        settling.tick(Mimic.IDLE_SLEEP_TICKS);
        assertEquals(MimicState.SETTLING, settling.mimic.getState());
        hit(settling, settling.prey(.5, 5));
        assertEquals(MimicState.HUNTING, settling.mimic.getState());

        MimicRig staring = new MimicRig(server, dice);
        Player waker = staring.prey(.5, 12.5);
        staring.wake(waker.getUniqueId(), null);
        staring.tickUntil(MimicState.HUNTING, 80);
        staring.tick(Mimic.HUNT_TICKS + 1);
        assertEquals(MimicState.STARING, staring.mimic.getState());
        staring.tick(12);
        Player other = staring.prey(5.5, .5);
        hit(staring, other);
        assertEquals(MimicState.HUNTING, staring.mimic.getState());
        staring.tick();
        // after the newcomer now
        assertEquals(new Vector(5.5, MimicRig.FLOOR, .5), staring.entity.query(Pounce.class).getTarget().get());

        staring.tick(Mimic.HUNT_TICKS / 2 + 1);
        staring.tickUntil(MimicState.FLEEING, Mimic.STARE_TICKS);
        staring.tick(12);
        hit(staring, waker);
        assertEquals(MimicState.HUNTING, staring.mimic.getState());
    }

    @Test
    @DisplayName("ridden, it lets blows pass; with an empty saddle it fights back")
    void ridden() {
        MimicRig rig = awake();
        Player attacker = rig.prey(.5, 5);
        rig.ride();
        hit(rig, attacker);
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        rig.dismount();
        rig.tick(12);
        hit(rig, attacker);
        assertEquals(MimicState.HUNTING, rig.mimic.getState());
    }

    @Test
    @DisplayName("a mimic without options of its own can't hunt")
    void noOptions() {
        MimicRig rig = awake();
        ECSEntity bare = new ECSEntity();
        bare.addComponent(rig.mimic);
        hit(rig, null);
        rig.tick(12);
        rig.mimic.onHit(bare, rig.body, rig.prey(.5, 5));
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        assertFalse(rig.options.getGallop());
    }
}
