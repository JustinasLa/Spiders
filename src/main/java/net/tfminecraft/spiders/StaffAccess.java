package net.tfminecraft.spiders;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Every command, borrowed tool, held-item effect and mount uses the same permission. */
public final class StaffAccess {
    private StaffAccess() {}

    public static final String PERMISSION = "tfmc.spider.staff";

    public static boolean canUse(CommandSender sender) {
        return sender.hasPermission(PERMISSION);
    }

    public static boolean require(CommandSender sender) {
        if (canUse(sender)) return true;
        Component message = Component.text("Only staff may operate Cinderweavers.", NamedTextColor.RED);
        if (sender instanceof Player player) player.sendActionBar(message);
        else sender.sendMessage(message);
        return false;
    }
}
