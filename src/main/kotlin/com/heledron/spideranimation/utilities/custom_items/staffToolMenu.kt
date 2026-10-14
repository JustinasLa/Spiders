package com.heledron.spideranimation.utilities.custom_items

import com.heledron.spideranimation.StaffAccess
import com.heledron.spideranimation.utilities.overloads.playSound
import com.heledron.spideranimation.utilities.overloads.position
import io.papermc.paper.dialog.Dialog
import io.papermc.paper.registry.data.dialog.ActionButton
import io.papermc.paper.registry.data.dialog.DialogBase
import io.papermc.paper.registry.data.dialog.action.DialogAction
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback
import io.papermc.paper.registry.data.dialog.body.DialogBody
import io.papermc.paper.registry.data.dialog.type.DialogType
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickCallback
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import net.kyori.adventure.text.`object`.ObjectContents
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import java.time.Duration

/**
 * A tool in the staff menu.
 *
 * @param sprite the item's texture in the block atlas, shown on its menu button.
 * @param does what the tool does, one sentence.
 * @param use how to use it.
 */
class StaffTool(
    val component: CustomItemComponent,
    val name: String,
    val sprite: String,
    val does: String,
    val use: String,
) {
    /** A fresh tool, with what it does and how to use it in its lore. */
    fun create(): ItemStack = component.create(name).apply {
        lore(listOf(loreLine(does, NamedTextColor.GRAY), loreLine(use, NamedTextColor.DARK_AQUA)))
    }

    fun icon(): Component = Component.`object`(ObjectContents.sprite(Key.key(sprite)))
}

/** The tools, in menu order. */
val staffTools = mutableListOf<StaffTool>()

private val MENU_WIDTH = 330
private val COLUMNS = 3

/** Each menu lasts this long; staff reopen it with /spider tools. */
private val CALLBACKS: ClickCallback.Options = ClickCallback.Options.builder()
    .uses(ClickCallback.UNLIMITED_USES)
    .lifetime(Duration.ofMinutes(30))
    .build()

/** A menu screen, not a container: one row per tool saying what it does, and a button to take each. */
fun openStaffToolMenu(player: Player) {
    if (!StaffAccess.require(player)) return
    player.showDialog(staffToolMenu())
}

private fun staffToolMenu(): Dialog {
    val body = mutableListOf<DialogBody>(
        DialogBody.plainMessage(Component.text("Click a tool below to receive it. The menu stays open, so take as many as you need.",
            NamedTextColor.GRAY), MENU_WIDTH),
    )
    for (tool in staffTools) {
        val description = Component.text()
            .append(Component.text(tool.name, NamedTextColor.GOLD))
            .append(Component.newline())
            .append(Component.text(tool.does, NamedTextColor.WHITE))
            .append(Component.newline())
            .append(Component.text(tool.use, NamedTextColor.GRAY))
            .build()
        body += DialogBody.item(tool.create())
            .description(DialogBody.plainMessage(description, MENU_WIDTH - 40))
            .showTooltip(true)
            .build()
    }

    val buttons = staffTools.map { tool ->
        ActionButton.builder(Component.text().append(tool.icon()).append(Component.text(" " + shortName(tool.name))).build())
            .tooltip(Component.text("${tool.does}\n${tool.use}"))
            .width(MENU_WIDTH / COLUMNS - 4)
            .action(DialogAction.customClick(DialogActionCallback { _, audience -> (audience as? Player)?.let { give(it, tool) } }, CALLBACKS))
            .build()
    }
    val close = ActionButton.builder(Component.text("Done")).width(100).build()

    return Dialog.create { builder ->
        builder.empty()
            .base(DialogBase.builder(Component.text("Cinderweaver • Staff Tools"))
                .canCloseWithEscape(true)
                .pause(false)
                // stay open after each click
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build())
            .type(DialogType.multiAction(buttons, close, COLUMNS))
    }
}

private fun give(player: Player, tool: StaffTool) {
    if (!StaffAccess.require(player)) return
    val leftover = player.inventory.addItem(tool.create())
    for (item in leftover.values) player.world.dropItem(player.location, item)
    player.world.playSound(player.position, Sound.ENTITY_ITEM_PICKUP, .5f, 1.2f)
    player.sendActionBar(Component.text("Received ${tool.name}", NamedTextColor.GRAY))
}

/** The part of a tool's name before any " — " explanation. */
private fun shortName(name: String) = name.substringBefore(" — ")

private fun loreLine(text: String, color: NamedTextColor) =
    Component.text(text, color).decoration(TextDecoration.ITALIC, false)
