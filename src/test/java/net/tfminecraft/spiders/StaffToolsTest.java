package net.tfminecraft.spiders;

import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.custom_items.CustomItemComponent;
import net.tfminecraft.spiders.utilities.custom_items.CustomItems;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolsReset;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class StaffToolsTest {
    private final Server server = mock(Server.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final JavaPlugin plugin = mock(JavaPlugin.class);
    private final Runnable restoreTools = StaffToolsReset.isolate();

    @BeforeEach
    void setup() {
        when(plugin.getName()).thenReturn("TFMCSpiders");
        when(plugin.namespace()).thenReturn("tfmcspiders");
        when(plugin.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(manager);
        Core.setCurrentPlugin(plugin);
    }

    @AfterEach
    void forgetTools() {
        restoreTools.run();
    }

    private ItemStack tool(Material material, boolean tagged) {
        ItemStack tool = mock(ItemStack.class);
        when(tool.getType()).thenReturn(material);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(tool.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN))).thenReturn(tagged ? true : null);
        return tool;
    }

    private ItemStack tool(Material material) {
        return tool(material, true);
    }

    private ItemStack tool() {
        return tool(Material.FERMENTED_SPIDER_EYE);
    }

    @Test
    @DisplayName("borrowed spawn tool is inert for nonstaff then works only while permission is present")
    void borrowedSpawnToolWorksOnlyWhilePermissionIsPresent() throws Exception {
        CustomItemComponent component = new CustomItemComponent("test_spawn", Material.FERMENTED_SPIDER_EYE);
        int[] actions = {0};
        component.onGestureUse((player, item) -> actions[0]++);
        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(manager).registerEvents(captor.capture(), eq(plugin));
        Listener listener = captor.getValue();
        Method callback = listener.getClass().getDeclaredMethod("onPlayerInteract", PlayerInteractEvent.class);
        callback.setAccessible(true);
        Player player = mock(Player.class);
        PlayerInteractEvent event = new PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, tool(), null, BlockFace.SELF, EquipmentSlot.HAND);

        callback.invoke(listener, event);
        assertEquals(0, actions[0]);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        callback.invoke(listener, event);
        assertEquals(1, actions[0]);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
        callback.invoke(listener, event);
        assertEquals(1, actions[0]);
    }

    @Test
    @DisplayName("holding a borrowed steering beacon never puts a nonstaff player in the controller set")
    void borrowedSteeringBeaconNeverAddsNonstaffController() {
        CustomItemComponent component = new CustomItemComponent("test_beacon", Material.REDSTONE_TORCH);
        Player staff = mock(Player.class);
        Player regular = mock(Player.class);
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        when(staff.getInventory()).thenReturn(inventory);
        ItemStack heldTool = tool(Material.REDSTONE_TORCH);
        when(inventory.getItemInMainHand()).thenReturn(heldTool);
        when(inventory.getItemInOffHand()).thenReturn(heldTool);
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Collection<Player> online = List.of(staff, regular);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(online);
            assertEquals(List.of(staff), component.getPlayersHoldingItem());
            verify(regular, never()).getInventory();
            when(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(false);
            assertTrue(component.getPlayersHoldingItem().isEmpty());
        }
    }

    @Test
    @DisplayName("only the exact tool counts, not a plain item of its type or its tag on another item")
    void onlyTheExactToolCounts() {
        CustomItemComponent component = new CustomItemComponent("test_shroud", Material.GREEN_DYE);
        assertTrue(component.isAttached(tool(Material.GREEN_DYE)));
        assertFalse(component.isAttached(tool(Material.GREEN_DYE, false)), "a plain green dye");
        assertFalse(component.isAttached(tool(Material.LIME_DYE)), "the tag copied onto another item");
        assertFalse(CustomItems.isStaffTool(tool(Material.GREEN_DYE, false)));
        assertTrue(CustomItems.isStaffTool(tool(Material.GREEN_DYE)));
    }
}
