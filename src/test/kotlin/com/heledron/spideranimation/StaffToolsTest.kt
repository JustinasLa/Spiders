package com.heledron.spideranimation

import com.heledron.spideranimation.utilities.currentPlugin
import com.heledron.spideranimation.utilities.custom_items.CustomItemComponent
import com.heledron.spideranimation.utilities.custom_items.isStaffTool
import org.bukkit.Material
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.Server
import org.bukkit.block.BlockFace
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.PlayerInventory
import org.bukkit.persistence.PersistentDataContainer
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.PluginManager
import org.bukkit.plugin.java.JavaPlugin
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import kotlin.test.*

class StaffToolsTest {
    private val server = mock(Server::class.java)
    private val manager = mock(PluginManager::class.java)
    private val plugin = mock(JavaPlugin::class.java)

    @BeforeEach
    fun setup() {
        `when`(plugin.name).thenReturn("TFMCSpiders")
        `when`(plugin.namespace()).thenReturn("tfmcspiders")
        `when`(plugin.server).thenReturn(server)
        `when`(server.pluginManager).thenReturn(manager)
        currentPlugin = plugin
    }

    private fun tool(material: Material = Material.FERMENTED_SPIDER_EYE, tagged: Boolean = true): ItemStack {
        val tool = mock(ItemStack::class.java)
        `when`(tool.type).thenReturn(material)
        val pdc = mock(PersistentDataContainer::class.java)
        `when`(tool.persistentDataContainer).thenReturn(pdc)
        `when`(pdc.get(any(NamespacedKey::class.java), eq(PersistentDataType.BOOLEAN))).thenReturn(if (tagged) true else null)
        return tool
    }

    @Test
    fun `borrowed spawn tool is inert for nonstaff then works only while permission is present`() {
        val component = CustomItemComponent("test_spawn", Material.FERMENTED_SPIDER_EYE)
        var actions = 0
        component.onGestureUse { _, _ -> actions++ }
        val captor = ArgumentCaptor.forClass(Listener::class.java)
        verify(manager).registerEvents(captor.capture(), eq(plugin))
        val listener = captor.value
        val callback = listener.javaClass.getDeclaredMethod("onPlayerInteract", PlayerInteractEvent::class.java)
        callback.isAccessible = true
        val player = mock(Player::class.java)
        val event = PlayerInteractEvent(player, Action.RIGHT_CLICK_AIR, tool(), null, BlockFace.SELF, EquipmentSlot.HAND)

        callback.invoke(listener, event)
        assertEquals(0, actions)
        `when`(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true)
        callback.invoke(listener, event)
        assertEquals(1, actions)
        `when`(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(false)
        callback.invoke(listener, event)
        assertEquals(1, actions)
    }

    @Test
    fun `holding a borrowed steering beacon never puts a nonstaff player in the controller set`() {
        val component = CustomItemComponent("test_beacon", Material.REDSTONE_TORCH)
        val staff = mock(Player::class.java)
        val regular = mock(Player::class.java)
        val inventory = mock(PlayerInventory::class.java)
        `when`(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(true)
        `when`(staff.inventory).thenReturn(inventory)
        val heldTool = tool(Material.REDSTONE_TORCH)
        `when`(inventory.itemInMainHand).thenReturn(heldTool)
        `when`(inventory.itemInOffHand).thenReturn(heldTool)
        mockStatic(Bukkit::class.java).use { bukkit ->
            bukkit.`when`<Collection<Player>> { Bukkit.getOnlinePlayers() }.thenReturn(listOf(staff, regular))
            assertEquals(listOf(staff), component.getPlayersHoldingItem())
            verify(regular, never()).inventory
            `when`(staff.hasPermission(StaffAccess.PERMISSION)).thenReturn(false)
            assertTrue(component.getPlayersHoldingItem().isEmpty())
        }
    }

    @Test
    fun `only the exact tool counts, not a plain item of its type or its tag on another item`() {
        val component = CustomItemComponent("test_shroud", Material.GREEN_DYE)
        assertTrue(component.isAttached(tool(Material.GREEN_DYE)))
        assertFalse(component.isAttached(tool(Material.GREEN_DYE, tagged = false)), "a plain green dye")
        assertFalse(component.isAttached(tool(Material.LIME_DYE)), "the tag copied onto another item")
        assertFalse(isStaffTool(tool(Material.GREEN_DYE, tagged = false)))
        assertTrue(isStaffTool(tool(Material.GREEN_DYE)))
    }
}
