package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.testing.RenderTestSupport;
import net.tfminecraft.spiders.utilities.Pair;
import org.bukkit.Location;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class RenderEntityTrackerTest {
    private static final String TAG = "tfmc_spider_render";

    private final RenderTestSupport server = new RenderTestSupport();

    @AfterEach
    void close() {
        server.close();
    }

    private BlockDisplay display() {
        BlockDisplay display = server.entity(BlockDisplay.class, new Location(server.world, 0.0, 0.0, 0.0));
        server.worldEntities.add(display);
        return display;
    }

    @Test
    @DisplayName("put tags and tracks the display under its handle; get hands it back only for its own type")
    void putAndGet() {
        BlockDisplay display = display();
        assertSame(display, RenderEntityTracker.put("a", display));
        assertTrue(display.getScoreboardTags().contains(TAG));
        assertSame(display, RenderEntityTracker.get("a", BlockDisplay.class));

        assertNull(RenderEntityTracker.get("missing", BlockDisplay.class));
        assertNull(RenderEntityTracker.get("a", ItemDisplay.class));
        verify(display).remove();
        assertTrue(RenderEntityTracker.getAll().isEmpty());
    }

    @Test
    @DisplayName("a second put under a taken handle keeps the first display")
    void putKeepsTheFirst() {
        BlockDisplay first = display();
        BlockDisplay second = display();
        RenderEntityTracker.put("a", first);
        RenderEntityTracker.put("a", second);
        assertEquals(List.of(new Pair<Object, Entity>("a", first)), RenderEntityTracker.getAll());
    }

    @Test
    @DisplayName("at the end of each tick, displays nobody submitted that tick are removed")
    void tickEndRemovesUnused() {
        BlockDisplay kept = display();
        BlockDisplay dropped = display();
        RenderEntityTracker.put("kept", kept);
        RenderEntityTracker.put("dropped", dropped);
        RenderTestSupport.runTrackerTickEnd();
        assertEquals(2, RenderEntityTracker.getAll().size(), "both were used the tick they were put");

        RenderEntityTracker.get("kept", BlockDisplay.class);
        RenderTestSupport.runTrackerTickEnd();

        verify(dropped).remove();
        verify(kept, never()).remove();
        assertEquals(List.of(new Pair<Object, Entity>("kept", kept)), RenderEntityTracker.getAll());

        RenderTestSupport.runTrackerTickEnd();
        verify(kept).remove();
        assertTrue(RenderEntityTracker.getAll().isEmpty());
    }

    @Test
    @DisplayName("detach hands a display over: untagged, untracked and left alive")
    void detach() {
        BlockDisplay display = display();
        RenderEntityTracker.put("a", display);

        RenderEntityTracker.detach("a");
        RenderEntityTracker.detach("never tracked");
        RenderTestSupport.runTrackerTickEnd();

        assertFalse(display.getScoreboardTags().contains(TAG));
        assertTrue(RenderEntityTracker.getAll().isEmpty());
        verify(display, never()).remove();
    }

    @Test
    @DisplayName("remove kills the tracked display; an unknown handle is ignored")
    void remove() {
        BlockDisplay display = display();
        RenderEntityTracker.put("a", display);
        RenderEntityTracker.remove("b");
        RenderEntityTracker.remove("a");

        verify(display).remove();
        assertTrue(RenderEntityTracker.getAll().isEmpty());
    }

    @Test
    @DisplayName("on plugin shutdown every still-tagged display is removed and the tracker forgets them all")
    void shutdownRemovesAll() throws Exception {
        BlockDisplay tagged = display();
        BlockDisplay untagged = display();
        RenderEntityTracker.put("tagged", tagged);
        RenderEntityTracker.put("untagged", untagged);
        untagged.removeScoreboardTag(TAG);

        RenderTestSupport.runTrackerShutdownHook();

        verify(tagged).remove();
        verify(untagged, never()).remove();
        assertTrue(RenderEntityTracker.getAll().isEmpty());
    }

    @Test
    @DisplayName("removeAllByTag clears displays left in the world by an earlier crash, and nothing else")
    void removeAllByTag() {
        BlockDisplay leftover = display();
        leftover.addScoreboardTag(TAG);
        BlockDisplay unrelated = display();

        RenderEntityTracker.removeAllByTag();

        verify(leftover).remove();
        verify(unrelated, never()).remove();
        assertEquals(List.of(unrelated), server.worldEntities);
    }
}
