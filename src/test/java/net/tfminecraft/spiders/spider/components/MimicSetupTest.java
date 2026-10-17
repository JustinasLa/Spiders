package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.LegStepEvent;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.MimicPose;
import net.tfminecraft.spiders.spider.presets.MimicPreset;
import net.tfminecraft.spiders.spider.presets.MimicShape;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** The mimic systems as the plugin wires them up: listener, timers, ticking, chunk tickets, hits, steps and shutdown. */
class MimicSetupTest {
    private MimicServer server;
    private MimicDice dice;
    private MimicWorld blocks;
    private ECS app;

    @BeforeEach
    void start() {
        server = new MimicServer();
        dice = new MimicDice();
        blocks = new MimicWorld(0);
        app = new ECS();
    }

    @AfterEach
    void stop() {
        dice.close();
        server.close();
    }

    /** A console-woken mimic standing up at ({@code x}, {@code z}). */
    private ECSEntity mimic(MimicWorld in, double x, double z) {
        SpiderOptions options = MimicPreset.mimic();
        SpiderBody body = SpiderBody.create(in.world, new Vector(x, 1 + MimicShape.RESTING_HEIGHT, z));
        return app.spawn(body, options, new Locomotion(), new Mimic(new MimicPose(options.getBodyPlan().getBodyModel()), null));
    }

    private static Mimic mimicOf(ECSEntity entity) {
        return entity.query(Mimic.class);
    }

    private void update(int times) {
        for (int i = 0; i < times; i++) app.update();
    }

    @Test
    @DisplayName("setup registers the chest listener and the lurking and haunting timers")
    void setupRegistersEverything() {
        Mimic.setupMimic(app);
        verify(server.plugins).registerEvents(any(MimicChestListener.class), eq(server.plugin));
        assertEquals(List.of(5L, 10L), server.tasks.stream().map(MimicServer.Task::period).toList());
    }

    @Test
    @DisplayName("every tick each mimic acts, holding a chunk ticket where it stands and letting go when it leaves")
    void ticksAndChunkTickets() {
        ECSEntity doomed = mimic(blocks, 90.5, 90.5);
        // removed by another system earlier in the same tick
        app.onTick(ecs -> doomed.remove());
        Mimic.setupMimic(app);
        ECSEntity entity = mimic(blocks, .5, .5);
        MimicWorld other = new MimicWorld(0);
        ECSEntity elsewhere = mimic(other, .5, .5);

        update(1);
        assertEquals(1, mimicOf(entity).getStateTicks());
        assertEquals(0, mimicOf(doomed).getStateTicks(), "a removed mimic still acted");
        verify(blocks.world).addPluginChunkTicket(0, 0, server.plugin);
        verify(other.world).addPluginChunkTicket(0, 0, server.plugin);
        verify(blocks.world, never()).addPluginChunkTicket(5, 5, server.plugin);

        update(Mimic.WAKE_TICKS);
        assertEquals(MimicState.AWAKE, mimicOf(entity).getState());
        verify(blocks.world, times(1)).addPluginChunkTicket(0, 0, server.plugin);

        entity.query(SpiderBody.class).getPosition().setX(-40.5);
        update(1);
        verify(blocks.world).addPluginChunkTicket(-3, 0, server.plugin);
        verify(blocks.world).removePluginChunkTicket(0, 0, server.plugin);

        entity.remove();
        elsewhere.remove();
        update(1);
        verify(blocks.world).removePluginChunkTicket(-3, 0, server.plugin);
        verify(other.world).removePluginChunkTicket(0, 0, server.plugin);
    }

    @Test
    @DisplayName("on shutdown each waking mimic drops back into a chest, or a warning says where one was lost")
    void shutdown() {
        Mimic.setupMimic(app);
        ECSEntity grounded = mimic(blocks, .5, .5);
        ECSEntity flying = mimic(blocks, 10.5, 10.5);
        flying.query(SpiderBody.class).getPosition().setY(30);
        ECSEntity removed = mimic(blocks, 20.5, 20.5);
        removed.remove();
        SpiderOptions options = MimicPreset.mimic();
        app.spawn(SpiderBody.create(blocks.world, new Vector(30.5, 1.5, 30.5)), options);

        Core.shutdownCoreUtils(server.plugin);

        assertTrue(MimicChests.isMimic(blocks.block(0, 1, 0)));
        assertEquals(List.of("A waking mimic at 10, 30, 10 had no room to become a chest."), server.warnings);
        assertTrue(blocks.block(20, 1, 20).getType() != Material.CHEST, "a mimic already being removed");
        assertTrue(blocks.block(30, 1, 30).getType() != Material.CHEST, "not a mimic");
        assertTrue(grounded.query(Mimic.class) != null);
    }

