package net.tfminecraft.spiders.utilities.custom_items

import net.tfminecraft.spiders.StaffAccess
import net.tfminecraft.spiders.utilities.events.addEventListener
import net.tfminecraft.spiders.utilities.events.onGestureUseItem
import net.tfminecraft.spiders.utilities.events.onTick
import net.tfminecraft.spiders.utilities.namespacedID
import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType

fun setupStaffTools() {
    // A tool is only ever a tool: no placing the Brood Guide's torch, dyeing signs or sheep with
    // a charm, steering pigs with the whistle, feeding animals or using it on any block or entity.
    addEventListener(object : Listener {
        @EventHandler(priority = EventPriority.LOWEST)
        fun onUse(event: PlayerInteractEvent) {
            if (!isStaffTool(event.item)) return
            event.setUseItemInHand(Event.Result.DENY)
            if (event.action == Action.RIGHT_CLICK_BLOCK) event.setUseInteractedBlock(Event.Result.DENY)
        }

        @EventHandler(priority = EventPriority.LOWEST)
        fun onPlace(event: BlockPlaceEvent) {
            if (isStaffTool(event.itemInHand)) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.LOWEST)
        fun onUseOnEntity(event: PlayerInteractEntityEvent) {
            if (isStaffTool(event.player.inventory.getItem(event.hand))) event.isCancelled = true
        }

        @EventHandler(priority = EventPriority.LOWEST)
        fun onConsume(event: PlayerItemConsumeEvent) {
            if (isStaffTool(event.item)) event.isCancelled = true
        }
    })
}

/** Every tool, so their vanilla uses can be blocked wherever they are held. */
private val toolComponents = mutableListOf<CustomItemComponent>()

/** True for any staff tool, matched exactly (tag and item type). */
fun isStaffTool(item: ItemStack?): Boolean = item != null && toolComponents.any { it.isAttached(item) }

/**
 * A staff tool. An item only counts as this tool if it carries the tool's tag *and* is still the
 * tool's [material]: a plain item of the same type, or a tagged item of another type, does nothing.
 */
class CustomItemComponent(val id: String, val material: Material) {
    private val key by lazy { namespacedID("item_component_$id") }

    init {
        toolComponents += this
    }

    fun isAttached(item: ItemStack): Boolean {
        if (item.type != material) return false
        return item.persistentDataContainer.get(key, PersistentDataType.BOOLEAN) == true
    }

    /** A fresh tool for the staff tools menu. */
    fun create(name: String): ItemStack = createNamedItem(material, name).attach(this)

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
        net.tfminecraft.spiders.utilities.events.onInteractEntity(fun(player, entity, hand) {
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
