package net.tfminecraft.spiders.spider.components;

import io.papermc.paper.entity.LookAnchor;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** From the moment a chest is touched to the moment it turns to run: ambush, lunge, waking, hunt, bites, stare. */
class MimicHuntTest {
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

    private Player victim;

    @Test
    @DisplayName("touched, it cracks its lid and breathes, the waker's heart thumps, and it springs at their face")
    void ambushThenLunge() {
        MimicRig rig = woken(.5, 3.5);
        assertEquals(MimicState.AMBUSH, rig.mimic.getState());
        assertEquals(victim.getUniqueId(), rig.mimic.getWaker());

        rig.tick();
        assertEquals(1, rig.heard(MimicTerror.CREAK));
        assertEquals(1, rig.heard(MimicTerror.BREATH));
        assertEquals(new Vector(0, 0, 1), rig.locomotion.getFaceDirection());
        assertEquals(0.0, rig.mimic.getRise());
        rig.tick(4);
        assertEquals(0, MimicRig.heardBy(victim, MimicTerror.HEARTBEAT));
        rig.tick();
        assertEquals(1, MimicRig.heardBy(victim, MimicTerror.HEARTBEAT), "half way through the ambush");
        assertTrue(rig.mimic.getLidAngle() > 0 && rig.mimic.getLidAngle() <= Maths.toRadians(10f) + 1e-6, "only a crack");

        rig.tick(5);
        assertEquals(MimicState.AMBUSH, rig.mimic.getState());
        rig.tick();
        assertEquals(MimicState.LUNGE, rig.mimic.getState());
        // a jump scare: their head is wrenched round to look into its mouth
        verify(victim).lookAt(eq(.5), anyDouble(), eq(.5), eq(LookAnchor.EYES));
        assertEquals(1, MimicRig.heardBy(victim, MimicTerror.STINGER));
        assertEquals(1, rig.heard(MimicTerror.CHOMP));
        verify(rig.blocks.world).spawnParticle(eq(Particle.BLOCK), any(Location.class), eq(30), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(BlockData.class));
        // a hop up and towards them, stopping short of their feet
        int flight = (int) Math.max(4, Math.round(2 * Mimic.LUNGE_LIFT / rig.options.getGait().getGravityAcceleration()));
        assertEquals(flight, rig.body.getLeapTicks());
        assertEquals(new Vector(0, Mimic.LUNGE_LIFT, (3 - .9) / flight), rig.body.getVelocity());
    }

    /** A mimic woken by a player at ({@code x}, {@code z}), who becomes {@link #victim}. */
    private MimicRig woken(double x, double z) {
        MimicRig rig = new MimicRig(server, dice);
        victim = rig.prey(x, z);
        return rig.wake(victim.getUniqueId(), null);
    }

    @Test
    @DisplayName("the lunge carries it at most three blocks, at least a little, and only scares a waker who is close")
    void lungeReach() {
        MimicRig far = woken(.5, 20.5);
        far.tick(Mimic.AMBUSH_TICKS);
        int flight = far.body.getLeapTicks();
        assertEquals(Mimic.LUNGE_RANGE / flight, far.body.getVelocity().getZ(), 1e-9);
        assertEquals(0, MimicRig.heardBy(victim, MimicTerror.STINGER), "too far off to see it spring");
        verify(victim, never()).lookAt(anyDouble(), anyDouble(), anyDouble(), any(LookAnchor.class));

        MimicRig close = woken(.5, 1.0);
        close.tick(Mimic.AMBUSH_TICKS);
        assertEquals(.4 / close.body.getLeapTicks(), close.body.getVelocity().getZ(), 1e-9);
    }

    @Test
    @DisplayName("mid-lunge it shrieks again and bites whoever it reaches")
    void lungeBites() {
        MimicRig rig = woken(.5, 2.0);
        rig.tick(Mimic.AMBUSH_TICKS);
        int screeches = rig.heard(MimicTerror.SCREECH);
        rig.tick();
        assertEquals(1, rig.mimic.getBites());
        verify(victim).damage(Mimic.BITE_DAMAGE);
        rig.tick(Mimic.SECOND_SHRIEK_TICKS - 1);
        assertEquals(screeches + 1, rig.heard(MimicTerror.SCREECH));
        // one bite per lunge: the bite cooldown outlasts it
        assertEquals(1, rig.mimic.getBites());
    }

