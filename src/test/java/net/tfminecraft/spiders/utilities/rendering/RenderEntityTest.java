package net.tfminecraft.spiders.utilities.rendering;

import io.papermc.paper.entity.TeleportFlag;
import net.tfminecraft.spiders.testing.RenderTestSupport;
import net.tfminecraft.spiders.utilities.Pair;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RenderEntityTest {
    private final RenderTestSupport server = new RenderTestSupport();
    private final World world = server.world;

    @AfterEach
    void close() {
        server.close();
    }

    @Test
    @DisplayName("the first submit spawns a non-persistent, tagged display; later ones reuse it and run only the update")
    void spawnsOnceThenUpdates() {
        Location location = new Location(world, 1.0, 2.0, 3.0);
        List<String> calls = new ArrayList<>();
        RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location, it -> calls.add("init"), it -> calls.add("update"));

        render.submit("handle");
        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        assertEquals(List.of("init", "update"), calls);
        verify(display).setPersistent(false);
        verify(display, never()).setVisibleByDefault(anyBoolean());
        assertTrue(display.getScoreboardTags().contains("tfmc_spider_render"));
        assertEquals(List.of(new Pair<Object, Entity>("handle", display)), RenderEntityTracker.getAll());

        render.submit("handle");
        assertEquals(List.of("init", "update", "update"), calls);
        assertEquals(1, server.spawned.size());
        verify(display, never()).teleport(any(Location.class), any(TeleportFlag[].class));
    }

    @Test
    @DisplayName("a moved location teleports the display, keeping its passengers")
    void teleportsWhenMoved() {
        Location location = new Location(world, 1.0, 2.0, 3.0);
        RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location);
        render.submit("handle");
        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();

        location.setY(9.0);
        render.submit("handle");

        verify(display).teleport(location, TeleportFlag.EntityState.RETAIN_PASSENGERS);
        assertEquals(9.0, display.getLocation().getY());
    }

    @Test
    @DisplayName("any difference in world, coordinates or facing counts as moved")
    void everyComponentOfThePoseIsCompared() {
        Location location = new Location(world, 1.0, 2.0, 3.0, 10f, 20f);
        List<Consumer<Location>> drifts = List.of(
            it -> it.setWorld(mock(World.class)),
            it -> it.setX(5.0),
            it -> it.setY(5.0),
            it -> it.setZ(5.0),
            it -> it.setYaw(45f),
            it -> it.setPitch(45f)
        );
        for (int index = 0; index < drifts.size(); index++) {
            RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location);
            render.submit(index);
            BlockDisplay display = server.spawned(BlockDisplay.class).getLast();
            Location drifted = location.clone();
            drifts.get(index).accept(drifted);
            server.place(display, drifted);

            render.submit(index);

            verify(display, times(1)).teleport(location, TeleportFlag.EntityState.RETAIN_PASSENGERS);
            assertEquals(location, display.getLocation());
        }
    }

    @Test
    @DisplayName("a display riding a vehicle is carried by it and not teleported")
    void ridersAreNotTeleported() {
        Location location = new Location(world, 1.0, 2.0, 3.0);
        int[] updates = {0};
        RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location, it -> {}, it -> updates[0]++);
        render.submit("seat");
        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        when(display.isInsideVehicle()).thenReturn(true);

        location.setX(50.0);
        render.submit("seat");

        verify(display, never()).teleport(any(Location.class), any(TeleportFlag[].class));
        assertEquals(2, updates[0]);
    }

    @Test
    @DisplayName("a non-finite location is reported and nothing is spawned")
    void nonFiniteLocationIgnored() {
        PrintStream original = System.err;
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        try {
            new RenderEntity<>(BlockDisplay.class, new Location(world, Double.NaN, 0.0, 0.0)).submit("handle");
        } finally {
            System.setErr(original);
        }

        assertTrue(err.toString(StandardCharsets.UTF_8).contains("non-finite location"));
        assertTrue(server.spawned.isEmpty());
        assertTrue(RenderEntityTracker.getAll().isEmpty());
    }

    @Test
    @DisplayName("a display that died, or is of another type, is replaced by a fresh one")
    void replacedWhenInvalidOrRetyped() {
        Location location = new Location(world, 1.0, 2.0, 3.0);
        new RenderEntity<>(BlockDisplay.class, location).submit("handle");
        BlockDisplay first = server.spawned(BlockDisplay.class).getFirst();

        first.remove(); // killed by something else
        new RenderEntity<>(BlockDisplay.class, location).submit("handle");
        BlockDisplay second = server.spawned(BlockDisplay.class).getLast();
        assertNotSame(first, second);

        new RenderEntity<>(ItemDisplay.class, location).submit("handle");
        verify(second).remove();
        ItemDisplay item = server.spawned(ItemDisplay.class).getFirst();
        assertEquals(List.of(new Pair<Object, Entity>("handle", item)), RenderEntityTracker.getAll());
    }

    @Test
    @DisplayName("the shorter constructors default to doing nothing, and the getters return what was given")
    void constructorsAndGetters() {
        Location location = new Location(world, 1.0, 2.0, 3.0);
        Consumer<BlockDisplay> init = it -> {};
        Consumer<BlockDisplay> update = it -> {};
        RenderEntity<BlockDisplay> full = new RenderEntity<>(BlockDisplay.class, location, init, update);
        assertSame(BlockDisplay.class, full.getClazz());
        assertSame(location, full.getLocation());
        assertSame(init, full.getInit());
        assertSame(update, full.getUpdate());

        BlockDisplay display = mock(BlockDisplay.class);
        RenderEntity<BlockDisplay> initOnly = new RenderEntity<>(BlockDisplay.class, location, init);
        assertSame(init, initOnly.getInit());
        initOnly.getUpdate().accept(display);
        RenderEntity<BlockDisplay> bare = new RenderEntity<>(BlockDisplay.class, location);
        bare.getInit().accept(display);
        bare.getUpdate().accept(display);
        verifyNoInteractions(display);
    }

    @Test
    @DisplayName("an empty render item draws nothing")
    void emptyItemDrawsNothing() {
        EmptyRenderItem.INSTANCE.submit("handle");
        assertTrue(server.spawned.isEmpty());
        assertFalse(RenderEntityTracker.getAll().iterator().hasNext());
    }
}
