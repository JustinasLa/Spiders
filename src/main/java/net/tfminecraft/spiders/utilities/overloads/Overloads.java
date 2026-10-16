package net.tfminecraft.spiders.utilities.overloads;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.util.function.Consumer;

public final class Overloads {
    private Overloads() {
    }


    public static <T extends Entity> T spawnEntity(World world, Vector position, Class<T> clazz, Consumer<T> initializer) {
        return world.spawn(position.toLocation(world), clazz, initializer);
    }

    public static void playSound(World world, Vector position, Sound sound, float volume, float pitch) {
        world.playSound(position.toLocation(world), sound, volume, pitch);
    }

    public static Vector getPosition(Entity entity) {
        return entity.getLocation().toVector();
    }

    public static Vector getEyePosition(LivingEntity entity) {
        return entity.getEyeLocation().toVector();
    }

    public static Vector getDirection(Entity entity) {
        return entity.getLocation().getDirection();
    }

    public static float getYaw(Entity entity) {
        return entity.getLocation().getYaw();
    }

    public static float getPitch(Entity entity) {
        return entity.getLocation().getPitch();
    }

    public static float yawRadians(Entity entity) {
        return Maths.yawRadians(entity.getLocation());
    }

    public static float pitchRadians(Entity entity) {
        return Maths.pitchRadians(entity.getLocation());
    }
}