    @Test
    @DisplayName("someone hovering above it isn't in reach of its mouth")
    void outOfReachAbove() {
        MimicRig rig = woken(.5, 2.0);
        MimicServer.move(victim, new Vector(.5, MimicRig.FLOOR + 3, 2.0), new Vector(0, 0, -1));
        rig.tick(Mimic.AMBUSH_TICKS + 3);
        assertEquals(0, rig.mimic.getBites());
    }

    @Test
    @DisplayName("the lunge ends when it lands, after a few ticks at least, or after a second and a bit at most")
    void lungeEnds() {
        MimicRig rig = woken(.5, 6.5);
        rig.tick(Mimic.AMBUSH_TICKS);
        rig.tick(Mimic.LUNGE_MAX_TICKS);
        assertEquals(MimicState.LUNGE, rig.mimic.getState(), "still in the air");
        rig.tick();
        assertEquals(MimicState.WAKING, rig.mimic.getState());

        MimicRig landed = woken(.5, 6.5);
        landed.tick(Mimic.AMBUSH_TICKS);
        landed.body.leap(new Vector(), 0);
        landed.tick(3);
        assertEquals(MimicState.LUNGE, landed.mimic.getState());
        landed.tick();
        assertEquals(MimicState.WAKING, landed.mimic.getState());
    }

    @Test
    @DisplayName("woken by a player it unfolds in four cracking snaps, then hunts them at a gallop")
    void wakesInSnapsThenHunts() {
        MimicRig rig = woken(.5, 6.5);
        rig.tickUntil(MimicState.WAKING, 60);
        int cracks = rig.heard(MimicTerror.CRACK);
        double previous = 0;
        for (int i = 1; i < Mimic.WAKE_TICKS; i++) {
            rig.tick();
            assertEquals(Mimic.stutter((double) i / Mimic.WAKE_TICKS), rig.mimic.getRise(), 1e-9);
            assertTrue(rig.mimic.getRise() >= previous);
            previous = rig.mimic.getRise();
        }
        assertEquals(cracks + 4, rig.heard(MimicTerror.CRACK));
        assertFalse(rig.options.getGallop());
        rig.tick();
        assertEquals(MimicState.HUNTING, rig.mimic.getState());
        assertTrue(rig.options.getGallop());
    }

    @Test
    @DisplayName("woken from the console it just stands up smoothly and waits")
    void consoleWake() {
        MimicRig rig = new MimicRig(server, dice).wake(null, null);
        assertEquals(MimicState.WAKING, rig.mimic.getState());
        rig.tick(8);
        assertEquals(Mimic.smoothstep(.5), rig.mimic.getRise(), 1e-9);
        rig.tick(8);
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        assertEquals(0, rig.heard(MimicTerror.CRACK));
        assertNull(rig.locomotion.getFaceDirection());
    }

    @Test
    @DisplayName("a waker who is gone or in another world is neither faced, scared nor leapt at, and it stands about instead")
    void wakerGone() {
        MimicRig offline = woken(.5, 3.5);
        server.players.clear();
        offline.tick(Mimic.AMBUSH_TICKS);
        assertEquals(MimicState.LUNGE, offline.mimic.getState());
        assertEquals(0, offline.body.getLeapTicks());
        assertNull(offline.locomotion.getFaceDirection());
        assertEquals(0, MimicRig.heardBy(victim, MimicTerror.STINGER));
        offline.tickUntil(MimicState.AWAKE, 60);

        MimicRig elsewhere = woken(.5, 3.5);
        when(victim.getWorld()).thenReturn(mock(World.class));
        elsewhere.tick(Mimic.AMBUSH_TICKS);
        assertEquals(0, elsewhere.body.getLeapTicks());
        // they're still its prey, wherever they are, so the scare still finds them
        assertEquals(1, MimicRig.heardBy(victim, MimicTerror.STINGER));
    }

    @Test
    @DisplayName("someone riding it as it wakes keeps it from hunting")
    void riderStopsTheWakingHunt() {
        MimicRig rig = woken(.5, 6.5);
        rig.tickUntil(MimicState.WAKING, 60);
        rig.ride();
        rig.tickUntil(MimicState.AWAKE, Mimic.WAKE_TICKS);
        assertFalse(rig.options.getGallop());
    }

