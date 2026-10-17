package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.MimicPose;
import net.tfminecraft.spiders.spider.presets.MimicPreset;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.mockito.invocation.Invocation;

import java.util.List;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

/**
 * One awake mimic standing on the floor of a {@link MimicWorld}, ticked by hand: the test moves its body
 * and its prey, and reads what it asked its legs to do from its {@link Locomotion}.
 */
final class MimicRig {
    /** Floor level: the ground is solid up to y = 0. */
    static final double FLOOR = 1.0;

    final MimicServer server;
    final MimicDice dice;
    final MimicWorld blocks = new MimicWorld(0);
    final ECS app = new ECS();
    final SpiderOptions options = MimicPreset.mimic();
    final SpiderBody body;
    final Locomotion locomotion = new Locomotion();
    ECSEntity entity;
    Mimic mimic;
    /** Give the mimic no legs to steer with. */
    boolean legless = false;

    /** A dormant chest's body at (.5, .5); {@link #wake} makes the mimic. */
    MimicRig(MimicServer server, MimicDice dice) {
        this.server = server;
        this.dice = dice;
        Mimic.applyMimicRise(options, 0.0);
        body = SpiderBody.create(blocks.world, new Vector(.5, FLOOR + MimicShape.RESTING_HEIGHT, .5));
    }

    /** Woken by {@code waker}, going after {@code threat} if given. */
    MimicRig wake(UUID waker, Function<SpiderBody, Vector> threat) {
        mimic = new Mimic(new MimicPose(options.getBodyPlan().getBodyModel()), waker, threat);
        entity = app.spawn(body, options, locomotion, mimic);
        return this;
    }

    /** A frightenable player in this mimic's world, on the floor at ({@code x}, {@code z}), looking at the mimic. */
    Player prey(double x, double z) {
        Vector feet = new Vector(x, FLOOR, z);
        Player player = server.player(blocks.world, feet, body.getPosition().clone().subtract(feet).setY(0).normalize());
        blocks.players.add(player);
        return player;
    }

    void tick() {
        locomotion.clear();
        mimic.update(entity, body, options, legless ? null : locomotion);
    }

    void tick(int times) {
        for (int i = 0; i < times; i++) tick();
    }

    /** Tick until it enters {@code state}, failing after {@code limit} ticks. */
    int tickUntil(MimicState state, int limit) {
        for (int i = 1; i <= limit; i++) {
            tick();
            if (mimic.getState() == state) return i;
        }
        assertEquals(state, mimic.getState(), "after " + limit + " ticks");
        return -1;
    }

    /** Shift its body to ({@code x}, {@code z}), keeping its height. */
    void moveTo(double x, double z) {
        body.getPosition().setX(x).setZ(z);
    }

    /** Someone climbs on. */
    Player ride() {
        Mountable mountable = entity.query(Mountable.class);
        if (mountable == null) {
            mountable = new Mountable();
            entity.addComponent(mountable);
        }
        ArmorStand marker = mock(ArmorStand.class);
        Player rider = mock(Player.class);
        when(marker.getPassengers()).thenReturn(List.<Entity>of(rider));
        mountable.setCurrentMarker(marker);
        return rider;
    }

    /** They climb off again. */
    void dismount() {
        entity.query(Mountable.class).setCurrentMarker(null);
    }

    /** How many times anyone in this world heard the pack sound {@code sound}. */
    int heard(String sound) {
        return count(blocks.world, sound);
    }

    /** How many times {@code player} alone heard the pack sound {@code sound}. */
    static int heardBy(Player player, String sound) {
        return count(player, sound);
    }

    private static int count(Object mock, String sound) {
        int n = 0;
        for (Invocation it : mockingDetails(mock).getInvocations()) {
            if (it.getMethod().getName().equals("playSound") && ("tfmc_spiders:" + sound).equals(it.getArguments()[1])) n++;
        }
        return n;
    }

    /** How many times {@code method} was called on {@code mock}. */
    static int calls(Object mock, String method) {
        return (int) mockingDetails(mock).getInvocations().stream().filter(it -> it.getMethod().getName().equals(method)).count();
    }
}
