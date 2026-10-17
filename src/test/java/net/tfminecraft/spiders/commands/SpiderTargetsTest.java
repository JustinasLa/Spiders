package net.tfminecraft.spiders.commands;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.persistence.UUIDDataType;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static net.tfminecraft.spiders.commands.CommandTestSupport.console;
import static net.tfminecraft.spiders.commands.CommandTestSupport.source;
import static net.tfminecraft.spiders.commands.CommandTestSupport.spider;
import static net.tfminecraft.spiders.commands.CommandTestSupport.staff;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SpiderTargetsTest {
    private final CommandTestSupport commands = new CommandTestSupport();
    private final ECSEntity near = spider(commands.world, new Vector(2.5, 64.0, 0.5));
    private final ECSEntity far = spider(commands.world, new Vector(500.5, 64.0, 0.5));
    private final UUID farId = far.query(SpiderBody.class).getUuid();

    @AfterEach
    void close() {
        commands.close();
    }

    /** A Summoning Seal linked to the spider with {@code id}. */
    private static ItemStack seal(UUID id) {
        ItemStack seal = mock(ItemStack.class);
        ItemMeta meta = mock(ItemMeta.class);
        PersistentDataContainer data = mock(PersistentDataContainer.class);
        when(seal.getItemMeta()).thenReturn(meta);
        when(meta.getPersistentDataContainer()).thenReturn(data);
        when(data.get(any(NamespacedKey.class), eq(UUIDDataType.INSTANCE))).thenReturn(id);
        return seal;
    }

    private static void holdMainHand(Player player, UUID id) {
        ItemStack seal = seal(id);
        when(player.getInventory().getItemInMainHand()).thenReturn(seal);
    }

    private static void holdOffHand(Player player, UUID id) {
        ItemStack seal = seal(id);
        when(player.getInventory().getItemInOffHand()).thenReturn(seal);
    }

    @Test
    @DisplayName("a seal in either hand picks its spider, wherever it is")
    void aSealPicksItsSpider() {
        Player player = staff("Warden", commands.at(0.5, 64.0, 0.5));
        assertEquals(near, SpiderTargets.forCommand(source(player)));
        assertEquals(near, SpiderTargets.forTool(player));

        holdOffHand(player, farId);
        assertEquals(far, SpiderTargets.forCommand(source(player)));
        assertEquals(far, SpiderTargets.forTool(player), "beyond tool range, but linked");

        holdMainHand(player, near.query(SpiderBody.class).getUuid());
        assertEquals(near, SpiderTargets.forCommand(source(player)), "the main hand comes first");
    }

    @Test
    @DisplayName("a seal whose spider is gone falls back to the nearest")
    void aSealWhoseSpiderIsGoneFallsBack() {
        Player player = staff("Warden", commands.at(0.5, 64.0, 0.5));
        holdMainHand(player, farId);
        far.remove();
        assertEquals(near, SpiderTargets.forCommand(source(player)));
    }

    @Test
    @DisplayName("/execute as a player uses that player's seal, as another entity the command's location")
    void executeAsUsesTheExecutor() {
        Player holder = staff("Holder", commands.at(0.5, 64.0, 0.5));
        holdMainHand(holder, farId);
        CommandSourceStack asHolder = source(console(), commands.at(0.0, 64.0, 0.0));
        when(asHolder.getExecutor()).thenReturn(holder);
        assertEquals(far, SpiderTargets.forCommand(asHolder));

        CommandSourceStack asZombie = source(holder, commands.at(499.0, 64.0, 0.0));
        when(asZombie.getExecutor()).thenReturn(mock(Entity.class));
        assertEquals(far, SpiderTargets.forCommand(asZombie), "the sender's seal still counts");

        CommandSourceStack positioned = source(console(), commands.at(499.0, 64.0, 0.0));
        when(positioned.getExecutor()).thenReturn(mock(Entity.class));
        assertEquals(far, SpiderTargets.forCommand(positioned));
        assertNull(SpiderTargets.forCommand(source(console(), new Location(null, 0.0, 64.0, 0.0))), "no world");
    }
}
