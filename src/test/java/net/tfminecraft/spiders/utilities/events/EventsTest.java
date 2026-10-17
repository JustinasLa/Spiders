package net.tfminecraft.spiders.utilities.events;

import net.tfminecraft.spiders.utilities.Disposable;
import net.tfminecraft.spiders.utilities.UtilitiesTestSupport;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EventsTest {
    private UtilitiesTestSupport support;

    @BeforeEach
    void setup() {
        support = UtilitiesTestSupport.install();
    }

    private static Block block(BlockData data, Material type, BlockState state) {
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(data);
        when(block.getType()).thenReturn(type);
        when(block.getState(false)).thenReturn(state);
        return block;
    }

    private static Block plainBlock() {
        return block(mock(BlockData.class), Material.STONE, mock(BlockState.class));
    }

    private static Block door() {
        return block(mock(Door.class), Material.OAK_DOOR, mock(BlockState.class));
    }

    @Test
    @DisplayName("a registered listener is unregistered when disposed")
    void listenersUnregisterWhenDisposed() {
        Listener listener = new Listener() {
        };
        Disposable registration = Events.addEventListener(listener);
        verify(support.manager).registerEvents(listener, support.plugin);

        try (MockedStatic<HandlerList> handlers = mockStatic(HandlerList.class)) {
            registration.close();
            handlers.verify(() -> HandlerList.unregisterAll(listener));
        }
    }

    @Test
    @DisplayName("entity interactions reach both listener styles")
    void entityInteractionsReachBothStyles() {
        Player player = mock(Player.class);
        Entity clicked = mock(Entity.class);
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(clicked);
        when(event.getHand()).thenReturn(EquipmentSlot.OFF_HAND);
        List<Object> heard = new ArrayList<>();
        Events.onInteractEntity((Player who, Entity what, EquipmentSlot hand) -> heard.addAll(List.of(who, what, hand)));
        Events.onInteractEntity((PlayerInteractEntityEvent it) -> heard.add(it));

        support.fire("onInteract", PlayerInteractEntityEvent.class, event);

        assertEquals(List.of(player, clicked, EquipmentSlot.OFF_HAND, event), heard);
    }

    @Test
    @DisplayName("gesture use hears right clicks with an item, but not clicks that open a block")
    void gestureUseHearsRightClicksWithAnItem() {
        List<ItemStack> used = new ArrayList<>();
        Events.onGestureUseItem((player, item) -> used.add(item));
        Player player = mock(Player.class);
        Player sneaking = mock(Player.class);
        when(sneaking.isSneaking()).thenReturn(true);
        ItemStack item = mock(ItemStack.class);

        fireUse(player, Action.LEFT_CLICK_AIR, item, null);
        fireUse(player, Action.RIGHT_CLICK_AIR, null, null);
        fireUse(player, Action.RIGHT_CLICK_BLOCK, item, door());
        assertTrue(used.isEmpty());

        fireUse(player, Action.RIGHT_CLICK_AIR, item, null);
        fireUse(sneaking, Action.RIGHT_CLICK_BLOCK, item, door());
        fireUse(player, Action.RIGHT_CLICK_BLOCK, item, plainBlock());
        assertEquals(3, used.size());
        assertSame(item, used.getFirst());
    }

    private void fireUse(Player player, Action action, ItemStack item, Block block) {
        support.fire("onPlayerInteract", PlayerInteractEvent.class, new PlayerInteractEvent(player, action, item, block, BlockFace.UP, EquipmentSlot.HAND));
    }

    @Test
    @DisplayName("doors, switches, ender chests and containers open without using the item")
    void whatOpensWithoutTheItem() {
        assertFalse(Events.opensWithoutItem(null));
        assertTrue(Events.opensWithoutItem(door()));
        assertTrue(Events.opensWithoutItem(block(mock(Switch.class), Material.LEVER, mock(BlockState.class))));
        assertTrue(Events.opensWithoutItem(block(mock(BlockData.class), Material.ENDER_CHEST, mock(BlockState.class))));
        assertTrue(Events.opensWithoutItem(block(mock(BlockData.class), Material.CHEST, mock(Chest.class))));
        assertFalse(Events.opensWithoutItem(plainBlock()));
    }
}