    @Test
    @DisplayName("hits from players and their arrows set it hunting them; other blows only make it scream")
    void hits() {
        Mimic.setupMimic(app);
        ECSEntity punched = mimic(blocks, .5, .5);
        ECSEntity shot = mimic(blocks, 40.5, .5);
        ECSEntity skeletal = mimic(blocks, 80.5, .5);
        ECSEntity mauled = mimic(blocks, 120.5, .5);
        update(Mimic.WAKE_TICKS);
        Player player = server.player(blocks.world, new Vector(.5, 1, 5), new Vector(0, 0, -1));
        Arrow arrow = mock(Arrow.class);
        when(arrow.getShooter()).thenReturn(player);
        Arrow stray = mock(Arrow.class);
        when(stray.getShooter()).thenReturn(mock(Skeleton.class));

        app.emit(new SpiderHitEvent(punched, punched.query(SpiderBody.class), player, new Vector(), true));
        app.emit(new SpiderHitEvent(shot, shot.query(SpiderBody.class), arrow, new Vector(), true));
        app.emit(new SpiderHitEvent(skeletal, skeletal.query(SpiderBody.class), stray, new Vector(), true));
        app.emit(new SpiderHitEvent(mauled, mauled.query(SpiderBody.class), mock(Zombie.class), new Vector(), true));
        // a spider that isn't a mimic is none of its business
        ECSEntity spider = app.spawn(SpiderBody.create(blocks.world, new Vector()));
        app.emit(new SpiderHitEvent(spider, spider.query(SpiderBody.class), player, new Vector(), true));

        assertEquals(MimicState.HUNTING, mimicOf(punched).getState());
        assertEquals(MimicState.HUNTING, mimicOf(shot).getState());
        assertEquals(MimicState.AWAKE, mimicOf(skeletal).getState());
        assertEquals(MimicState.AWAKE, mimicOf(mauled).getState());
        verify(blocks.world, times(4)).playSound(any(Location.class), eq("tfmc_spiders:" + MimicTerror.SHRIEK), eq(SoundCategory.HOSTILE), eq(2.5f), anyFloat());
    }

    @Test
    @DisplayName("galloping, its wooden feet drum the ground")
    void legSteps() {
        Mimic.setupMimic(app);
        ECSEntity entity = mimic(blocks, .5, .5);
        SpiderBody body = entity.query(SpiderBody.class);
        Leg leg = mock(Leg.class);
        when(leg.getEndEffector()).thenReturn(new Vector(1, 1, 2));

        app.emit(new LegStepEvent(entity, body, leg));
        verify(blocks.world, never()).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());

        entity.query(SpiderOptions.class).setGallop(true);
        app.emit(new LegStepEvent(entity, body, leg));
        verify(blocks.world).playSound(eq(new Location(blocks.world, 1, 1, 2)), eq("tfmc_spiders:" + MimicTerror.SKITTER), eq(SoundCategory.HOSTILE), eq(.5f), anyFloat());

        // not a mimic, or a mimic with no options: silent
        ECSEntity spider = app.spawn(body, MimicPreset.mimic());
        spider.query(SpiderOptions.class).setGallop(true);
        app.emit(new LegStepEvent(spider, body, leg));
        ECSEntity bare = new ECSEntity();
        bare.addComponent(mimicOf(entity));
        app.emit(new LegStepEvent(bare, body, leg));
        verify(blocks.world, times(1)).playSound(any(Location.class), anyString(), any(SoundCategory.class), anyFloat(), anyFloat());
        verify(blocks.world, never()).addPluginChunkTicket(anyInt(), anyInt(), any());
    }
}
