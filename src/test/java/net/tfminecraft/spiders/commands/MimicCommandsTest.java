package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.MimicChests;
import net.tfminecraft.spiders.spider.components.MimicState;
import net.tfminecraft.spiders.spider.components.MimicTerror;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static net.tfminecraft.spiders.commands.CommandTestSupport.console;
import static net.tfminecraft.spiders.commands.CommandTestSupport.lastMessage;
import static net.tfminecraft.spiders.commands.CommandTestSupport.source;
import static net.tfminecraft.spiders.commands.CommandTestSupport.spider;
import static net.tfminecraft.spiders.commands.CommandTestSupport.staff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MimicCommandsTest {
    private final CommandTestSupport commands = new CommandTestSupport();
    /** Looking south (+z). */
    private final Player staff = staff("Warden", commands.at(0.5, 64.0, 0.5));
    private final CommandSourceStack source = source(staff);
    private final ConsoleCommandSender console = console();
    private final CommandSourceStack fromConsole = source(console, commands.at(0.0, 64.0, 0.0));
    private final MockedStatic<MimicChests> chests = mockStatic(MimicChests.class);

    @AfterEach
    void close() {
        chests.close();
        commands.close();
    }

    private static Block block(int x, int y, int z) {
        Block block = mock(Block.class);
        when(block.getX()).thenReturn(x);
        when(block.getY()).thenReturn(y);
        when(block.getZ()).thenReturn(z);
        return block;
    }

    private void aimAt(RayTraceResult hit) {
        when(commands.world.rayTraceBlocks(any(Location.class), any(Vector.class), eq(32.0), eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(hit);
    }

    @Test
    @DisplayName("/spider mimic on its own explains the subcommands")
    void mimicOnItsOwnExplains() throws CommandSyntaxException {
        assertEquals(1, commands.run(source, "spider mimic"));
        assertEquals("Mimic: /spider mimic place, wake, sleep, scare. Anyone who right-clicks or hits a dormant mimic wakes it.", lastMessage(staff));
    }

    @Test
    @DisplayName("scare previews the jump scare for players only")
    void scarePreviewsTheJumpScare() throws CommandSyntaxException {
        try (MockedStatic<MimicTerror> terror = mockStatic(MimicTerror.class)) {
            assertEquals(1, commands.run(source, "spider mimic scare"));
            terror.verify(() -> MimicTerror.jumpScare(staff));
            assertEquals("Only players can see the jump scare.", commands.error(fromConsole, "spider mimic scare"));
        }
    }

    @Test
    @DisplayName("an aimed place puts a chest on top of the floor, facing the player")
    void anAimedPlacePutsAChestOnTheFloor() throws CommandSyntaxException {
        Block floor = block(3, 63, 4);
        Block above = block(3, 64, 4);
        when(floor.getRelative(BlockFace.UP)).thenReturn(above);
        aimAt(new RayTraceResult(new Vector(3.5, 64.0, 4.5), floor, BlockFace.UP));
        chests.when(() -> MimicChests.place(above, BlockFace.NORTH)).thenReturn(true);

        assertEquals(1, commands.run(source, "spider mimic place"));
        assertEquals("Placed a dormant mimic at 3, 64, 4. It wakes when anyone right-clicks or hits it.", lastMessage(staff));
        // spawn mimic is the same as mimic place
        assertEquals(1, commands.run(source, "spider spawn mimic"));
        chests.verify(() -> MimicChests.place(above, BlockFace.NORTH), org.mockito.Mockito.times(2));

        chests.when(() -> MimicChests.place(above, BlockFace.NORTH)).thenReturn(false);
        assertEquals("A mimic needs an empty space with solid floor beneath it.", commands.error(source, "spider mimic place"));
    }

    @Test
    @DisplayName("an aimed place needs a player looking at the top of a block")
    void anAimedPlaceNeedsTheTopOfABlock() {
        assertEquals("Console: /spider mimic place at <world> <x> <y> <z>", commands.error(fromConsole, "spider mimic place"));
        aimAt(null);
        assertEquals("Aim at a nearby floor to place a mimic.", commands.error(source, "spider mimic place"));
        aimAt(new RayTraceResult(new Vector(3.5, 64.0, 4.5), BlockFace.UP));
        assertEquals("Aim at a nearby floor to place a mimic.", commands.error(source, "spider mimic place"));
        aimAt(new RayTraceResult(new Vector(3.5, 64.0, 4.0), block(3, 64, 4), BlockFace.NORTH));
        assertEquals("Mimics sit on floors. Aim at the top of a block.", commands.error(source, "spider mimic place"));
        chests.verifyNoInteractions();
    }

    @Test
    @DisplayName("place at a position faces south and checks the world, height and chunk")
    void placeAtAPosition() throws CommandSyntaxException {
        Block block = block(10, 70, -5);
        when(commands.world.getBlockAt(10, 70, -5)).thenReturn(block);
        chests.when(() -> MimicChests.place(block, BlockFace.SOUTH)).thenReturn(true);
        assertEquals(1, commands.run(fromConsole, "spider mimic place at world 10 70 -5"));
        assertEquals("Placed a dormant mimic at 10, 70, -5. It wakes when anyone right-clicks or hits it.", lastMessage(console));

        assertEquals("World not found.", commands.error(fromConsole, "spider mimic place at nowhere 0 64 0"));
        assertEquals("Choose a position within the world's build height.", commands.error(fromConsole, "spider mimic place at world 0 -65 0"));
        assertEquals("Choose a position within the world's build height.", commands.error(fromConsole, "spider mimic place at world 0 320 0"));
        when(commands.world.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertEquals("Visit that area first; mimics only wake in loaded chunks.", commands.error(fromConsole, "spider mimic place at world 0 64 0"));
    }

    @Test
    @DisplayName("place at suggests worlds by prefix")
    void placeAtSuggestsWorlds() {
        assertEquals(List.of("world", "world_nether"), commands.suggest(source, "spider mimic place at "));
        assertEquals(List.of("world_nether"), commands.suggest(source, "spider mimic wake at world_"));
        assertEquals(List.of(), commands.suggest(source, "spider mimic wake at nether"));
    }

    @Test
    @DisplayName("wake wakes the dormant mimic looked at, or at a position from the console")
    void wakeWakesADormantMimic() throws CommandSyntaxException {
        Block chest = block(3, 64, 4);
        when(staff.getTargetBlockExact(32)).thenReturn(chest);
        chests.when(() -> MimicChests.isMimic(chest)).thenReturn(true);
        chests.when(() -> MimicChests.wake(chest, staff)).thenReturn(true);
        assertEquals(1, commands.run(source, "spider mimic wake"));
        assertEquals("The mimic wakes. 0/" + AppState.MAX_SPIDERS + " active.", lastMessage(staff));

        when(commands.world.getBlockAt(3, 64, 4)).thenReturn(chest);
        chests.when(() -> MimicChests.wake(chest, null)).thenReturn(true);
        assertEquals(1, commands.run(fromConsole, "spider mimic wake at world 3 64 4"));
        // nobody to hunt when the console wakes it
        chests.verify(() -> MimicChests.wake(chest, null));

        chests.when(() -> MimicChests.wake(chest, staff)).thenReturn(false);
        assertEquals("The test limit is " + AppState.MAX_SPIDERS + " awake creatures. Remove one first.", commands.error(source, "spider mimic wake"));
    }

    @Test
    @DisplayName("wake needs a player looking at a dormant mimic")
    void wakeNeedsADormantMimic() {
        assertEquals("Console: /spider mimic wake at <world> <x> <y> <z>", commands.error(fromConsole, "spider mimic wake"));
        assertEquals("Look at a dormant mimic.", commands.error(source, "spider mimic wake"));
        Block stone = block(3, 64, 4);
        when(staff.getTargetBlockExact(32)).thenReturn(stone);
        assertEquals("That block isn't a dormant mimic.", commands.error(source, "spider mimic wake"));
        chests.verify(() -> MimicChests.wake(any(), any()), never());
    }

    private Mimic awakeMimic(Vector position, MimicState state) {
        Mimic mimic = mock(Mimic.class);
        when(mimic.getState()).thenReturn(state);
        spider(commands.world, position, mimic);
        return mimic;
    }

    @Test
    @DisplayName("sleep settles the nearest awake mimic in the player's world")
    void sleepSettlesTheNearestMimic() throws CommandSyntaxException {
        spider(commands.world, new Vector(1.0, 64.0, 0.0));
        Mimic gone = awakeMimic(new Vector(1.0, 64.0, 1.0), MimicState.HUNTING);
        AppState.getEcs().getEntities().getLast().remove();
        Mimic elsewhere = mock(Mimic.class);
        spider(commands.nether, new Vector(0.5, 64.0, 0.5), elsewhere);
        Mimic far = awakeMimic(new Vector(30.0, 64.0, 0.0), MimicState.HUNTING);
        Mimic near = awakeMimic(new Vector(5.0, 64.0, 0.0), MimicState.FLEEING);
        Mimic middle = awakeMimic(new Vector(10.0, 64.0, 0.0), MimicState.HUNTING);
        when(near.beginSettling(any(SpiderBody.class))).thenReturn(true);

        assertEquals(1, commands.run(source, "spider mimic sleep"));
        assertEquals("The mimic is settling back into a chest.", lastMessage(staff));
        verify(near).beginSettling(any(SpiderBody.class));
        for (Mimic other : List.of(gone, elsewhere, far, middle)) verify(other, never()).beginSettling(any());

        when(near.getState()).thenReturn(MimicState.SETTLING);
        assertEquals("That mimic is already settling down.", commands.error(source, "spider mimic sleep"));
        when(near.getState()).thenReturn(MimicState.CROUCHING);
        assertEquals("That mimic is already settling down.", commands.error(source, "spider mimic sleep"));
        when(near.getState()).thenReturn(MimicState.HUNTING);
        when(near.beginSettling(any(SpiderBody.class))).thenReturn(false);
        assertEquals("There is no free floor next to the mimic for it to become a chest.", commands.error(source, "spider mimic sleep"));
    }

    @Test
    @DisplayName("sleep from the console settles the first awake mimic")
    void sleepFromTheConsole() throws CommandSyntaxException {
        assertEquals("No awake mimic found.", commands.error(fromConsole, "spider mimic sleep"));
        Mimic first = awakeMimic(new Vector(30.0, 64.0, 0.0), MimicState.AWAKE);
        Mimic second = awakeMimic(new Vector(1.0, 64.0, 0.0), MimicState.AWAKE);
        when(first.beginSettling(any(SpiderBody.class))).thenReturn(true);
        assertEquals(1, commands.run(fromConsole, "spider mimic sleep"));
        verify(second, never()).beginSettling(any());

    }

    @Test
    @DisplayName("a player finds no mimic to settle when the only one is in another world")
    void noMimicInThePlayersWorld() {
        spider(commands.nether, new Vector(0.0, 64.0, 0.0), mock(Mimic.class));
        assertEquals("No awake mimic found.", commands.error(source, "spider mimic sleep"));
    }
}
