package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.LegStepEvent;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.components.body.SpiderBodyHitGroundEvent;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.util.Vector;

import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.random.RandomGenerator;

public class SoundsAndParticles implements ECSComponent {
    private int timeSinceLastSound = 0;
    private WeakHashMap<Leg, Integer> wetness = new WeakHashMap<>();
    private final int maxWetness = 20 * 3;
    /** Splash pitches and drips; tests pass a seeded source. */
    private final RandomGenerator random;

    public SoundsAndParticles() {
        this(() -> ThreadLocalRandom.current().nextLong());
    }

    SoundsAndParticles(RandomGenerator random) {
        this.random = random;
    }

    public int getTimeSinceLastSound() {
        return timeSinceLastSound;
    }

    public void setTimeSinceLastSound(int timeSinceLastSound) {
        this.timeSinceLastSound = timeSinceLastSound;
    }

    public WeakHashMap<Leg, Integer> getWetness() {
        return wetness;
    }

    public void setWetness(WeakHashMap<Leg, Integer> wetness) {
        this.wetness = wetness;
    }

    public int getMaxWetness() {
        return maxWetness;
    }

    public static void setupSoundAndParticles(ECS app) {
        app.onEvent(SpiderBodyHitGroundEvent.class, event -> {
            Overloads.playSound(event.getSpider().getWorld(), event.getSpider().getPosition(), Sound.ENTITY_SPIDER_STEP, .7f, .6f);
        });

        app.onEvent(SpiderHitEvent.class, event -> {
            // a flinch for real hits, a dull glance off the carapace for weak ones
            if (event.getForceful()) Overloads.playSound(event.getSpider().getWorld(), event.getPoint(), Sound.ENTITY_SPIDER_HURT, .5f, .8f);
            else Overloads.playSound(event.getSpider().getWorld(), event.getPoint(), Sound.ENTITY_ARROW_HIT, .6f, .7f);
        });

        app.onEvent(CloakToggleEvent.class, event -> {
            Overloads.playSound(event.getSpider().getWorld(), event.getSpider().getPosition(), Sound.BLOCK_SCULK_PLACE, .5f, .7f);
        });

        app.onEvent(CloakDamageEvent.class, event -> {
            Overloads.playSound(event.getSpider().getWorld(), event.getSpider().getPosition(), Sound.BLOCK_SCULK_SHRIEKER_BREAK, .4f, .8f);
        });

        app.onEvent(LegStepEvent.class, event -> {
            World world = event.getSpider().getWorld();
            Vector position = event.getLeg().getEndEffector();

            SpiderOptions options = event.getEntity().query(SpiderOptions.class);
            if (options == null) return;

            boolean isUnderwater = world.getBlockAt(position.toLocation(world)).isLiquid();
            float volumeScale = isUnderwater ? 0.5f : 1.0f;
            float pitchScale = isUnderwater ? 0.75f : 1.0f;

            options.getSound().getStep().scale(volumeScale, pitchScale).play(world, position);

            Sound blockSound = blockStepSound(world, position);
            if (blockSound != null) options.getSound().getStepBlockOverlay()
                .withSound(blockSound)
                .scale(volumeScale, pitchScale)
                .play(world, position);
        });

        app.onTick(ecs -> {
            for (Pair<SpiderBody, SoundsAndParticles> it : app.query(SpiderBody.class, SoundsAndParticles.class)) {
                SpiderBody spider = it.first();
                SoundsAndParticles sounds = it.second();
                sounds.timeSinceLastSound++;

                for (Leg leg : spider.getLegs()) {
                    spawnLegParticles(sounds, spider.getWorld(), leg);
                }
            }
        });
    }

    static boolean isInWater(World world, Vector position) {
        Block block = world.getBlockAt(position.toLocation(world));
        return block.isLiquid() || (block.getBlockData() instanceof Waterlogged waterlogged && waterlogged.isWaterlogged());
    }


    private static void spawnLegParticles(SoundsAndParticles sounds, World world, Leg leg) {
        boolean justBegunMoving = leg.isMoving() && leg.getTimeSinceBeginMove() < 1;
        boolean wasUnderWater = isInWater(world, leg.getPreviousEndEffector());
        boolean isUnderWater = isInWater(world, leg.getEndEffector());
        boolean justEnteredWater = isUnderWater && !wasUnderWater;
        boolean justExitedWater = !isUnderWater && wasUnderWater;

        if (isUnderWater) sounds.wetness.put(leg, sounds.maxWetness);
        else {
            Integer current = sounds.wetness.get(leg);
            sounds.wetness.put(leg, (current != null ? current : 1) - 1);
        }

        // sound
        if (sounds.timeSinceLastSound > 20) {
            if (justEnteredWater) {
                float volume = .3f;
                float pitch = 1.0f + sounds.random.nextFloat() * 0.1f;
                Overloads.playSound(world, leg.getEndEffector(), Sound.ENTITY_PLAYER_SPLASH, volume, pitch);
                sounds.timeSinceLastSound = 0;
            }
            else if (justExitedWater) {
                float volume = .3f;
                float pitch = 1f + sounds.random.nextFloat() * 0.1f;
                Overloads.playSound(world, leg.getEndEffector(), Sound.AMBIENT_UNDERWATER_EXIT, volume, pitch);
                sounds.timeSinceLastSound = 0;
            }
            else if (justBegunMoving && isUnderWater) {
                float volume = .3f;
                float pitch = .7f + sounds.random.nextFloat() * 0.1f;
                Overloads.playSound(world, leg.getEndEffector(), Sound.ENTITY_PLAYER_SWIM, volume, pitch);
                sounds.timeSinceLastSound = 0;
            }
        }


        // particles
        // set for this leg just above
        int wetness = sounds.wetness.get(leg);
        for (Vector segment : leg.getIk().getSegmentTipPositions()) {
            boolean segmentIsUnderWater = isInWater(world, segment);

            Location location = segment.toLocation(world).add(.0, -.1, .0);

            if (segmentIsUnderWater) {
                if (justEnteredWater || justExitedWater) {
                    double offset = .3;
                    world.spawnParticle(Particle.SPLASH, location, 40, offset, .1, offset);
                } else if (leg.isMoving()) {
                    double offset = .1;
                    world.spawnParticle(Particle.BUBBLE, location, 1, offset, .0, offset, .0);
                }
            }

            if (wetness > 0) {
                if (sounds.random.nextInt(0, sounds.maxWetness) > wetness) continue;

                double offset = .0;
                world.spawnParticle(Particle.FALLING_WATER, location, 1, offset, offset, offset, .1);
            }
        }
    }

    public static Sound blockStepSound(World world, Vector position) {
        Block block = world.getBlockAt(position.toLocation(world).subtract(0.0, 1.0e-3, 0.0));
        if (block.isEmpty()) return null;

        if (block.getType() == Material.GRASS_BLOCK) return Sound.BLOCK_GRASS_BREAK;

        Sound sound = block.getBlockSoundGroup().getHitSound();
        if (String.valueOf(Registry.SOUNDS.getKey(sound)).equals("minecraft:intentionally_empty")) return null;

        return sound;
    }
}
