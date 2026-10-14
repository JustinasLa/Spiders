package com.heledron.spideranimation.utilities.custom_items

import com.heledron.spideranimation.StaffAccess
import com.heledron.spideranimation.utilities.events.addEventListener
import com.heledron.spideranimation.utilities.events.onGestureUseItem
import com.heledron.spideranimation.utilities.events.onTick
import com.heledron.spideranimation.utilities.namespacedID
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Bukkit.createInventory
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.event.inventory.InventoryType
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

val customItemRegistry = mutableListOf<() -> ItemStack>()

private class StaffToolInventory : InventoryHolder {
    private val contents = createInventory(this, InventoryType.CHEST, Component.text("Cinderweaver • Staff Tools"))
    override fun getInventory(): Inventory = contents
}

fun openCustomItemInventory(player: Player) {
    if (!StaffAccess.require(player)) return
    val inventory = StaffToolInventory().inventory
    customItemRegistry.forEach { inventory.addItem(it()) }
    player.openInventory(inventory)
}

fun setupStaffToolInventory() {
    addEventListener(object : Listener {
        @EventHandler
        fun onClick(event: InventoryClickEvent) {
            if (event.view.topInventory.holder !is StaffToolInventory) return
            if (StaffAccess.canUse(event.whoClicked)) return
            event.isCancelled = true
        }

        @EventHandler
        fun onDrag(event: InventoryDragEvent) {
            if (event.view.topInventory.holder is StaffToolInventory && !StaffAccess.canUse(event.whoClicked)) {
                event.isCancelled = true
            }
        }
    })
}

class CustomItemComponent(val id: String) {
    private val key by lazy { namespacedID("item_component_$id") }

    fun isAttached(item: ItemStack): Boolean {
        return item.persistentDataContainer.get(key, PersistentDataType.BOOLEAN) == true
    }

    fun attach(item: ItemStack) {
        item.editPersistentDataContainer { it.set(key, PersistentDataType.BOOLEAN, true) }
    }

    fun getPlayersHoldingItem() = Bukkit.getOnlinePlayers().filter { player ->
        if (!StaffAccess.canUse(player)) return@filter false
        val itemInMainHand = player.inventory.itemInMainHand
        val itemInOffHand = player.inventory.itemInOffHand
        isAttached(itemInMainHand) || isAttached(itemInOffHand)
    }

    fun onGestureUse(action: (Player, ItemStack) -> Unit) {
        onGestureUseItem { player, item ->
            if (isAttached(item) && StaffAccess.require(player)) action(player, item)
        }
    }

    fun onInteractEntity(action: (Player, Entity, ItemStack) -> Unit) {
        com.heledron.spideranimation.utilities.events.onInteractEntity(fun(player, entity, hand) {
            val item = player.inventory.getItem(hand)
            if (isAttached(item) && StaffAccess.require(player)) action(player, entity, item)
        })
    }

    fun onHeldTick(action: (Player, ItemStack) -> Unit) {
        onTick {
            for (player in Bukkit.getServer().onlinePlayers) {
                if (!StaffAccess.canUse(player)) continue
                val itemInMainHand = player.inventory.itemInMainHand
                val itemInOffHand = player.inventory.itemInOffHand
                if (isAttached(itemInMainHand)) action(player, itemInMainHand)
                if (isAttached(itemInOffHand)) action(player, itemInOffHand)
            }
        }
    }
}

fun createNamedItem(material: Material, name: String): ItemStack {
    val item = ItemStack(material)
    item.setData(DataComponentTypes.ITEM_NAME, Component.text(name))
    return item
}

fun ItemStack.attach(component: CustomItemComponent): ItemStack {
    component.attach(this)
    return this
}
