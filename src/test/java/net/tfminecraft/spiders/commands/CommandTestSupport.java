package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.ecs.ECSComponent;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A /spider command tree with a stand-in server: two worlds, online players, block data and a plugin
 * for the scheduler. Spiders go into {@link AppState}'s ECS and are cleared again on {@link #close()}.
 */
final class CommandTestSupport implements AutoCloseable {
    final CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
    final MockedStatic<Bukkit> bukkit;
    final JavaPlugin plugin = mock(JavaPlugin.class);
    final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    final World world;
    final World nether;
    /** Who {@code Bukkit.getOnlinePlayers()} returns. */
    final List<Player> online = new ArrayList<>();
    private final JavaPlugin previousPlugin;

    CommandTestSupport() {
        previousPlugin = currentPlugin();
        Server server = mock(Server.class);
        when(plugin.getName()).thenReturn("TFMCSpiders");
        when(plugin.namespace()).thenReturn("tfmcspiders");
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        Core.setCurrentPlugin(plugin);

        world = world("world");
        nether = world("world_nether");
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createBlockData(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            int bracket = name.indexOf('[');
            return blockData(Objects.requireNonNull(Material.matchMaterial(bracket < 0 ? name : name.substring(0, bracket))));
        });
        bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world, nether));
        bukkit.when(() -> Bukkit.getWorld(anyString())).thenAnswer(call -> {
            String name = call.getArgument(0);
            return name.equals("world") ? world : name.equals("world_nether") ? nether : null;
        });
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> List.copyOf(online));
        bukkit.when(() -> Bukkit.getPlayerExact(anyString())).thenAnswer(call -> {
            for (Player player : online) if (player.getName().equals(call.getArgument(0))) return player;
            return null;
        });

        dispatcher.register(SetupCommands.spiderCommandTree());
        AppState.getEcs().getEntities().clear();
    }

    @Override
    public void close() {
        AppState.getEcs().getEntities().clear();
        bukkit.close();
        Core.setCurrentPlugin(previousPlugin);
    }

    private static JavaPlugin currentPlugin() {
        try {
            return Core.getCurrentPlugin();
        } catch (IllegalStateException e) {
            return null;
        }
    }

    static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.getAsString()).thenReturn(material.getKey().toString());
        when(data.clone()).thenAnswer(call -> blockData(material));
        return data;
    }

    /** A world with vanilla build limits whose chunks are all loaded. */
    static World world(String name) {
        World world = mock(World.class);
        when(world.getName()).thenReturn(name);
        when(world.getMinHeight()).thenReturn(-64);
        when(world.getMaxHeight()).thenReturn(320);
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(true);
        return world;
    }

    Location at(double x, double y, double z) {
        return new Location(world, x, y, z);
    }

    /** An online survival player standing at {@code location}, holding nothing; staff if {@code staff}. */
    static Player player(String name, Location location, boolean staff) {
        Player player = mock(Player.class);
        when(player.getName()).thenReturn(name);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(staff);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(location.getWorld());
        when(player.getLocation()).thenAnswer(call -> location.clone());
        when(player.getEyeLocation()).thenAnswer(call -> location.clone().add(0.0, 1.62, 0.0));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
        when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class));
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    static Player staff(String name, Location location) {
        return player(name, location, true);
    }

    static ConsoleCommandSender console() {
        ConsoleCommandSender console = mock(ConsoleCommandSender.class);
        when(console.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        return console;
    }

    /** The source of a command sent by {@code sender} that runs at {@code location}. */
    static CommandSourceStack source(CommandSender sender, Location location) {
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(sender);
        when(source.getLocation()).thenAnswer(call -> location.clone());
        return source;
    }

    /** A player's own command, run where they stand. */
    static CommandSourceStack source(Player player) {
        return source(player, player.getLocation());
    }

    int run(CommandSourceStack source, String command) throws CommandSyntaxException {
        return dispatcher.execute(command, source);
    }

    /** The message of the error the command fails with. */
    String error(CommandSourceStack source, String command) {
        return assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source), command).getRawMessage().getString();
    }

    Suggestions suggestions(CommandSourceStack source, String input) {
        return dispatcher.getCompletionSuggestions(dispatcher.parse(input, source)).join();
    }

    List<String> suggest(CommandSourceStack source, String input) {
        return suggestions(source, input).getList().stream().map(Suggestion::getText).toList();
    }

    /** Every plain message {@code sender} was sent so far. */
    static List<String> messages(CommandSender sender) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(sender, atLeast(0)).sendPlainMessage(captor.capture());
        return captor.getAllValues();
    }

    static String lastMessage(CommandSender sender) {
        List<String> messages = messages(sender);
        return messages.isEmpty() ? null : messages.getLast();
    }

    /** A spider body at {@code position} in {@code world}, with {@code components}, in the plugin's ECS. */
    static ECSEntity spider(World world, Vector position, ECSComponent... components) {
        ECSEntity entity = AppState.getEcs().spawn(SpiderBody.create(world, position));
        for (ECSComponent component : components) entity.addComponent(component);
        return entity;
    }
}
