package net.tfminecraft.spiders.utilities.overloads;

import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.Iterator;
import java.util.function.Consumer;

public final class Overloads {
    private Overloads() {
    }


    public static void sendActionBarOrMessage(CommandSender sender, String message) {
        if (sender instanceof Player player) {
            player.sendActionBar(Component.text(message));
        } else {
            sender.sendMessage(message);
        }
    }

    public static void sendDebugActionBar(String message) {
        Iterator<? extends Player> players = Bukkit.getOnlinePlayers().iterator();
        if (players.hasNext()) players.next().sendActionBar(Component.text(message));
    }

    public static void sendDebugChatMessage(String message) {
        Iterator<? extends Player> players = Bukkit.getOnlinePlayers().iterator();
        if (players.hasNext()) players.next().sendMessage(message);
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
