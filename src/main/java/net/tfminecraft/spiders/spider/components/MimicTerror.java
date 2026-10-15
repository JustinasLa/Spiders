package net.tfminecraft.spiders.spider.components;

import io.papermc.paper.entity.LookAnchor;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Pair;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Everything a mimic does to the people near it: its own sounds from the `tfmc_spiders` pack (layered
 * over vanilla ones, so players without the pack still hear something awful), the jump scare, bites,
 * the victim's heartbeat and the dark that closes in while it hunts. Sounds are played by key, which
 * needs no server registry.
 */
public final class MimicTerror {
    private MimicTerror() {
    }

    public static final String SCREECH = "mimic.screech";
    public static final String SHRIEK = "mimic.shriek";
    public static final String STINGER = "mimic.stinger";
    public static final String CHOMP = "mimic.chomp";
    public static final String CRACK = "mimic.crack";
    public static final String BREATH = "mimic.breath";
    public static final String GROWL = "mimic.growl";
    public static final String HEARTBEAT = "mimic.heartbeat";
    public static final String CREAK = "mimic.creak";
    public static final String SKITTER = "mimic.skitter";
    public static final String GULP = "mimic.gulp";

    /** Pack sound event key. */
    public static String key(String sound) {
        return "tfmc_spiders:" + sound;
    }

    public static void play(World world, Vector position, String sound, float volume, float pitch) {
        world.playSound(position.toLocation(world), key(sound), SoundCategory.HOSTILE, volume, pitch);
    }

    /** Only {@code player} hears it, from {@code position}. */
    public static void playTo(Player player, Vector position, String sound, float volume, float pitch) {
        player.playSound(position.toLocation(player.getWorld()), key(sound), SoundCategory.HOSTILE, volume, pitch);
    }

    private static final double SCREECH_RANGE = 80.0;
    private static final List<Pair<String, Float>> SCREECH_LAYERS = List.of(
        new Pair<>("minecraft:entity.fox.screech", .7f),
        new Pair<>("minecraft:entity.ghast.scream", 1.6f),
        new Pair<>("minecraft:entity.enderman.scream", .8f),
        new Pair<>("minecraft:entity.phantom.death", .6f)
    );

    /**
     * A sound can't play louder than volume 1 (higher only widens its range), so the screech is
     * layered, and each player within range also hears it at full volume from just beside them,
     * towards the mimic, so it stays loud far off and still comes from the right direction.
     */
    public static void screech(World world, Vector centre, float pitch) {
        Location location = centre.toLocation(world);
        world.playSound(location, key(SCREECH), SoundCategory.HOSTILE, 5f, pitch);
        for (Pair<String, Float> entry : SCREECH_LAYERS) world.playSound(location, entry.first(), SoundCategory.HOSTILE, 5f, entry.second() * pitch);
        for (Player player : world.getPlayers()) {
            Vector ear = player.getEyeLocation().toVector();
            Vector toMimic = centre.clone().subtract(ear);
            double distance = toMimic.length();
            if (distance > SCREECH_RANGE) continue;
            Location source = (distance < 2 ? centre : ear.add(toMimic.multiply(2 / distance))).toLocation(world);
            for (int i = 0; i < 2; i++) player.playSound(source, key(SCREECH), SoundCategory.HOSTILE, 1f, pitch);
            for (Pair<String, Float> entry : SCREECH_LAYERS) player.playSound(source, entry.first(), SoundCategory.HOSTILE, .6f, entry.second() * pitch);
        }
    }

    /** A shorter cry: lunges, bites and pain. */
    public static void shriek(World world, Vector position, float pitch) {
        play(world, position, SHRIEK, 2.5f, pitch);
        world.playSound(position.toLocation(world), "minecraft:entity.fox.screech", SoundCategory.HOSTILE, 1.5f, 1.3f * pitch);
    }

    /** The lid slamming shut on something. */
    public static void chomp(World world, Vector position, float volume) {
        float pitch = .9f + ThreadLocalRandom.current().nextFloat() * .25f;
        play(world, position, CHOMP, volume, pitch);
        world.playSound(position.toLocation(world), "minecraft:block.chest.close", SoundCategory.HOSTILE, volume * .7f, 1.3f);
        world.playSound(position.toLocation(world), "minecraft:entity.evoker_fangs.attack", SoundCategory.HOSTILE, volume * .35f, 1.6f);
    }

    /** Is this player someone a mimic can frighten or hurt? */
    public static boolean isPrey(Player player) {
        return player != null && player.isOnline() && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }

    // Titles draw at 4x scale; the pack's mimic_scare font has the same picture at three sizes.
    private static final Key SCARE_FONT = Key.key("tfmc_spiders", "mimic_scare");
    private static final String[] SCARE_FRAMES = {"", "", ""};