    /** Woken by a player at ({@code x}, {@code z}) and now hunting them. */
    private MimicRig hunting(double x, double z) {
        MimicRig rig = woken(x, z);
        rig.tickUntil(MimicState.HUNTING, 80);
        return rig;
    }

    @Test
    @DisplayName("while hunting it pounces, darkens their world and sets their heart racing")
    void huntingFromAfar() {
        MimicRig rig = hunting(.5, 12.5);
        rig.tick();
        Pounce pounce = rig.entity.query(Pounce.class);
        assertNotNull(pounce);
        assertEquals(new Vector(.5, MimicRig.FLOOR, 12.5), pounce.getTarget().get());
        assertEquals(1.0, rig.mimic.getRise());
        verify(victim, atLeastOnce()).addPotionEffect(any(PotionEffect.class));
        int beats = MimicRig.heardBy(victim, MimicTerror.HEARTBEAT);
        rig.tick(MimicTerror.heartbeatInterval(12) - 1);
        assertEquals(beats, MimicRig.heardBy(victim, MimicTerror.HEARTBEAT));
        rig.tick();
        assertEquals(beats + 1, MimicRig.heardBy(victim, MimicTerror.HEARTBEAT));
        // it doesn't stack pounces
        assertTrue(rig.entity.getComponents().stream().filter(it -> it instanceof Pounce).count() == 1);
        // the lid works up and down as it runs, but never snaps
        int chomps = rig.heard(MimicTerror.CHOMP);
        rig.tick(12);
        assertEquals(chomps, rig.heard(MimicTerror.CHOMP));
    }

    @Test
    @DisplayName("beyond 24 blocks the dark doesn't reach them")
    void noDarknessFarOff() {
        MimicRig rig = hunting(.5, 30.5);
        rig.tick(10);
        verify(victim, never()).addPotionEffect(any(PotionEffect.class));
        assertEquals(MimicState.HUNTING, rig.mimic.getState());
    }

    @Test
    @DisplayName("close in, its lid snaps over and over")
    void frenzy() {
        MimicRig rig = hunting(.5, 5.5);
        int chomps = rig.heard(MimicTerror.CHOMP);
        rig.tick(12);
        assertEquals(chomps + 2, rig.heard(MimicTerror.CHOMP));
    }

    @Test
    @DisplayName("it cries out every so often while hunting: mostly shrieks, sometimes a screech")
    void cries() {
        MimicRig shrieking = hunting(.5, 12.5);
        int shrieks = shrieking.heard(MimicTerror.SHRIEK);
        shrieking.tick(30);
        assertEquals(shrieks + 1, shrieking.heard(MimicTerror.SHRIEK));

        dice.nextFloat = 0f;
        MimicRig screeching = hunting(.5, 12.5);
        int screeches = screeching.heard(MimicTerror.SCREECH);
        screeching.tick(30);
        assertEquals(screeches + 1, screeching.heard(MimicTerror.SCREECH));
    }

    @Test
    @DisplayName("it bites, thrashes its head instead of pouncing, and can't bite again for over a second")
    void biteAndThrash() {
        MimicRig rig = hunting(.5, 6.5);
        rig.tick();
        assertNotNull(rig.entity.query(Pounce.class));
        int before = rig.mimic.getBites();
        MimicServer.move(victim, new Vector(.5, MimicRig.FLOOR, 1.8), new Vector(0, 0, -1));
        rig.tick();
        assertEquals(before + 1, rig.mimic.getBites());
        assertNull(rig.entity.query(Pounce.class), "the pounce stops for the bite");
        for (int i = 0; i < Mimic.THRASH_TICKS; i++) {
            rig.tick();
            assertNull(rig.entity.query(Pounce.class), "pounced while thrashing");
            assertEquals(new Vector(0, 0, 1), rig.locomotion.getFaceDirection());
        }
        rig.tick();
        assertNotNull(rig.entity.query(Pounce.class));
        rig.tick(Mimic.BITE_COOLDOWN - Mimic.THRASH_TICKS - 2);
        assertEquals(before + 1, rig.mimic.getBites());
        rig.tick();
        assertEquals(before + 2, rig.mimic.getBites());
    }

