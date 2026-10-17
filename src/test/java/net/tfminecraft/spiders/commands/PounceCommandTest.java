package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.spider.components.Pounce;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.spider.presets.Cinderweaver;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static net.tfminecraft.spiders.commands.CommandTestSupport.console;
import static net.tfminecraft.spiders.commands.CommandTestSupport.lastMessage;
import static net.tfminecraft.spiders.commands.CommandTestSupport.messages;
import static net.tfminecraft.spiders.commands.CommandTestSupport.player;
import static net.tfminecraft.spiders.commands.CommandTestSupport.source;
import static net.tfminecraft.spiders.commands.CommandTestSupport.spider;
import static net.tfminecraft.spiders.commands.CommandTestSupport.staff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PounceCommandTest {
    private final CommandTestSupport commands = new CommandTestSupport();
    private final Player staff = staff("Warden", commands.at(0.5, 64.0, 0.5));
    private final CommandSourceStack source = source(staff);
    private final Player steve = player("Steve", commands.at(10.5, 64.0, 0.5), false);
    private final ECSEntity spider = spider(commands.world, new Vector(2.5, 64.0, 0.5), Cinderweaver.cinderweaver(0.8));
    private final SpiderOptions options = spider.query(SpiderOptions.class);

    @AfterEach
    void close() {
        commands.close();
    }

    private Pounce pounce() {
        Pounce pounce = spider.query(Pounce.class);
        assertNotNull(pounce, "not pouncing");
        return pounce;
    }

    private static String oneDecimal(double value) {
        return "%.1f".formatted(value);
    }

    private void moveTo(Player player, Location location) {
        when(player.getLocation()).thenAnswer(call -> location.clone());
        when(player.getWorld()).thenReturn(location.getWorld());
    }

    @Test
    @DisplayName("pouncing at a player chases them while they can be reached")
    void pouncingAtAPlayerChasesThemWhileReachable() throws CommandSyntaxException {
        commands.online.add(steve);
        assertEquals(1, commands.run(source, "spider pounce Steve 8"));
        assertEquals(8.0 / 20, options.getGallopGait().getMaxSpeed(), 1e-9);
        assertEquals("Pouncing at Steve (gallop " + oneDecimal(8.0) + " blocks/s). /spider pounce stop cancels.", lastMessage(staff));

        Pounce pounce = pounce();
        assertEquals(new Vector(10.5, 64.0, 0.5), pounce.getTarget().get());

        when(steve.getGameMode()).thenReturn(GameMode.SPECTATOR);
        assertNull(pounce.getTarget().get(), "spectators can't be chased");
        when(steve.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertEquals(new Vector(10.5, 64.0, 0.5), pounce.getTarget().get());

        moveTo(steve, commands.at(200.5, 64.0, 0.5));
        assertNull(pounce.getTarget().get(), "too far away");
        moveTo(steve, new Location(commands.nether, 10.5, 64.0, 0.5));
        assertNull(pounce.getTarget().get(), "in another world");
        moveTo(steve, commands.at(5.5, 64.0, 0.5));
        assertEquals(new Vector(5.5, 64.0, 0.5), pounce.getTarget().get(), "follows them as they move");
        when(steve.isOnline()).thenReturn(false);
        assertNull(pounce.getTarget().get(), "logged out");
        when(steve.isOnline()).thenReturn(true);

        when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
        assertNull(pounce.getTarget().get(), "the staff member lost access");
        when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        when(staff.isOnline()).thenReturn(false);
        assertNull(pounce.getTarget().get(), "the staff member left");
    }

    @Test
    @DisplayName("the staff member hears how each pounce ended, unless they left")
    void theStaffMemberHearsHowThePounceEnded() throws CommandSyntaxException {
        commands.online.add(steve);
        commands.run(source, "spider pounce Steve");
        Pounce pounce = pounce();

        pounce.getOnEnd().accept(Pounce.Outcome.LANDED, 1.26);
        assertEquals("Pounced at Steve: landed " + oneDecimal(1.26) + " blocks away", lastMessage(staff));
        pounce.getOnEnd().accept(Pounce.Outcome.LANDED, null);
        assertEquals("Pounced at Steve: landed " + oneDecimal(0.0) + " blocks away", lastMessage(staff));
        pounce.getOnEnd().accept(Pounce.Outcome.TARGET_LOST, null);
        assertEquals("Pounce ended: lost Steve", lastMessage(staff));
        pounce.getOnEnd().accept(Pounce.Outcome.GAVE_UP, null);
        assertEquals("Pounce ended: could not reach Steve in time", lastMessage(staff));
        pounce.getOnEnd().accept(Pounce.Outcome.STOPPED, null);
        assertEquals("Pounce at Steve stopped", lastMessage(staff));

        int heard = messages(staff).size();
        when(staff.isOnline()).thenReturn(false);
        pounce.getOnEnd().accept(Pounce.Outcome.GAVE_UP, null);
        assertEquals(heard, messages(staff).size());
    }

    @Test
    @DisplayName("names match any online player ignoring case, and must be in the spider's world")
    void namesMatchIgnoringCase() throws CommandSyntaxException {
        Player alex = player("Alex", commands.at(4.5, 64.0, 0.5), false);
        commands.online.add(alex);
        commands.online.add(steve);
        commands.run(source, "spider pounce sTEVE");
        assertTrue(lastMessage(staff).startsWith("Pouncing at Steve "));
        assertEquals("Player not found", commands.error(source, "spider pounce Herobrine"));

        moveTo(alex, new Location(commands.nether, 4.5, 64.0, 0.5));
        assertEquals("Alex is not in the spider's world", commands.error(source, "spider pounce Alex 6"));
    }

    @Test
    @DisplayName("a new pounce or pounce stop ends the running one")
    void pounceStopEndsTheRunningOne() throws CommandSyntaxException {
        commands.online.add(steve);
        assertEquals(1, commands.run(source, "spider pounce stop"));
        assertEquals("That spider is not pouncing", lastMessage(staff));

        commands.run(source, "spider pounce Steve");
        Pounce first = pounce();
        commands.run(source, "spider pounce at 10 64 -3");
        assertTrue(messages(staff).contains("Pounce at Steve stopped"), "the first pounce was stopped");
        assertNotSame(first, pounce());

        commands.run(source, "spider pounce stop");
        assertEquals("Pounce at 10, 64, -3 stopped", lastMessage(staff));
        assertNull(spider.query(Pounce.class));
    }

    @Test
    @DisplayName("pounce at a spot runs for console too and stops if they lose access")
    void pounceAtASpot() throws CommandSyntaxException {
        ConsoleCommandSender console = console();
        CommandSourceStack fromConsole = source(console, commands.at(0.0, 64.0, 0.0));
        assertEquals(1, commands.run(fromConsole, "spider pounce at 10.7 64 -3.2 16"));
        assertEquals("Pouncing at 10, 64, -4 (gallop " + oneDecimal(16.0) + " blocks/s). /spider pounce stop cancels.", lastMessage(console));
        assertEquals(16.0 / 20, options.getGallopGait().getMaxSpeed(), 1e-9);
        assertEquals(new Vector(10.7, 64.0, -3.2), pounce().getTarget().get());
        pounce().getOnEnd().accept(Pounce.Outcome.GAVE_UP, null);
        assertEquals("Pounce ended: could not reach 10, 64, -4 in time", lastMessage(console));
        when(console.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
        assertNull(pounce().getTarget().get());

        double gallop = options.getGallopGait().getMaxSpeed();
        when(console.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        commands.run(fromConsole, "spider pounce at 0 64 0");
        assertEquals(gallop, options.getGallopGait().getMaxSpeed(), 1e-9, "no speed keeps the gallop speed");
        assertTrue(commands.error(fromConsole, "spider pounce at 30000000 64 0").contains("must not be more than"));
    }

    @Test
    @DisplayName("pounce nearest picks the closest player in range who isn't spectating")
    void pounceNearestPicksTheClosestPlayer() throws CommandSyntaxException {
        Player spectator = player("Ghost", commands.at(3.5, 64.0, 0.5), false);
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        Player far = player("Far", commands.at(150.5, 64.0, 0.5), false);
        Player middle = player("Middle", commands.at(22.5, 64.0, 0.5), false);
        Player near = player("Near", commands.at(6.5, 64.0, 0.5), false);
        Player further = player("Further", commands.at(40.5, 64.0, 0.5), false);
        when(commands.world.getPlayers()).thenReturn(List.of(spectator, far, middle, near, further));

        commands.run(source, "spider pounce nearest");
        assertTrue(lastMessage(staff).startsWith("Pouncing at Near "), lastMessage(staff));
        assertEquals(new Vector(6.5, 64.0, 0.5), pounce().getTarget().get());

        commands.run(source, "spider pounce nearest 5");
        assertEquals(5.0 / 20, options.getGallopGait().getMaxSpeed(), 1e-9);

        when(commands.world.getPlayers()).thenReturn(List.of(spectator, far));
        assertEquals("No player within 96 blocks of the spider", commands.error(source, "spider pounce nearest"));
    }

    @Test
    @DisplayName("a spider without options can't pounce")
    void aSpiderWithoutOptionsCantPounce() {
        spider.getComponents().remove(options);
        assertEquals("No spider found", commands.error(source, "spider pounce at 0 64 0"));
    }

    @Test
    @DisplayName("a target without a body is reported as no spider")
    void aTargetWithoutABodyIsNoSpider() {
        commands.online.add(steve);
        ECSEntity bodiless = new ECSEntity();
        bodiless.addComponent(options);
        try (MockedStatic<SpiderTargets> targets = mockStatic(SpiderTargets.class)) {
            targets.when(() -> SpiderTargets.forCommand(any())).thenReturn(bodiless);
            assertEquals("No spider found", commands.error(source, "spider pounce at 0 64 0"));
            assertEquals("No spider found", commands.error(source, "spider pounce nearest"));
            assertEquals("No spider found", commands.error(source, "spider scale 1"));
            assertEquals("Steve is not in the spider's world", commands.error(source, "spider pounce Steve"));
        }
    }

    @Test
    @DisplayName("pounce suggests online players alongside its subcommands")
    void pounceSuggestsOnlinePlayers() {
        commands.online.add(steve);
        commands.online.add(player("Alex", commands.at(0.0, 64.0, 0.0), false));
        assertEquals(List.of("Steve", "stop"), commands.suggest(source, "spider pounce st"));
        assertEquals(List.of("Alex", "at", "nearest", "Steve", "stop"), commands.suggest(source, "spider pounce "));
    }
}
