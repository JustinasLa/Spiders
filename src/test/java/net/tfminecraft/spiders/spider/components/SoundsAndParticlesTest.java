package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.LegStepEvent;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderBodyHitGroundEvent;
import net.tfminecraft.spiders.spider.components.body.SpiderLegIK;
import net.tfminecraft.spiders.spider.configuration.SoundOptions;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundGroup;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Arrow;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;
import java.util.WeakHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SoundsAndParticlesTest {
    /** Water below y = 0, a stone floor below y = -5; the ground at y = 10 is the material {@link #ground}. */
    private Material ground = Material.STONE;
    private final Sound groundHit = Sound.BLOCK_STONE_HIT;
    private final World world = world();

    private World world() {
        World world = mock(World.class);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> {
            Location location = call.getArgument(0);
            Block block = mock(Block.class);
            boolean water = location.getY() < 0 && location.getY() >= -5;
            when(block.isLiquid()).thenReturn(water);
            BlockData data = ComponentServer.blockData(water ? Material.WATER : Material.AIR);
            when(block.getBlockData()).thenReturn(data);
            boolean floor = location.getY() >= 9 && location.getY() < 10;
            when(block.isEmpty()).thenReturn(!floor);
            when(block.getType()).thenReturn(floor ? ground : Material.AIR);
            SoundGroup group = mock(SoundGroup.class);
            when(group.getHitSound()).thenReturn(ground == Material.BARRIER ? Sound.INTENTIONALLY_EMPTY : groundHit);
            when(block.getBlockSoundGroup()).thenReturn(group);
            return block;
        });
        return world;
    }

    private void verifySound(Sound sound, int times) {
        verify(world, times(times)).playSound(any(Location.class), eq(sound), anyFloat(), anyFloat());
    }

    private record Scene(ECS app, ECSEntity entity, SpiderBody spider) {
    }

    private Scene scene(boolean withOptions) {
        ECS app = new ECS();
        SoundsAndParticles.setupSoundAndParticles(app);
        SpiderBody spider = SpiderBody.create(world, new Vector(.5, 12.0, .5));
        SpiderOptions options = mock(SpiderOptions.class);
        when(options.getSound()).thenReturn(new SoundOptions());
        ECSEntity entity = withOptions ? app.spawn(spider, options) : app.spawn(spider);
        return new Scene(app, entity, spider);
    }

    private static Leg foot(Vector at) {
        Leg leg = mock(Leg.class);
        when(leg.getEndEffector()).thenReturn(at);
        return leg;
    }

    @Test
    @DisplayName("landing, hits and the cloak each make their sound")
    void landingHitsAndTheCloakEachMakeTheirSound() {
        Scene scene = scene(true);
        scene.app().emit(new SpiderBodyHitGroundEvent(scene.spider()));
        verifySound(Sound.ENTITY_SPIDER_STEP, 1);
        scene.app().emit(new SpiderHitEvent(scene.entity(), scene.spider(), mock(Arrow.class), new Vector(), true));
        verifySound(Sound.ENTITY_SPIDER_HURT, 1);
        scene.app().emit(new SpiderHitEvent(scene.entity(), scene.spider(), mock(Arrow.class), new Vector(), false));
        verifySound(Sound.ENTITY_ARROW_HIT, 1);
        scene.app().emit(new CloakToggleEvent(scene.entity(), scene.spider()));
        verifySound(Sound.BLOCK_SCULK_PLACE, 1);
        scene.app().emit(new CloakDamageEvent(scene.entity(), scene.spider(), new Cloak()));
        verifySound(Sound.BLOCK_SCULK_SHRIEKER_BREAK, 1);
    }

    @Test
    @DisplayName("a step sounds of the spider and of the block stepped on, muffled under water")
    void aStepSoundsOfTheSpiderAndOfTheBlock() {
        Scene scene = scene(true);
        Sound step = new SoundOptions().getStep().getSound();
        scene.app().emit(new LegStepEvent(scene.entity(), scene.spider(), foot(new Vector(.5, 10.0, .5))));
        verifySound(step, 1);
        verifySound(groundHit, 1);

        ground = Material.GRASS_BLOCK;
        scene.app().emit(new LegStepEvent(scene.entity(), scene.spider(), foot(new Vector(.5, 10.0, .5))));
        verifySound(Sound.BLOCK_GRASS_BREAK, 1);

        // a silent block and empty air add nothing
        ground = Material.BARRIER;
        scene.app().emit(new LegStepEvent(scene.entity(), scene.spider(), foot(new Vector(.5, 10.0, .5))));
        scene.app().emit(new LegStepEvent(scene.entity(), scene.spider(), foot(new Vector(.5, 20.0, .5))));
        verifySound(step, 4);
        verifySound(groundHit, 1);

        // under water the step is quieter and lower
        clearInvocations(world);
        scene.app().emit(new LegStepEvent(scene.entity(), scene.spider(), foot(new Vector(.5, -2.0, .5))));
        SoundOptions defaults = new SoundOptions();
        float maxVolume = (defaults.getStep().getVolume() + defaults.getStep().getVolumeVary()) * .5f;
        verify(world).playSound(any(Location.class), eq(step), org.mockito.ArgumentMatchers.floatThat(volume -> volume <= maxVolume), anyFloat());

        // without options there is nothing to play
        Scene bare = scene(false);
        clearInvocations(world);
        bare.app().emit(new LegStepEvent(bare.entity(), bare.spider(), foot(new Vector(.5, 10.0, .5))));
        verify(world, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());
    }

    @Test
    @DisplayName("the block sound is what the ground under the foot sounds like")
    void theBlockSoundIsWhatTheGroundUnderTheFootSoundsLike() {
        assertSame(groundHit, SoundsAndParticles.blockStepSound(world, new Vector(.5, 10.0, .5)));
        assertNull(SoundsAndParticles.blockStepSound(world, new Vector(.5, 30.0, .5)));
        ground = Material.BARRIER;
        assertNull(SoundsAndParticles.blockStepSound(world, new Vector(.5, 10.0, .5)));
    }

    /** A leg moving through the scene; the test sets where its foot was and is. */
    private static final class Wader {
        Vector previous = new Vector(.5, 2.0, .5);
        Vector current = new Vector(.5, 2.0, .5);
        boolean moving = false;
        int sinceBeginMove = 5;
        final Leg leg = mock(Leg.class);

        Wader() {
            when(leg.getPreviousEndEffector()).thenAnswer(call -> previous);
            when(leg.getEndEffector()).thenAnswer(call -> current);
            when(leg.isMoving()).thenAnswer(call -> moving);
            when(leg.getTimeSinceBeginMove()).thenAnswer(call -> sinceBeginMove);
            SpiderLegIK ik = mock(SpiderLegIK.class);
            when(ik.getSegmentTipPositions()).thenAnswer(call -> List.of(current.clone(), current.clone().add(new Vector(0.0, .5, 0.0))));
            when(leg.getIk()).thenReturn(ik);
        }

        void to(double y) {
            previous = current;
            current = new Vector(.5, y, .5);
        }
    }

    private void verifyParticles(Particle particle) {
        verify(world, atLeastOnce()).spawnParticle(eq(particle), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble());
    }

    private void verifyParticles(Particle particle, int times) {
        verify(world, times(times)).spawnParticle(eq(particle), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());
    }

    @Test
    @DisplayName("legs splash into and out of water, swim, bubble and drip as they dry")
    void legsSplashSwimBubbleAndDrip() {
        Scene scene = scene(true);
        Wader wader = new Wader();
        scene.spider().setLegs(List.of(wader.leg));
        SoundsAndParticles sounds = new SoundsAndParticles(new SplittableRandom(7));
        scene.entity().addComponent(sounds);
        scene.app().update();
        assertEquals(1, sounds.getTimeSinceLastSound());
        assertEquals(0, sounds.getWetness().get(wader.leg), "A dry leg stays dry");
        verify(world, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());

        // too soon after the last sound: a silent splash
        wader.to(-1.0);
        scene.app().update();
        verifySound(Sound.ENTITY_PLAYER_SPLASH, 0);
        verifyParticles(Particle.SPLASH);
        assertEquals(sounds.getMaxWetness(), sounds.getWetness().get(wader.leg));

        sounds.setTimeSinceLastSound(100);
        wader.to(3.0);
        wader.to(-1.0);
        scene.app().update();
        verifySound(Sound.ENTITY_PLAYER_SPLASH, 1);
        assertEquals(0, sounds.getTimeSinceLastSound());

        // standing in the water, then taking a step
        sounds.setTimeSinceLastSound(100);
        wader.to(-1.2);
        scene.app().update();
        verifySound(Sound.ENTITY_PLAYER_SWIM, 0);
        wader.moving = true;
        wader.to(-1.4);
        scene.app().update();
        verifySound(Sound.ENTITY_PLAYER_SWIM, 0);
        verifyParticles(Particle.BUBBLE, 2);
        wader.sinceBeginMove = 0;
        wader.to(-1.6);
        scene.app().update();
        verifySound(Sound.ENTITY_PLAYER_SWIM, 1);
        wader.moving = false;

        // climbing out, then dripping until dry
        sounds.setTimeSinceLastSound(100);
        wader.to(2.0);
        scene.app().update();
        verifySound(Sound.AMBIENT_UNDERWATER_EXIT, 1);
        for (int i = 0; i < 80; i++) {
            wader.to(2.0);
            scene.app().update();
        }
        int drips = countFalling();
        assertEquals(true, drips > 0 && drips < 2 * 87, "Wet legs should drip now and then, not every tick: " + drips);
        int wetness = sounds.getWetness().get(wader.leg);
        assertEquals(true, wetness < 0, "Still wet: " + wetness);
        clearInvocations(world);
        scene.app().update();
        verify(world, never()).spawnParticle(eq(Particle.FALLING_WATER), any(Location.class), anyInt(), anyDouble(), anyDouble(), anyDouble(), anyDouble());

        WeakHashMap<Leg, Integer> fresh = new WeakHashMap<>();
        sounds.setWetness(fresh);
        assertSame(fresh, sounds.getWetness());
    }

    @Test
    @DisplayName("a dry step is silent, and a foot pulled out splashes where the leg is still under")
    void aDryStepIsSilentAndAFootPulledOutSplashesWhereTheLegIsStillUnder() {
        Scene scene = scene(true);
        Wader wader = new Wader();
        scene.spider().setLegs(List.of(wader.leg));
        // the real random source: nothing asserted here depends on it
        SoundsAndParticles sounds = new SoundsAndParticles();
        sounds.setTimeSinceLastSound(100);
        scene.entity().addComponent(sounds);
        wader.moving = true;
        wader.sinceBeginMove = 0;
        scene.app().update();
        verify(world, never()).playSound(any(Location.class), any(Sound.class), anyFloat(), anyFloat());

        wader.moving = false;
        wader.to(-1.0);
        // the foot comes out just above the surface; the lower tip is still in the water
        wader.to(.1);
        SpiderLegIK ik = wader.leg.getIk();
        when(ik.getSegmentTipPositions()).thenReturn(List.of(new Vector(.5, -.5, .5)));
        clearInvocations(world);
        scene.app().update();
        verifySound(Sound.AMBIENT_UNDERWATER_EXIT, 1);
        verifyParticles(Particle.SPLASH);
    }

    private int countFalling() {
        return (int) org.mockito.Mockito.mockingDetails(world).getInvocations().stream()
            .filter(it -> it.getMethod().getName().equals("spawnParticle") && it.getArgument(0) == Particle.FALLING_WATER)
            .count();
    }
}
