package net.tfminecraft.spiders;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.commands.SetupCommands;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StaffAccessTest {
    @AfterEach
    void cleanState() { AppState.getEcs().getEntities().clear(); }

    @Test
    @DisplayName("permission is checked rather than assuming operators are always authorised")
    void permissionIsCheckedRatherThanAssumingOperators() {
        Player player = mock(Player.class);
        when(player.isOp()).thenReturn(true);
        assertFalse(StaffAccess.canUse(player));
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        assertTrue(StaffAccess.canUse(player));
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
        assertFalse(StaffAccess.canUse(player));
    }

    @Test
    @DisplayName("spawn factory denies a caller before reading the world or options")
    void spawnFactoryDeniesCallerBeforeReadingWorldOrOptions() {
        CommandSender actor = mock(CommandSender.class);
        World world = mock(World.class);
        SpiderOptions options = mock(SpiderOptions.class);
        assertNull(AppState.createSpider(actor, world, new Vector(), options));
        assertEquals(0, AppState.spiderCount());
        verifyNoInteractions(world, options);
    }

    @Test
    @DisplayName("every command branch is unreachable without staff permission")
    void everyCommandBranchIsUnreachableWithoutStaffPermission() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        Player player = mock(Player.class);
        when(source.getSender()).thenReturn(player);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SetupCommands.spiderCommandTree());
        for (String command : List.of("spider", "spider spawn hunter", "spider tools", "spider items",
            "spider remove all", "spider list", "spider scale 1", "spider splay", "spider pounce nearest",
            "spider options global.showLaser set false")) {
            assertThrows(CommandSyntaxException.class, () -> dispatcher.execute(command, source), command);
        }
        assertEquals(0, AppState.spiderCount());
    }

    @Test
    @DisplayName("staff can use commands and losing permission blocks the same source immediately")
    void losingPermissionBlocksTheSameSourceImmediately() throws CommandSyntaxException {
        CommandSourceStack source = mock(CommandSourceStack.class);
        CommandSender actor = mock(CommandSender.class);
        when(source.getSender()).thenReturn(actor);
        when(actor.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SetupCommands.spiderCommandTree());
        assertEquals(1, dispatcher.execute("spider list", source));
        when(actor.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
        assertThrows(CommandSyntaxException.class, () -> dispatcher.execute("spider list", source));
    }
}
