package net.tfminecraft.spiders.spider.components;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

class MimicHauntTest {
    private MimicServer server;
    private MimicDice dice;
    private final World world = mock(World.class);
    private Runnable update;

    @BeforeEach
    void start() {
        server = new MimicServer();
        dice = new MimicDice();
        MimicHaunt.setup();
        assertEquals(1, server.tasks.size());
        assertEquals(10, server.tasks.get(0).delay());
        assertEquals(10, server.tasks.get(0).period());
        update = server.timers().get(0);
    }

    @AfterEach
    void stop() {
        dice.close();
        server.close();
    }

    /** Sounds only {@code player} heard, as (key, location). */
    private static List<Heard> heard(Player player) {
        List<Heard> out = new ArrayList<>();
        mockingDetails(player).getInvocations().stream()
            .filter(it -> it.getMethod().getName().equals("playSound") && it.getArguments().length == 5 && it.getArguments()[1] instanceof String)
            .forEach(it -> out.add(new Heard((String) it.getArguments()[1], ((Location) it.getArguments()[0]).toVector())));
        return out;
    }

    private record Heard(String sound, Vector at) {
    }

    private void tick(int times) {
        for (int i = 0; i < times; i++) update.run();
    }

    @Test
    @DisplayName("after a mimic runs, its prey hears it skitter from just behind them, every so often, until the haunting ends")
    void hauntsFromBehindUntilItEnds() {
        Player player = server.player(world, new Vector(0, 0, 0), new Vector(1, 0, 0));
        MimicHaunt.haunt(player, 400);
        // a shorter haunting doesn't cut a longer one short
        MimicHaunt.haunt(player, 100);

        // the first sound comes after 100 ticks (the low end of 100-200)
        tick(9);
        assertTrue(heard(player).isEmpty());
        tick(1);
        List<Heard> sounds = heard(player);
        assertEquals(1, sounds.size());
        assertEquals("tfmc_spiders:" + MimicTerror.SKITTER, sounds.get(0).sound());
        // 4 blocks behind their eyes, 3 to their right and a little low
        assertEquals(new Vector(-4, 1.62 - 1.2, -3), sounds.get(0).at());

        // then every 140 ticks or more
        tick(13);
        assertEquals(1, heard(player).size());
        tick(1);
        assertEquals(2, heard(player).size());

        // 400 ticks in all: the third and last whisper is at 380, and then it's over
        tick(13);
        assertEquals(2, heard(player).size());
        tick(1);
        assertEquals(3, heard(player).size());
        tick(2);
        clearInvocations(player);
        tick(100);
        assertTrue(heard(player).isEmpty(), "still haunted after it ended");
    }

    @Test
    @DisplayName("each whisper is one of skittering, breathing, a creak, a far shriek, or a chomp with a racing heart")
    void everyKindOfWhisper() {
        Player player = server.player(world, new Vector(0, 0, 0), new Vector(1, 0, 0));
        MimicHaunt.haunt(player, 100_000);
        List<String> kinds = new ArrayList<>();
        Vector shriekAt = null;
        for (int roll = 0; roll < 6; roll++) {
            dice.ints.add(roll);
            clearInvocations(player);
            for (int i = 0; i < 20 && heard(player).isEmpty(); i++) update.run();
            List<Heard> sounds = heard(player);
            kinds.add(sounds.get(0).sound().replace("tfmc_spiders:", ""));
            if (roll == 4) shriekAt = sounds.get(0).at();
            if (roll == 5) {
                // the chomp, and their heart pounding as if it were six blocks off
                assertEquals("tfmc_spiders:" + MimicTerror.HEARTBEAT, sounds.get(1).sound());
                assertEquals("minecraft:entity.warden.heartbeat", sounds.get(2).sound());
            }
        }
        assertEquals(List.of(MimicTerror.SKITTER, MimicTerror.SKITTER, MimicTerror.BREATH, MimicTerror.CREAK, MimicTerror.SHRIEK, MimicTerror.CHOMP), kinds);
        // the shriek comes from four times as far behind
        assertEquals(new Vector(-16, 1.62 - 4.8, -12), shriekAt);
    }

    @Test
    @DisplayName("looking straight up, the sound still comes from behind them")
    void lookingStraightUp() {
        Player player = server.player(world, new Vector(0, 0, 0), new Vector(0, 1, 0));
        MimicHaunt.haunt(player, 400);
        tick(10);
        Vector at = heard(player).get(0).at();
        assertEquals(-4.0, at.getZ(), 1e-9);
        assertEquals(3.0, at.getX(), 1e-9);
    }

    @Test
    @DisplayName("players who log off, die or spectate are no longer haunted")
    void theGoneAreLetGo() {
        Player offline = server.player(world, new Vector(), new Vector(1, 0, 0));
        Player spectator = server.player(world, new Vector(), new Vector(1, 0, 0));
        MimicHaunt.haunt(offline, 10_000);
        MimicHaunt.haunt(spectator, 10_000);
        server.players.remove(offline.getUniqueId());
        when(spectator.getGameMode()).thenReturn(GameMode.SPECTATOR);
        tick(1);

        // back again, they hear nothing: the haunting was dropped, not paused
        server.players.put(offline.getUniqueId(), offline);
        when(spectator.getGameMode()).thenReturn(GameMode.SURVIVAL);
        tick(100);
        assertTrue(heard(offline).isEmpty());
        assertTrue(heard(spectator).isEmpty());
    }
}
