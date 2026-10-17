package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.testing.RenderTestSupport;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RenderVisibilityTest {
    private final RenderTestSupport server = new RenderTestSupport();
    private final Player viewer = mock(Player.class);
    private final Player other = mock(Player.class);
    private final Location location = new Location(server.world, 0.0, 0.0, 0.0);

    @AfterEach
    void close() {
        server.close();
    }

    private void online(Player... players) {
        server.online.clear();
        server.online.addAll(List.of(players));
    }

    @Test
    @DisplayName("displays rendered for some players spawn hidden and are shown to those players only")
    void shownToViewersOnly() {
        online(viewer, other);
        RenderVisibility.onlyFor(List.of(viewer), () -> new RenderEntity<>(BlockDisplay.class, location).submit("debug"));

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        verify(display).setVisibleByDefault(false);
        verify(viewer).showEntity(server.plugin, display);
        verify(other, never()).hideEntity(any(), any(Entity.class));
    }

    @Test
    @DisplayName("each later submit shows it to new viewers and hides it from those who stopped viewing")
    void followsTheViewers() {
        online(viewer, other);
        RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location);
        RenderVisibility.onlyFor(List.of(viewer), () -> render.submit("debug"));
        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        when(viewer.canSee(display)).thenReturn(true);

        RenderVisibility.onlyFor(List.of(other), () -> render.submit("debug"));

        verify(viewer).hideEntity(server.plugin, display);
        verify(other).showEntity(server.plugin, display);
    }

    @Test
    @DisplayName("players who already see what they should are left alone")
    void unchangedViewersUntouched() {
        online(viewer, other);
        RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location);
        RenderVisibility.onlyFor(List.of(viewer), () -> render.submit("debug"));
        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        when(viewer.canSee(display)).thenReturn(true);

        RenderVisibility.onlyFor(List.of(viewer), () -> render.submit("debug"));

        verify(viewer).showEntity(server.plugin, display);
        verify(viewer, never()).hideEntity(any(), any(Entity.class));
        verify(other, never()).showEntity(any(), any(Entity.class));
    }

    @Test
    @DisplayName("outside onlyFor displays are public and nobody's visibility is touched")
    void publicByDefault() {
        online(viewer);
        RenderEntity<BlockDisplay> render = new RenderEntity<>(BlockDisplay.class, location);
        render.submit("public");
        render.submit("public");

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        verify(display, never()).setVisibleByDefault(false);
        verify(viewer, never()).canSee(any(Entity.class));
    }

    @Test
    @DisplayName("nested onlyFor restores the outer viewers, even when the render throws")
    void nestingRestoresViewers() {
        online(viewer, other);
        RenderEntity<BlockDisplay> outer = new RenderEntity<>(BlockDisplay.class, location);
        RenderVisibility.onlyFor(List.of(viewer), () -> {
            assertThrows(IllegalStateException.class, () -> RenderVisibility.onlyFor(List.of(other), () -> {
                throw new IllegalStateException("render failed");
            }));
            outer.submit("outer");
        });

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        verify(viewer).showEntity(server.plugin, display);
        verify(other, never()).showEntity(any(), any(Entity.class));

        new RenderEntity<>(BlockDisplay.class, location).submit("after");
        verify(server.spawned(BlockDisplay.class).getLast(), never()).setVisibleByDefault(false);
        assertSame(display, server.spawned(BlockDisplay.class).getFirst());
    }
}