    @Test
    @DisplayName("bites never take the last heart")
    void bitesLeaveTheLastHeart() {
        MimicRig rig = hunting(.5, 6.5);
        when(victim.getHealth()).thenReturn(3.0);
        MimicServer.move(victim, new Vector(.5, MimicRig.FLOOR, 1.8), new Vector(0, 0, -1));
        rig.tick();
        verify(victim).damage(1.0);
        when(victim.getHealth()).thenReturn(2.0);
        rig.tick(Mimic.BITE_COOLDOWN);
        assertTrue(rig.mimic.getBites() >= 2);
        verify(victim).damage(anyDouble());
        verify(victim, atLeastOnce()).sendHurtAnimation(anyFloat());
    }

    @Test
    @DisplayName("chasing something that isn't a player, a bite is just a snap of the lid")
    void bitingNothing() {
        Vector target = new Vector(.5, MimicRig.FLOOR, 1.8);
        MimicRig rig = new MimicRig(server, dice).wake(null, spider -> target);
        assertEquals(MimicState.AMBUSH, rig.mimic.getState());
        rig.tickUntil(MimicState.HUNTING, 80);
        int bites = rig.mimic.getBites();
        rig.tick(Mimic.BITE_COOLDOWN);
        assertTrue(rig.mimic.getBites() > bites);
        verify(rig.blocks.world, atLeastOnce()).playSound(any(Location.class), eq("tfmc_spiders:" + MimicTerror.CHOMP), any(), eq(1.5f), anyFloat());
    }

    @Test
    @DisplayName("the hunt ends after ten seconds, when they get away or vanish, with a growl")
    void huntEnds() {
        MimicRig timedOut = hunting(.5, 12.5);
        timedOut.tick(Mimic.HUNT_TICKS);
        assertEquals(MimicState.HUNTING, timedOut.mimic.getState());
        timedOut.tick();
        assertEquals(MimicState.STARING, timedOut.mimic.getState());
        assertEquals(1, timedOut.heard(MimicTerror.GROWL));
        assertFalse(timedOut.options.getGallop());
        assertNull(timedOut.entity.query(Pounce.class));

        MimicRig outran = hunting(.5, 12.5);
        MimicServer.move(victim, new Vector(.5, MimicRig.FLOOR, 60), new Vector(0, 0, 1));
        outran.tick();
        assertEquals(MimicState.STARING, outran.mimic.getState());

        MimicRig vanished = hunting(.5, 12.5);
        server.players.remove(victim.getUniqueId());
        vanished.tick();
        assertEquals(MimicState.STARING, vanished.mimic.getState());
    }

    @Test
    @DisplayName("mounted mid-hunt, it stops and stands")
    void riderStopsTheHunt() {
        MimicRig rig = hunting(.5, 12.5);
        rig.tick();
        rig.ride();
        rig.tick();
        assertEquals(MimicState.AWAKE, rig.mimic.getState());
        assertFalse(rig.options.getGallop());
        assertNull(rig.entity.query(Pounce.class));
    }

    @Test
    @DisplayName("then it stares, head cocked, gulps, and bolts, and its prey keeps hearing it")
    void staresThenBolts() {
        MimicRig rig = hunting(.5, 12.5);
        server.players.remove(victim.getUniqueId());
        rig.tick();
        server.players.put(victim.getUniqueId(), victim);
        assertEquals(MimicState.STARING, rig.mimic.getState());
        int beats = MimicRig.heardBy(victim, MimicTerror.HEARTBEAT);
        rig.tick(Mimic.STARE_TICKS - 11);
        assertEquals(0, rig.heard(MimicTerror.GULP));
        rig.tick();
        assertEquals(1, rig.heard(MimicTerror.GULP));
        assertEquals(new Vector(0, 0, 1), rig.locomotion.getFaceDirection());
        assertEquals(beats + 2, MimicRig.heardBy(victim, MimicTerror.HEARTBEAT), "a beat every second");
        int shrieks = rig.heard(MimicTerror.SHRIEK);
        rig.tick(9);
        assertEquals(MimicState.STARING, rig.mimic.getState());
        rig.tick();
        assertEquals(MimicState.FLEEING, rig.mimic.getState());
        assertTrue(rig.options.getGallop());
        assertEquals(shrieks + 1, rig.heard(MimicTerror.SHRIEK));

        // it's gone, but they'll keep hearing it
        MimicHaunt.setup();
        Runnable haunt = server.timers().get(server.timers().size() - 1);
        for (int i = 0; i < 10; i++) haunt.run();
        assertEquals(1, MimicRig.heardBy(victim, MimicTerror.SKITTER));
    }

