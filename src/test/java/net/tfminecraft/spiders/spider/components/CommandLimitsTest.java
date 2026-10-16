package net.tfminecraft.spiders.spider.components;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.commands.SetupCommands;
import net.tfminecraft.spiders.commands.Symbols;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommandLimitsTest {
    @BeforeAll
    static void installBlockDataFactory() {
        BlockDataMocks.install();
    }

    @AfterAll
    static void restoreBukkit() {
        BlockDataMocks.restore();
    }

    @Test
    @DisplayName("the upstream robot presets and models are gone from /spider options")
    void theUpstreamRobotPresetsAreGone() throws CommandSyntaxException {
        assertThrows(CommandSyntaxException.class, () -> Symbols.resolveSymbol("HEX_BOT {segments: 100000}"));
        assertNull(Symbols.resolveSymbol("TORSO_BOXY"));
        assertNotNull(Symbols.resolveSymbol("EMPTY"));
    }

    @Test
    @DisplayName("pounce takes a player name or nearest, each with an optional speed")
    void pounceTakesANameOrNearest() {
        CommandSourceStack source = mock(CommandSourceStack.class);
        CommandSender staff = mock(CommandSender.class);
        when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        when(source.getSender()).thenReturn(staff);
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(SetupCommands.spiderCommandTree());
        for (String command : new String[]{"spider pounce nearest", "spider pounce nearest 12", "spider pounce Steve", "spider pounce Steve 8.5"}) {
            ParseResults<CommandSourceStack> parsed = dispatcher.parse(command, source);
            assertTrue(parsed.getExceptions().isEmpty() && !parsed.getReader().canRead() && parsed.getContext().getCommand() != null, command);
        }
    }
}