    /** Wrench their view onto {@code target}: they look into its mouth whether they want to or not. */
    public static void stareInto(Player player, Vector target) {
        player.lookAt(target.getX(), target.getY() + .2, target.getZ(), LookAnchor.EYES);
    }

    /** The face of the thing, flung at the screen: three frames growing closer, then gone. */
    public static void jumpScare(Player player) {
        playTo(player, player.getEyeLocation().toVector(), STINGER, 1f, 1f);
        player.playSound(player.getEyeLocation(), "minecraft:entity.warden.sonic_boom", SoundCategory.HOSTILE, .35f, 1.8f);
        // the camera jolts as it hits
        for (int shake = 0; shake < 3; shake++) {
            float yaw = shake * 120f;
            Bukkit.getScheduler().runTaskLater(Core.getCurrentPlugin(), () -> {
                if (player.isOnline()) player.sendHurtAnimation(yaw);
            }, shake * 3L);
        }
        for (int frame = 0; frame < SCARE_FRAMES.length; frame++) {
            Component face = Component.text(SCARE_FRAMES[frame]).font(SCARE_FONT).color(NamedTextColor.WHITE);
            boolean last = frame == SCARE_FRAMES.length - 1;
            Title title = Title.title(face, Component.empty(), Title.Times.times(Duration.ZERO, Duration.ofMillis(last ? 250 : 150), Duration.ofMillis(last ? 300 : 0)));
            Bukkit.getScheduler().runTaskLater(Core.getCurrentPlugin(), () -> {
                if (player.isOnline()) player.showTitle(title);
            }, frame * 2L);
        }
    }

    /** Bites hurt, shake the camera and blind for a moment, but never kill: the victim is left on a heart. */
    public static void bite(Player player, Vector mouth, double damage) {
        World world = player.getWorld();
        Vector away = player.getLocation().toVector().subtract(mouth).setY(0);
        if (away.lengthSquared() < 1e-6) away = player.getLocation().getDirection().multiply(-1).setY(0);
        if (away.lengthSquared() < 1e-6) away = new Vector(0, 0, 1);

        chomp(world, mouth, 2f);
        stareInto(player, mouth);
        play(world, mouth, GULP, 1f, .8f + ThreadLocalRandom.current().nextFloat() * .3f);
        boolean hurtable = player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE;
        double dealt = hurtable ? biteDamage(player.getHealth(), player.getAbsorptionAmount(), damage) : 0.0;
        if (dealt > 0) player.damage(dealt);
        else player.sendHurtAnimation(0f);
        player.setVelocity(away.normalize().multiply(.75).setY(.42));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 4, 0, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 15, 2, false, false, false));
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 12, 0, false, false, false));

        Location chest = player.getLocation().add(0, 1.1, 0);
        world.spawnParticle(Particle.BLOCK, chest, 40, .3, .4, .3, 0, Material.REDSTONE_BLOCK.createBlockData());
        world.spawnParticle(Particle.DAMAGE_INDICATOR, chest, 6, .3, .3, .3, .1);
        world.spawnParticle(Particle.BLOCK, mouth.toLocation(world), 12, .25, .1, .25, 0, Material.OAK_PLANKS.createBlockData());
    }

    /** As much of {@code damage} as leaves {@link #MIN_HEALTH_AFTER_BITE}; 0 if they're already that low. */
    public static double biteDamage(double health, double absorption, double damage) {
        return Math.max(0.0, Math.min(damage, health + absorption - MIN_HEALTH_AFTER_BITE));
    }

    /** Health a bite always leaves (one heart). */
    public static final double MIN_HEALTH_AFTER_BITE = 2.0;

    /** The hunted hear their own heart: faster the closer it gets. */
    public static int heartbeatInterval(double distance) {
        double closeness = Math.clamp(1 - (distance - 2) / 22, 0, 1);
        return (int) Math.round(26 - 17 * closeness);
    }

    public static void heartbeat(Player player, double distance) {
        float volume = (float) Math.clamp(1.4 - distance / 30, .5, 1.2);
        playTo(player, player.getLocation().toVector().add(new Vector(0, 1, 0)), HEARTBEAT, volume, 1f);
        if (distance < 10) player.playSound(player.getLocation(), "minecraft:entity.warden.heartbeat", SoundCategory.HOSTILE, .5f, 1.2f);
    }

    /** The world dims around the hunted, in the warden's slow pulses. */
    public static void darken(Player player) {
        PotionEffect current = player.getPotionEffect(PotionEffectType.DARKNESS);
        if (current == null || current.getDuration() < 30) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 4, 0, false, false, false));
        }
    }
}
