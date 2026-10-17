package net.tfminecraft.spiders.spider.components;

import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.StaffAccess;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MimicChestListenerTest {
    private MimicServer server;
    private MimicWorld blocks;
    private MockedStatic<MimicChests> chests;
    private final MimicChestListener listener = new MimicChestListener();
    private Block mimic;
    private Block chest;
    private Player player;

    @BeforeEach
    void start() {
        server = new MimicServer();
        blocks = new MimicWorld(0);
        mimic = blocks.chest(0, 1, 0, BlockFace.NORTH, true);
        chest = blocks.chest(1, 1, 0, BlockFace.NORTH, false);
        player = server.player(blocks.world, new Vector(0, 1, 2), new Vector(0, 0, -1));
        // waking is covered in MimicChestsTest; here only whether the listener asks for it
        chests = mockStatic(MimicChests.class, CALLS_REAL_METHODS);
        chests.when(() -> MimicChests.wake(any(), any())).thenReturn(true);
    }

    @AfterEach
    void stop() {
        chests.close();
        server.close();
    }

    private PlayerInteractEvent click(Action action, Block block, EquipmentSlot hand) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, null, block, BlockFace.UP, hand);
        listener.onInteract(event);
        return event;
    }

    private static boolean denied(PlayerInteractEvent event) {
        return event.useInteractedBlock() == Event.Result.DENY && event.useItemInHand() == Event.Result.DENY;
    }

    @Test
    @DisplayName("opening or hitting a dormant mimic wakes it instead")
    void touchingItWakesIt() {
        assertTrue(denied(click(Action.RIGHT_CLICK_BLOCK, mimic, EquipmentSlot.HAND)));
        chests.verify(() -> MimicChests.wake(mimic, player));
        assertTrue(denied(click(Action.LEFT_CLICK_BLOCK, mimic, EquipmentSlot.HAND)));
        chests.verify(() -> MimicChests.wake(mimic, player), org.mockito.Mockito.times(2));
    }

    @Test
    @DisplayName("the off-hand's click is refused too, but wakes nothing a second time")
    void offHandClickIsRefusedOnly() {
        assertTrue(denied(click(Action.RIGHT_CLICK_BLOCK, mimic, EquipmentSlot.OFF_HAND)));
        chests.verify(() -> MimicChests.wake(any(), any()), never());
    }

    @Test
    @DisplayName("ordinary chests, empty air, clicks at air and stepping on it are left alone")
    void othersAreLeftAlone() {
        assertFalse(denied(click(Action.RIGHT_CLICK_BLOCK, chest, EquipmentSlot.HAND)));
        assertFalse(denied(click(Action.RIGHT_CLICK_AIR, null, EquipmentSlot.HAND)));
        assertFalse(denied(click(Action.LEFT_CLICK_BLOCK, null, EquipmentSlot.HAND)));
        assertFalse(denied(click(Action.PHYSICAL, mimic, null)));
        chests.verify(() -> MimicChests.wake(any(), any()), never());
    }

    @Test
    @DisplayName("staff in creative can hit a dormant mimic to break it; anyone else wakes it")
    void creativeStaffCanBreakIt() {
        when(player.getGameMode()).thenReturn(GameMode.CREATIVE);
        assertTrue(denied(click(Action.LEFT_CLICK_BLOCK, mimic, EquipmentSlot.HAND)), "creative non-staff");
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        assertFalse(denied(click(Action.LEFT_CLICK_BLOCK, mimic, EquipmentSlot.HAND)));
        // a right-click still wakes it, even for them
        assertTrue(denied(click(Action.RIGHT_CLICK_BLOCK, mimic, EquipmentSlot.HAND)));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        assertTrue(denied(click(Action.LEFT_CLICK_BLOCK, mimic, EquipmentSlot.HAND)), "staff in survival");
        chests.verify(() -> MimicChests.wake(mimic, player), org.mockito.Mockito.times(3));
    }

    @Test
    @DisplayName("only staff can break a dormant mimic, and it drops nothing")
    void breaking() {
        BlockBreakEvent ordinary = new BlockBreakEvent(chest, player);
        listener.onBreak(ordinary);
        assertFalse(ordinary.isCancelled());
        assertTrue(ordinary.isDropItems());

        BlockBreakEvent refused = new BlockBreakEvent(mimic, player);
        listener.onBreak(refused);
        assertTrue(refused.isCancelled());
        verify(player, never()).sendActionBar(any(Component.class));

        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        BlockBreakEvent removed = new BlockBreakEvent(mimic, player);
        listener.onBreak(removed);
        assertFalse(removed.isCancelled());
        assertFalse(removed.isDropItems());
        verify(player).sendActionBar(any(Component.class));
    }

    @Test
    @DisplayName("explosions blow up everything around a dormant mimic but the mimic")
    void explosionsSpareIt() {
        Block dirt = blocks.block(2, 1, 0);
        EntityExplodeEvent creeper = mock(EntityExplodeEvent.class);
        List<Block> creeperBlocks = new ArrayList<>(List.of(mimic, chest, dirt));
        when(creeper.blockList()).thenReturn(creeperBlocks);
        listener.onEntityExplode(creeper);
        assertEquals(List.of(chest, dirt), creeperBlocks);

        BlockExplodeEvent bed = mock(BlockExplodeEvent.class);
        List<Block> bedBlocks = new ArrayList<>(List.of(dirt, mimic));
        when(bed.blockList()).thenReturn(bedBlocks);
        listener.onBlockExplode(bed);
        assertEquals(List.of(dirt), bedBlocks);
    }

    private static Inventory holding(InventoryHolder holder) {
        Inventory inventory = mock(Inventory.class);
        when(inventory.getHolder(anyBoolean())).thenReturn(holder);
        return inventory;
    }

    @Test
    @DisplayName("its inventory can't be opened by any means")
    void cannotBeOpened() {
        Inventory dormant = holding(blocks.state(mimic));
        Inventory plain = holding(blocks.state(chest));
        InventoryOpenEvent open = mock(InventoryOpenEvent.class);
        when(open.getInventory()).thenReturn(dormant);
        listener.onOpen(open);
        verify(open).setCancelled(true);

        InventoryOpenEvent ordinary = mock(InventoryOpenEvent.class);
        when(ordinary.getInventory()).thenReturn(plain);
        listener.onOpen(ordinary);
        verify(ordinary, never()).setCancelled(anyBoolean());
    }

    @Test
    @DisplayName("hoppers can't feed it or drain it")
    void hoppersCannotReachIt() {
        Inventory hopper = holding(mock(InventoryHolder.class));
        Inventory dormant = holding(blocks.state(mimic));
        Inventory ordinary = holding(blocks.state(chest));

        InventoryMoveItemEvent into = mock(InventoryMoveItemEvent.class);
        when(into.getSource()).thenReturn(hopper);
        when(into.getDestination()).thenReturn(dormant);
        listener.onHopper(into);
        verify(into).setCancelled(true);

        InventoryMoveItemEvent outOf = mock(InventoryMoveItemEvent.class);
        when(outOf.getSource()).thenReturn(dormant);
        when(outOf.getDestination()).thenReturn(hopper);
        listener.onHopper(outOf);
        verify(outOf).setCancelled(true);

        InventoryMoveItemEvent elsewhere = mock(InventoryMoveItemEvent.class);
        when(elsewhere.getSource()).thenReturn(ordinary);
        when(elsewhere.getDestination()).thenReturn(hopper);
        listener.onHopper(elsewhere);
        verify(elsewhere, never()).setCancelled(anyBoolean());
    }
}
