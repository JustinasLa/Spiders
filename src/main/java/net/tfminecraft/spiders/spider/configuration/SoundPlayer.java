package net.tfminecraft.spiders.spider.configuration;

import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.util.Vector;

import java.util.concurrent.ThreadLocalRandom;

public class SoundPlayer {
    private final Sound sound;
    private final float volume;
    private final float pitch;
    private final float volumeVary;
    private final float pitchVary;

    public SoundPlayer(Sound sound, float volume, float pitch, float volumeVary, float pitchVary) {
        this.sound = sound;
        this.volume = volume;
        this.pitch = pitch;
        this.volumeVary = volumeVary;
        this.pitchVary = pitchVary;
    }

    public SoundPlayer(Sound sound, float volume, float pitch, float volumeVary) {
        this(sound, volume, pitch, volumeVary, 0.1f);
    }

    public SoundPlayer(Sound sound, float volume, float pitch) {
        this(sound, volume, pitch, 0.1f, 0.1f);
    }

    public Sound getSound() {
        return sound;
    }

    public float getVolume() {
        return volume;
    }

    public float getPitch() {
        return pitch;
    }

    public float getVolumeVary() {
        return volumeVary;
    }

    public float getPitchVary() {
        return pitchVary;
    }

    public void play(World world, Vector position) {
        float volume = this.volume + ThreadLocalRandom.current().nextFloat() * volumeVary;
        float pitch = this.pitch + ThreadLocalRandom.current().nextFloat() * pitchVary;
        world.playSound(position.toLocation(world), sound, volume, pitch);
    }

    public SoundPlayer scale(float volume, float pitch) {
        return new SoundPlayer(
            sound, this.volume * volume, this.pitch * pitch, volumeVary * volume, pitchVary * pitch);
    }

    public SoundPlayer scale(float volume) {
        return scale(volume, 1f);
    }

    public SoundPlayer scale() {
        return scale(1f, 1f);
    }

    public SoundPlayer withSound(Sound sound) {
        return new SoundPlayer(sound, volume, pitch, volumeVary, pitchVary);
    }
}
