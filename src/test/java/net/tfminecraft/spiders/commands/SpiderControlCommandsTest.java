package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.Cloak;
import net.tfminecraft.spiders.spider.components.Mimic;
import net.tfminecraft.spiders.spider.components.Splay;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolMenu;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;

import static net.tfminecraft.spiders.commands.CommandTestSupport.console;
import static net.tfminecraft.spiders.commands.CommandTestSupport.lastMessage;
import static net.tfminecraft.spiders.commands.CommandTestSupport.source;
import static net.tfminecraft.spiders.commands.CommandTestSupport.spider;
import static net.tfminecraft.spiders.commands.CommandTestSupport.staff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiderControlCommandsTest {
    private final CommandTestSupport commands = new CommandTestSupport();
    private final Player staff = staff("Warden", commands.at(0.5, 64.0, 0.5));
    private final CommandSourceStack source = source(staff);

    @AfterEach
    void close() {
        commands.close();
    }

    private ECSEntity cinderweaver(double scale) {
        return spider(commands.world, new Vector(2.5, 64.0, 0.5), Cinderweaver.cinderweaver(scale), new Cloak());
    }

    @Test
    @DisplayName("/spider on its own shows the help")
    void spiderOnItsOwnShowsTheHelp() throws CommandSyntaxException {
        assertEquals(1, commands.run(source, "spider"));
        assertEquals(SetupCommands.HELP, lastMessage(staff));
        assertTrue(SetupCommands.HELP.contains("/spider spawn <hatchling|hunter|broodmother|mimic|"));
    }

    @Test
    @DisplayName("tools and its old name items open the staff tool menu for players only")
    void toolsOpensTheMenuForPlayersOnly() throws CommandSyntaxException {
        try (MockedStatic<StaffToolMenu> menu = mockStatic(StaffToolMenu.class)) {
            assertEquals(1, commands.run(source, "spider tools"));
            assertEquals(1, commands.run(source, "spider items"));
            menu.verify(() -> StaffToolMenu.openStaffToolMenu(staff), org.mockito.Mockito.times(2));

            CommandSourceStack fromConsole = source(console(), commands.at(0.0, 64.0, 0.0));
            assertEquals("Players only", commands.error(fromConsole, "spider tools"));
            assertEquals("Players only", commands.error(fromConsole, "spider items"));
        }
    }

    @Test
    @DisplayName("commands with no spider nearby say so, telling the console how to aim them")
    void commandsWithNoSpiderSaySo() {
        for (String command : List.of("spider scale 1", "spider gait walk", "spider speed walk", "spider speed gallop 4",
            "spider climb on", "spider shroud on", "spider splay", "spider pounce stop", "spider pounce nearest", "spider remove")) {
            assertEquals("No spider found in this world", commands.error(source, command), command);
        }
        // a spider in another world is out of reach
        spider(commands.nether, new Vector(0.5, 64.0, 0.5));
        ConsoleCommandSender console = console();
        assertEquals("No spider found in world; use /execute in <world> positioned <x> <y> <z> run spider ...",
            commands.error(source(console, commands.at(0.0, 64.0, 0.0)), "spider gait walk"));
    }

    @Test
    @DisplayName("scale resizes the gaits and body together")
    void scaleResizesGaitsAndBody() throws CommandSyntaxException {
        ECSEntity entity = cinderweaver(1.0);
        SpiderOptions options = entity.query(SpiderOptions.class);
        double walkSpeed = options.getWalkGait().getMaxSpeed();
        double gallopSpeed = options.getGallopGait().getMaxSpeed();

        assertEquals(1, commands.run(source, "spider scale 2"));

        assertEquals(2.0, options.getBodyPlan().getScale(), 1e-9);
        assertEquals(2 * walkSpeed, options.getWalkGait().getMaxSpeed(), 1e-9);
        assertEquals(2 * gallopSpeed, options.getGallopGait().getMaxSpeed(), 1e-9);
        assertEquals("Set scale to 2.0", lastMessage(staff));
        assertTrue(commands.error(source, "spider scale 3").contains("must not be more than 2.5"));
    }

    @Test
    @DisplayName("mimics keep their size and stay off walls")
    void mimicsKeepTheirSizeAndStayOffWalls() {
        SpiderOptions options = Cinderweaver.cinderweaver(0.8);
        spider(commands.world, new Vector(2.5, 64.0, 0.5), options, mock(Mimic.class));
        assertEquals("Mimics stay chest-sized.", commands.error(source, "spider scale 1"));
        assertEquals("Mimics keep to the floor so they can sit back down as chests.", commands.error(source, "spider climb on"));
        assertEquals(0.8, options.getBodyPlan().getScale(), 1e-9);
        assertFalse(options.getClimbing());
    }

    @Test
    @DisplayName("a spider without options can't be steered")
    void aSpiderWithoutOptionsCantBeSteered() {
        spider(commands.world, new Vector(2.5, 64.0, 0.5));
        for (String command : List.of("spider scale 1", "spider gait gallop", "spider speed walk", "spider speed walk 3",
            "spider climb off", "spider shroud off")) {
            assertEquals("No spider found", commands.error(source, command), command);
        }
    }

    @Test
    @DisplayName("gait switches between walking and galloping")
    void gaitSwitchesBetweenWalkAndGallop() throws CommandSyntaxException {
        SpiderOptions options = cinderweaver(0.8).query(SpiderOptions.class);
        commands.run(source, "spider gait gallop");
        assertTrue(options.getGallop());
        assertEquals("Gallop mode", lastMessage(staff));
        commands.run(source, "spider gait walk");
        assertFalse(options.getGallop());
        assertEquals("Walk mode", lastMessage(staff));
    }

    @Test
    @DisplayName("speed shows and sets each gait's speed in blocks per second")
    void speedShowsAndSetsEachGait() throws CommandSyntaxException {
        SpiderOptions options = cinderweaver(0.8).query(SpiderOptions.class);
        commands.run(source, "spider speed walk 3");
        assertEquals(3.0 / 20, options.getWalkGait().getMaxSpeed(), 1e-9);
        assertEquals("Set walk speed to 3.0 blocks/s", lastMessage(staff));
        commands.run(source, "spider speed walk");
        // the shown speed follows the server's locale
        assertEquals("walk speed: " + "%.1f".formatted(3.0) + " blocks/s", lastMessage(staff));

        commands.run(source, "spider speed gallop 12.5");
        assertEquals(12.5 / 20, options.getGallopGait().getMaxSpeed(), 1e-9);
        commands.run(source, "spider speed gallop");
        assertEquals("gallop speed: " + "%.1f".formatted(12.5) + " blocks/s", lastMessage(staff));
        assertEquals(3.0 / 20, options.getWalkGait().getMaxSpeed(), 1e-9, "walking is unchanged");

        assertTrue(commands.error(source, "spider speed gallop 25").contains("must not be more than 20.0"));
    }

    @Test
    @DisplayName("climb turns wall climbing on and off")
    void climbTurnsWallClimbingOnAndOff() throws CommandSyntaxException {
        SpiderOptions options = cinderweaver(0.8).query(SpiderOptions.class);
        commands.run(source, "spider climb on");
        assertTrue(options.getClimbing());
        assertEquals("Wall climbing on: walls and ceilings", lastMessage(staff));
        commands.run(source, "spider climb off");
        assertFalse(options.getClimbing());
        assertEquals("Wall climbing off", lastMessage(staff));
    }

    @Test
    @DisplayName("shroud sets the cloak to the asked state, toggling only when it differs")
    void shroudSetsTheCloakState() throws CommandSyntaxException {
        Cloak cloak = cinderweaver(0.8).query(Cloak.class);
        commands.run(source, "spider shroud on");
        assertTrue(cloak.getActive());
        assertEquals("Shroud on", lastMessage(staff));
        commands.run(source, "spider shroud on");
        assertTrue(cloak.getActive(), "asking again leaves it on");
        commands.run(source, "spider shroud off");
        assertFalse(cloak.getActive());
        assertEquals("Shroud off", lastMessage(staff));
        commands.run(source, "spider shroud off");
        assertFalse(cloak.getActive());
    }

    @Test
    @DisplayName("a spider without a cloak can't be shrouded")
    void aSpiderWithoutACloakCantBeShrouded() {
        spider(commands.world, new Vector(2.5, 64.0, 0.5), Cinderweaver.cinderweaver(0.8));
        assertEquals("No spider found", commands.error(source, "spider shroud on"));
    }

    /** The task the scheduler was given for {@code delay} ticks. */
    private Runnable scheduled(long delay) {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(commands.scheduler).runTaskLater(eq((Plugin) commands.plugin), task.capture(), eq(delay));
        return task.getValue();
    }

    @Test
    @DisplayName("splay runs after the delay, unless the staff member lost access or the spider is gone")
    void splayRunsAfterTheDelay() throws CommandSyntaxException {
        ECSEntity entity = cinderweaver(0.8);
        try (MockedStatic<Splay> splay = mockStatic(Splay.class)) {
            assertEquals(1, commands.run(source, "spider splay"));
            Runnable now = scheduled(0L);
            splay.verify(() -> Splay.splay(any()), never());
            now.run();
            splay.verify(() -> Splay.splay(entity));

            commands.run(source, "spider splay 40");
            Runnable later = scheduled(40L);
            when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
            later.run();
            when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
            entity.remove();
            later.run();
            splay.verify(() -> Splay.splay(entity));
            assertTrue(commands.error(source, "spider splay -1").contains("must not be less than 0"));
        }
        verify(commands.scheduler, org.mockito.Mockito.times(2)).runTaskLater(any(Plugin.class), any(Runnable.class), anyLong());
    }
}
