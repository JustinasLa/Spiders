package net.tfminecraft.spiders.utilities.custom_items;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.action.DialogActionCallback;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class StaffToolMenu {
    private StaffToolMenu() {
    }

    /** The tools, in menu order. */
    private static final List<StaffTool> staffTools = new ArrayList<>();

    public static List<StaffTool> getStaffTools() {
        return staffTools;
    }

    private static final int MENU_WIDTH = 330;
    private static final int COLUMNS = 3;

    /** Each menu lasts this long; staff reopen it with /spider tools. */
    private static final ClickCallback.Options CALLBACKS = ClickCallback.Options.builder()
        .uses(ClickCallback.UNLIMITED_USES)
        .lifetime(Duration.ofMinutes(30))
        .build();

    /** A menu screen, not a container: one row per tool saying what it does, and a button to take each. */
    public static void openStaffToolMenu(Player player) {
        if (!StaffAccess.require(player)) return;
        player.showDialog(staffToolMenu());
    }

    private static Dialog staffToolMenu() {
        List<DialogBody> body = new ArrayList<>();
        body.add(
            DialogBody.plainMessage(Component.text("Click a tool below to receive it. The menu stays open, so take as many as you need.",
                NamedTextColor.GRAY), MENU_WIDTH)
        );
        for (StaffTool tool : staffTools) {
            TextComponent description = Component.text()
                .append(Component.text(tool.getName(), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text(tool.getDoes(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text(tool.getUse(), NamedTextColor.GRAY))
                .build();
            body.add(DialogBody.item(tool.create())
                .description(DialogBody.plainMessage(description, MENU_WIDTH - 40))
                .showTooltip(true)
                .build());
        }

        List<ActionButton> buttons = new ArrayList<>();
        for (StaffTool tool : staffTools) {
            DialogActionCallback callback = (response, audience) -> {
                if (audience instanceof Player player) give(player, tool);
            };
            buttons.add(ActionButton.builder(Component.text().append(tool.icon()).append(Component.text(" " + shortName(tool.getName()))).build())
                .tooltip(Component.text(tool.getDoes() + "\n" + tool.getUse()))
                .width(MENU_WIDTH / COLUMNS - 4)
                .action(DialogAction.customClick(callback, CALLBACKS))
                .build());
        }
        ActionButton close = ActionButton.builder(Component.text("Done")).width(100).build();

        return Dialog.create(builder -> builder.empty()
            .base(DialogBase.builder(Component.text("Cinderweaver • Staff Tools"))
                .canCloseWithEscape(true)
                .pause(false)
                // stay open after each click
                .afterAction(DialogBase.DialogAfterAction.NONE)
                .body(body)
                .build())
            .type(DialogType.multiAction(buttons, close, COLUMNS)));
    }

    private static void give(Player player, StaffTool tool) {
        if (!StaffAccess.require(player)) return;
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(tool.create());
        for (ItemStack item : leftover.values()) player.getWorld().dropItem(player.getLocation(), item);
        Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.ENTITY_ITEM_PICKUP, .5f, 1.2f);
        player.sendActionBar(Component.text("Received " + tool.getName(), NamedTextColor.GRAY));
    }

    /** The part of a tool's name before any " — " explanation. */
    private static String shortName(String name) {
        int index = name.indexOf(" — ");
        return index == -1 ? name : name.substring(0, index);
    }

    static Component loreLine(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
