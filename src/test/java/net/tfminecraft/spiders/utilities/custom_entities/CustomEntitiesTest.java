package net.tfminecraft.spiders.utilities.custom_entities;

import net.tfminecraft.spiders.utilities.UtilitiesTestSupport;
import net.tfminecraft.spiders.utilities.events.SchedulerTestSupport;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomEntitiesTest {
    private static final String TAG = "tfmcspiders_render";

    private final Server server = mock(Server.class);
    private final Entity tagged = entity(TAG, "other");
    private final Entity plain = entity("other");
    private final Entity taggedElsewhere = entity(TAG);
    private MockedStatic<Bukkit> bukkit;

    private static Entity entity(String... tags) {
        Entity entity = mock(Entity.class);
        when(entity.getScoreboardTags()).thenReturn(Set.of(tags));
        return entity;
    }

    @BeforeEach
    void setup() {
        World overworld = mock(World.class);
        World nether = mock(World.class);
        when(overworld.getEntities()).thenReturn(List.of(tagged, plain));
        when(nether.getEntities()).thenReturn(List.of(taggedElsewhere));
        when(server.getWorlds()).thenReturn(List.of(overworld, nether));
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(Bukkit::getServer).thenReturn(server);
    }

    @AfterEach
    void close() {
        bukkit.close();
    }

    @Test
    @DisplayName("every entity in every world")
    void allEntitiesSpansWorlds() {
        assertEquals(List.of(tagged, plain, taggedElsewhere), CustomEntities.allEntities());
    }

    @Test
    @DisplayName("a component is a scoreboard tag named after its key")
    void aComponentIsAScoreboardTag() {
        CustomEntityComponent component = CustomEntityComponent.create(new NamespacedKey("tfmcspiders", "render"));
        assertEquals(TAG, component.getTag());
        assertEquals("custom", CustomEntityComponent.fromString("custom").getTag());

        Entity entity = mock(Entity.class);
        CustomEntities.attach(entity, component);
        verify(entity).addScoreboardTag(TAG);
        CustomEntities.detach(entity, component);
        verify(entity).removeScoreboardTag(TAG);

        assertTrue(component.isAttached(tagged));
        assertFalse(component.isAttached(plain));
        assertEquals(List.of(tagged, taggedElsewhere), component.entities());
    }

    @Test
    @DisplayName("tick handlers run for each tagged entity")
    void tickHandlersRunForTaggedEntities() {
        UtilitiesTestSupport.install();
        SchedulerTestSupport.clearTickHandlers();
        try {
            List<Entity> ticked = new ArrayList<>();
            CustomEntityComponent.fromString(TAG).onTick(ticked::add);
            SchedulerTestSupport.tick();
            assertEquals(List.of(tagged, taggedElsewhere), ticked);
        } finally {
            SchedulerTestSupport.clearTickHandlers();
        }
    }

    @Test
    @DisplayName("interaction handlers only hear about tagged entities")
    void interactHandlersOnlyHearTaggedEntities() {
        UtilitiesTestSupport support = UtilitiesTestSupport.install();
        List<PlayerInteractEntityEvent> heard = new ArrayList<>();
        CustomEntityComponent.fromString(TAG).onInteract(heard::add);

        PlayerInteractEntityEvent onTagged = mock(PlayerInteractEntityEvent.class);
        when(onTagged.getRightClicked()).thenReturn(tagged);
        PlayerInteractEntityEvent onPlain = mock(PlayerInteractEntityEvent.class);
        when(onPlain.getRightClicked()).thenReturn(plain);
        support.fire("onInteract", PlayerInteractEntityEvent.class, onTagged);
        support.fire("onInteract", PlayerInteractEntityEvent.class, onPlain);

        assertEquals(List.of(onTagged), heard);
    }
}
