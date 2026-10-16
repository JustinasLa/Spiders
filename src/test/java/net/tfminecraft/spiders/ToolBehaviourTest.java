package net.tfminecraft.spiders;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.tfminecraft.spiders.commands.SpiderTargets;
import net.tfminecraft.spiders.spider.components.PointDetector;
import net.tfminecraft.spiders.spider.components.body.Leg;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.custom_items.CustomItemComponent;
import net.tfminecraft.spiders.utilities.custom_items.CustomItems;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.events.Events;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolBehaviourTest {
    private final Server server = mock(Server.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final World world = mock(World.class);

    @BeforeEach
    void setup() {
        when(plugin.getName()).thenReturn("TFMCSpiders");
        when(plugin.namespace()).thenReturn("tfmcspiders");
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getScheduler()).thenReturn(mock(BukkitScheduler.class));
        Core.setCurrentPlugin(plugin);
    }

    @AfterEach
    void cleanState() {
        AppState.getEcs().getEntities().clear();
    }

    private static ItemStack tool(Material material) {
        ItemStack tool = mock(ItemStack.class);
        when(tool.getType()).thenReturn(material);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(tool.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN))).thenReturn(true);
        return tool;
    }

    private Player staff() {
        Player player = mock(Player.class);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 0.5, 64.0, 0.5));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
        when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class));
        when(player.getInventory()).thenReturn(inventory);
        return player;
    }

    /** Every listener the plugin registered so far. */
    private List<Listener> listeners() {
        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(manager, atLeastOnce()).registerEvents(captor.capture(), eq(plugin));
        return captor.getAllValues();
    }

    /** Calls every registered handler with the given name and event type, as the server would. */
    private void fire(String handler, Class<?> type, Object event) throws Exception {
        for (Listener listener : listeners()) {
            for (Method method : listener.getClass().getDeclaredMethods()) {
                if (!method.getName().equals(handler) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != type) continue;
                method.setAccessible(true);
                method.invoke(listener, event);
            }
        }
    }

    @Test
    @DisplayName("the Limb Binding Charm binds the leg looked at, whichever spider it belongs to")
    void theLimbBindingCharmWorksOnAnySpider() throws Exception {
        SetupItems.setupItems();
        Player player = staff();
        Leg leg = mock(Leg.class);
        PointDetector first = new PointDetector();
        PointDetector second = new PointDetector();
        second.getSelectedLeg().put(player, leg);
        AppState.getEcs().spawn(first);
        AppState.getEcs().spawn(second);

        try {
            fire("onPlayerInteract", PlayerInteractEvent.class, new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, tool(Material.RABBIT_HIDE), null, BlockFace.SELF, EquipmentSlot.HAND));
        } catch (InvocationTargetException e) {
            // the confirmation sound needs the server's sound registry, which unit tests lack; the leg is bound first
            if (!(e.getCause() instanceof LinkageError)) throw e;
        }

        verify(leg).setDisabled(true);
    }

    @Test
    @DisplayName("a held tool still opens doors, but never works on signs and other blocks")
    void aHeldToolStillOpensDoors() throws Exception {
        CustomItems.setupStaffTools();
        new CustomItemComponent("test_door_tool", Material.GREEN_DYE);
        Player player = staff();

        Block door = mock(Block.class);
        when(door.getBlockData()).thenReturn(mock(Door.class));
        assertTrue(Events.opensWithoutItem(door));
        PlayerInteractEvent onDoor = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, tool(Material.GREEN_DYE), door, BlockFace.NORTH, EquipmentSlot.HAND);
        fire("onUse", PlayerInteractEvent.class, onDoor);
        assertEquals(Event.Result.DENY, onDoor.useItemInHand());
        assertNotEquals(Event.Result.DENY, onDoor.useInteractedBlock(), "the door did not open");

        Block sign = mock(Block.class);
        when(sign.getBlockData()).thenReturn(mock(BlockData.class));
        when(sign.getType()).thenReturn(Material.OAK_SIGN);
        assertFalse(Events.opensWithoutItem(sign));
        PlayerInteractEvent onSign = new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, tool(Material.GREEN_DYE), sign, BlockFace.NORTH, EquipmentSlot.HAND);
        fire("onUse", PlayerInteractEvent.class, onSign);
        assertEquals(Event.Result.DENY, onSign.useInteractedBlock(), "the dye would colour the sign");
    }

    @Test
    @DisplayName("a dropped tool picked up by a player without staff access disappears")
    void aDroppedToolPickedUpByNonStaffDisappears() throws Exception {
        CustomItems.setupStaffTools();
        new CustomItemComponent("test_pickup_tool", Material.BONE);
        Player regular = mock(Player.class);
        Item item = mock(Item.class);
        ItemStack stack = tool(Material.BONE);
        when(item.getItemStack()).thenReturn(stack);

        EntityPickupItemEvent byRegular = new EntityPickupItemEvent(regular, item, 0);
        fire("onPickup", EntityPickupItemEvent.class, byRegular);
        assertTrue(byRegular.isCancelled());
        verify(item).remove();

        Item kept = mock(Item.class);
        when(kept.getItemStack()).thenReturn(stack);
        EntityPickupItemEvent byStaff = new EntityPickupItemEvent(staff(), kept, 0);
        fire("onPickup", EntityPickupItemEvent.class, byStaff);
        assertFalse(byStaff.isCancelled());
        verify(kept, never()).remove();
    }

    @Test
    @DisplayName("console commands act on the spider nearest the command's location, in its world")
    void consoleCommandsActNearTheirLocation() {
        AppState.getEcs().spawn(SpiderBody.create(mock(World.class), new Vector(0.5, 64.0, 0.5)));
        AppState.getEcs().spawn(SpiderBody.create(world, new Vector(500.5, 64.0, 0.5)));
        ECSEntity near = AppState.getEcs().spawn(SpiderBody.create(world, new Vector(20.5, 64.0, 0.5)));
        CommandSourceStack source = mock(CommandSourceStack.class);
        when(source.getSender()).thenReturn(mock(ConsoleCommandSender.class));
        when(source.getLocation()).thenReturn(new Location(world, 0.0, 64.0, 0.0));

        assertEquals(near, SpiderTargets.forCommand(source));

        when(source.getLocation()).thenReturn(new Location(mock(World.class), 0.0, 64.0, 0.0));
        assertNull(SpiderTargets.forCommand(source));
    }

    @Test
    @DisplayName("tools reach only spiders within range")
    void toolsReachOnlySpidersWithinRange() {
        AppState.getEcs().spawn(SpiderBody.create(world, new Vector(SpiderTargets.TOOL_RANGE + 20.0, 64.0, 0.5)));
        Player player = staff();
        assertNull(SpiderTargets.forTool(player));
        ECSEntity near = AppState.getEcs().spawn(SpiderBody.create(world, new Vector(10.5, 64.0, 0.5)));
        assertEquals(near, SpiderTargets.forTool(player));
    }
}
