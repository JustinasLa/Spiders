package net.tfminecraft.spiders.utilities;

import net.tfminecraft.spiders.utilities.events.Scheduler;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.minecart.CommandMinecart;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public final class Core {
    private Core() {
    }

    private static JavaPlugin currentPlugin;
    private static int currentTick = 0;

    private static final List<Disposable> closeableList = new ArrayList<>();

    public static JavaPlugin getCurrentPlugin() {
        if (currentPlugin == null) throw new IllegalStateException("lateinit property currentPlugin has not been initialized");
        return currentPlugin;
    }

    public static void setCurrentPlugin(JavaPlugin plugin) {
        currentPlugin = plugin;
    }

    public static int getCurrentTick() {
        return currentTick;
    }

    public static void setCurrentTick(int tick) {
        currentTick = tick;
    }

    public static boolean onPluginShutdown(Runnable task) {
        return closeableList.add(task::run);
    }

    public static void setupCoreUtils(JavaPlugin plugin) {
        currentPlugin = plugin;
        Scheduler.onTick(it -> currentTick++);
    }

    public static void shutdownCoreUtils(JavaPlugin plugin) {
        for (Disposable closeable : closeableList) closeable.close();
    }

    public static NamespacedKey namespacedID(String id) {
        return new NamespacedKey(getCurrentPlugin(), id);
    }

    /** A file packaged in the plugin jar (read through its class loader, so it also works without a running server). */
    public static InputStream requireResource(String name) {
        InputStream stream = Core.class.getClassLoader().getResourceAsStream(name);
        if (stream == null) throw new IllegalStateException("Resource " + name + " not found");
        return stream;
    }

    private static CommandMinecart commandBlockMinecart = null;

    public static void runCommandSilently(String command) {
        runCommandSilently(command, null, null);
    }

    public static void runCommandSilently(String command, World world) {
        runCommandSilently(command, world, null);
    }

    public static void runCommandSilently(String command, World world, Vector position) {
        if (world == null) world = Bukkit.getWorlds().getFirst();
        if (position == null) position = world.getSpawnLocation().toVector();

        Server server = Bukkit.getServer();

        CommandMinecart commandBlockMinecart = Core.commandBlockMinecart;
        if (commandBlockMinecart == null) {
            commandBlockMinecart = Overloads.spawnEntity(world, position, CommandMinecart.class, it -> {
                Core.commandBlockMinecart = it;
                it.remove();
            });
        }

        server.dispatchCommand(commandBlockMinecart, command);
    }
}
