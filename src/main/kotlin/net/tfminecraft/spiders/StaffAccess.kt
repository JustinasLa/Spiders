package net.tfminecraft.spiders

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** Every command, borrowed tool, held-item effect and mount uses the same permission. */
object StaffAccess {
    const val PERMISSION = "tfmc.spider.staff"

    fun canUse(sender: CommandSender): Boolean = sender.hasPermission(PERMISSION)

    fun require(sender: CommandSender): Boolean {
        if (canUse(sender)) return true
        val message = Component.text("Only staff may operate Cinderweavers.", NamedTextColor.RED)
        if (sender is Player) sender.sendActionBar(message) else sender.sendMessage(message)
        return false
    }
}
