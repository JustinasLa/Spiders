package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.MimicState;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.block.BlockFace;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static net.tfminecraft.spiders.commands.CommandTestSupport.console;
import static net.tfminecraft.spiders.commands.CommandTestSupport.lastMessage;
import static net.tfminecraft.spiders.commands.CommandTestSupport.messages;
import static net.tfminecraft.spiders.commands.CommandTestSupport.source;
import static net.tfminecraft.spiders.commands.CommandTestSupport.spider;
import static net.tfminecraft.spiders.commands.CommandTestSupport.staff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StaffCommandsTest {
    private final CommandTestSupport commands = new CommandTestSupport();
    private final Player staff = staff("Warden", commands.at(0.5, 64.0, 0.5));
    private final CommandSourceStack source = source(staff);
    private final ConsoleCommandSender console = console();
    private final CommandSourceStack fromConsole = source(console, commands.at(0.0, 64.0, 0.0));

    @AfterEach
    void close() {
        commands.close();
    }

    /** Where the staff member's aim lands. */
    private void aimAt(RayTraceResult hit) {
        when(commands.world.rayTraceBlocks(any(Location.class), any(Vector.class), eq(32.0), eq(FluidCollisionMode.NEVER), eq(true))).thenReturn(hit);
    }

    private SpiderBody onlySpider() {
        List<ECSEntity> entities = AppState.getEcs().getEntities();
        assertEquals(1, entities.size());
        return entities.getFirst().query(SpiderBody.class);
    }

    private static SpiderOptions optionsOf(SpiderBody body) {
        for (ECSEntity entity : AppState.getEcs().getEntities()) if (entity.query(SpiderBody.class) == body) return entity.query(SpiderOptions.class);
        throw new AssertionError("no such spider");
    }

    @Test
    @DisplayName("spawn suggests sizes ignoring case, and worlds after at")
    void spawnSuggestsSizesAndWorlds() {
        assertEquals(List.of("0.25", "0.5", "1.0", "2.0", "2.5", "broodmother", "hatchling", "hunter", "mimic"), commands.suggest(source, "spider spawn "));
        assertEquals(List.of("hatchling", "hunter"), commands.suggest(source, "spider spawn H"));
        assertEquals(List.of("world", "world_nether"), commands.suggest(source, "spider spawn hunter at "));
        assertEquals(List.of("world_nether"), commands.suggest(source, "spider spawn hunter at world_"));
    }

    @Test
    @DisplayName("a player spawns a spider standing on the floor they aim at")
    void aPlayerSpawnsOnTheFloorTheyAimAt() throws CommandSyntaxException {
        aimAt(new RayTraceResult(new Vector(3.5, 64.0, 0.5), BlockFace.UP));
        assertEquals(1, commands.run(source, "spider spawn hunter"));
        assertEquals("Spawned Cinderweaver hunter. 1/" + AppState.MAX_SPIDERS + " active. /spider tools opens its controls.", lastMessage(staff));
        SpiderBody body = onlySpider();
        assertEquals(3.5, body.getPosition().getX(), 1e-9);
        assertTrue(body.getPosition().getY() > 64.0, "stands above the floor");
        assertTrue(Maths.rotate(Maths.upVector(), body.getOrientation()).getY() > .99);
        assertFalse(optionsOf(body).getClimbing());
        assertEquals(0.8, optionsOf(body).getBodyPlan().getScale(), 1e-9);
    }

    @Test
    @DisplayName("a spider spawned on a wall clings to it")
    void aSpiderSpawnedOnAWallClingsToIt() throws CommandSyntaxException {
        aimAt(new RayTraceResult(new Vector(0.5, 65.0, 5.0), BlockFace.NORTH));
        commands.run(source, "spider spawn 1.2");
        assertEquals("Spawned Cinderweaver at scale 1.2. Clinging to the surface (wall climbing on). 1/" + AppState.MAX_SPIDERS + " active. /spider tools opens its controls.", lastMessage(staff));
        SpiderBody body = onlySpider();
        assertTrue(optionsOf(body).getClimbing());
        assertTrue(Maths.rotate(Maths.upVector(), body.getOrientation()).getZ() < -.99, "its back faces away from the wall");
    }

    @Test
    @DisplayName("a hit without a face spawns the spider upright")
    void aHitWithoutAFaceSpawnsUpright() throws CommandSyntaxException {
        aimAt(new RayTraceResult(new Vector(3.5, 64.0, 0.5)));
        commands.run(source, "spider spawn HATCHLING");
        assertEquals("Spawned Cinderweaver hatchling. 1/" + AppState.MAX_SPIDERS + " active. /spider tools opens its controls.", lastMessage(staff));
        assertFalse(optionsOf(onlySpider()).getClimbing());
    }

    @Test
    @DisplayName("aimed spawns need a surface in reach, a valid size and a sane position")
    void aimedSpawnsAreChecked() {
        aimAt(null);
        assertEquals("Aim at a nearby solid surface to spawn a Cinderweaver.", commands.error(source, "spider spawn hunter"));
        aimAt(new RayTraceResult(new Vector(3.5, 64.0, 0.5), BlockFace.UP));
        assertEquals("Size must be hatchling, hunter, broodmother or a scale from 0.25 to 2.5.", commands.error(source, "spider spawn huge"));
        assertEquals("Size must be hatchling, hunter, broodmother or a scale from 0.25 to 2.5.", commands.error(source, "spider spawn 3"));
        aimAt(new RayTraceResult(new Vector(Double.NaN, 64.0, 0.5), BlockFace.UP));
        assertEquals("Choose a position within the world's build height.", commands.error(source, "spider spawn hunter"));
        assertEquals(0, AppState.spiderCount());
    }

    @Test
    @DisplayName("the console spawns at a position in a loaded chunk within the build height")
    void theConsoleSpawnsAtAPosition() throws CommandSyntaxException {
        assertEquals("Console: /spider spawn <size> at <world> <x> <y> <z>", commands.error(fromConsole, "spider spawn hunter"));
        assertEquals("World not found.", commands.error(fromConsole, "spider spawn hunter at nowhere 0 64 0"));
        assertEquals("Choose a position within the world's build height.", commands.error(fromConsole, "spider spawn hunter at world 0 -65 0"));
        assertEquals("Choose a position within the world's build height.", commands.error(fromConsole, "spider spawn hunter at world 0 320 0"));
        when(commands.nether.isChunkLoaded(anyInt(), anyInt())).thenReturn(false);
        assertEquals("Visit that area first; spiders only spawn in loaded chunks.", commands.error(fromConsole, "spider spawn hunter at world_nether 0 64 0"));
        assertEquals("Console: /spider mimic place at <world> <x> <y> <z>", commands.error(fromConsole, "spider spawn mimic at world 0 64 0"));
        assertEquals("Console: /spider mimic place at <world> <x> <y> <z>", commands.error(fromConsole, "spider spawn MIMIC"));
        assertEquals(0, AppState.spiderCount());

        assertEquals(1, commands.run(fromConsole, "spider spawn broodmother at world 100.5 70 -20.5"));
        SpiderBody body = onlySpider();
        assertEquals(commands.world, body.getWorld());
        assertEquals(100.5, body.getPosition().getX(), 1e-9);
        assertEquals(-20.5, body.getPosition().getZ(), 1e-9);
        assertEquals(1.5, optionsOf(body).getBodyPlan().getScale(), 1e-9);
        assertTrue(lastMessage(console).startsWith("Spawned Cinderweaver broodmother. 1/"));
    }

    @Test
    @DisplayName("spawning stops at the spider limit")
    void spawningStopsAtTheLimit() throws CommandSyntaxException {
        for (int i = 0; i < AppState.MAX_SPIDERS; i++) spider(commands.world, new Vector(i * 10.0, 64.0, 0.0));
        assertEquals(0, commands.run(fromConsole, "spider spawn hunter at world 0 64 0"));
        assertEquals("The test limit is " + AppState.MAX_SPIDERS + " Cinderweavers. Remove one first.", lastMessage(console));
        assertEquals(AppState.MAX_SPIDERS, AppState.spiderCount());
    }

    private ECSEntity spiderWithId(String uuid, Vector position) {
        ECSEntity entity = AppState.getEcs().spawn(SpiderBody.create(commands.world, position, new Quaternionf(), UUID.fromString(uuid)));
        return entity;
    }

    @Test
    @DisplayName("list shows every live spider's short ID, world, block position and mimic state")
    void listShowsEverySpider() throws CommandSyntaxException {
        spiderWithId("0123abcd-0000-0000-0000-000000000001", new Vector(2.7, 64.0, -0.5));
        ECSEntity mimic = spiderWithId("4567ef01-0000-0000-0000-000000000002", new Vector(-10.0, 70.0, 5.0));
        Mimic state = mock(Mimic.class);
        when(state.getState()).thenReturn(MimicState.HUNTING);
        mimic.addComponent(state);
        spiderWithId("89abcdef-0000-0000-0000-000000000003", new Vector(0.0, 64.0, 0.0)).remove();

        assertEquals(1, commands.run(fromConsole, "spider list"));
        assertEquals(List.of(
            "Cinderweavers: 2/" + AppState.MAX_SPIDERS,
            "0123abcd • world • 2, 64, -1",
            "4567ef01 • world • -10, 70, 5 • mimic (hunting)"
        ), messages(console));
    }

    @Test
    @DisplayName("remove takes the targeted spider, all of them, or one by its ID")
    void removeTakesTargetAllOrId() throws CommandSyntaxException {
        ECSEntity near = spiderWithId("0123abcd-0000-0000-0000-000000000001", new Vector(1.0, 64.0, 0.0));
        ECSEntity twinA = spiderWithId("4567ef01-1000-0000-0000-000000000002", new Vector(50.0, 64.0, 0.0));
        ECSEntity twinB = spiderWithId("4567ef01-2000-0000-0000-000000000003", new Vector(60.0, 64.0, 0.0));

        assertEquals(1, commands.run(source, "spider remove"));
        assertTrue(near.getScheduledForRemoval());
        assertEquals("Removed the Cinderweaver.", lastMessage(staff));

        assertEquals("Use at least the eight-character ID from /spider list.", commands.error(source, "spider remove 4567ef0"));
        assertEquals("Use an unambiguous ID from /spider list.", commands.error(source, "spider remove 4567ef01"));
        assertEquals("Use an unambiguous ID from /spider list.", commands.error(source, "spider remove 0123abcd"), "already removed");
        assertEquals(1, commands.run(source, "spider remove 4567EF01-1"));
        assertTrue(twinA.getScheduledForRemoval());
        assertFalse(twinB.getScheduledForRemoval());
        assertEquals("Removed Cinderweaver 4567EF01-1.", lastMessage(staff));

        spiderWithId("89abcdef-0000-0000-0000-000000000004", new Vector(0.0, 64.0, 0.0));
        assertEquals(1, commands.run(source, "spider remove all"));
        assertEquals("Removed 2 Cinderweavers.", lastMessage(staff));
        assertEquals(0, AppState.spiderCount());
    }
}