    @Test
    @DisplayName("ridden while it stares, it doesn't run")
    void riderStopsTheBolt() {
        MimicRig rig = hunting(.5, 12.5);
        rig.tick(Mimic.HUNT_TICKS + 1);
        rig.ride();
        rig.tickUntil(MimicState.AWAKE, Mimic.STARE_TICKS);
        assertFalse(rig.options.getGallop());
    }

    @Test
    @DisplayName("staring after something that isn't a player, nobody is haunted")
    void staringAtNothing() {
        dice.nextFloat = 0f;
        Vector[] target = {new Vector(.5, MimicRig.FLOOR, 12.5)};
        MimicRig rig = new MimicRig(server, dice).wake(null, spider -> target[0]);
        rig.tickUntil(MimicState.HUNTING, 80);
        target[0] = null;
        rig.tick();
        assertEquals(MimicState.STARING, rig.mimic.getState());
        rig.tickUntil(MimicState.FLEEING, Mimic.STARE_TICKS);
        MimicHaunt.setup();
        Runnable haunt = server.timers().get(server.timers().size() - 1);
        for (int i = 0; i < 20; i++) haunt.run();
        assertTrue(server.players.isEmpty());
    }

    @Test
    @DisplayName("hit by someone it never saw, it stares into nothing and gives up")
    void staringWithoutATarget() {
        MimicRig rig = new MimicRig(server, dice).wake(null, null);
        rig.tickUntil(MimicState.AWAKE, 20);
        Player attacker = rig.prey(.5, 12.5);
        rig.mimic.onHit(rig.entity, rig.body, attacker);
        assertEquals(MimicState.HUNTING, rig.mimic.getState());
        when(attacker.getWorld()).thenReturn(mock(World.class));
        rig.tick();
        assertEquals(MimicState.STARING, rig.mimic.getState());
        int beats = MimicRig.heardBy(attacker, MimicTerror.HEARTBEAT);
        rig.tick(20);
        assertEquals(beats + 1, MimicRig.heardBy(attacker, MimicTerror.HEARTBEAT));
        assertNull(rig.locomotion.getFaceDirection());
        rig.tickUntil(MimicState.AWAKE, Mimic.STARE_TICKS);
    }

    @Test
    @DisplayName("with its target right underneath, it faces along its own heading, or north if it points straight up")
    void targetRightUnderneath() {
        MimicRig rig = new MimicRig(server, dice).wake(null, spider -> spider.getPosition().clone());
        rig.body.getOrientation().set(new org.joml.Quaternionf().rotationY((float) Math.PI / 2));
        rig.tick();
        assertEquals(new Vector(1, 0, 0), rig.locomotion.getFaceDirection());
        rig.body.getOrientation().set(new org.joml.Quaternionf().rotationX((float) -Math.PI / 2));
        rig.tick();
        assertEquals(new Vector(0, 0, 1), rig.locomotion.getFaceDirection());
    }

    @Test
    @DisplayName("it keeps the pose it was built with")
    void keepsItsPose() {
        MimicRig rig = new MimicRig(server, dice);
        net.tfminecraft.spiders.spider.presets.MimicPose pose = new net.tfminecraft.spiders.spider.presets.MimicPose(rig.options.getBodyPlan().getBodyModel());
        assertEquals(pose, new Mimic(pose, null).getPose());
    }

    @Test
    @DisplayName("a woken mimic's body sits at chest height until it rises")
    void restingHeight() {
        MimicRig rig = woken(.5, 6.5);
        rig.tick();
        assertEquals(MimicShape.RESTING_HEIGHT, rig.options.getWalkGait().getStationary().getBodyHeight(), 1e-9);
        rig.tickUntil(MimicState.HUNTING, 80);
        assertEquals(MimicShape.GALLOP_HEIGHT, rig.options.getGallopGait().getMoving().getBodyHeight(), 1e-9);
    }
}
