package net.tfminecraft.spiders.utilities.custom_items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.overloads.Overloads;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

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

    /** Lore lines wrap at about this many characters so tooltips stay narrow. */
    private static final int LORE_WIDTH = 36;

    /** Marks an open tools chest; the chest holds display copies, never real items. */
    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    /** A chest of every tool; clicking one gives the clicker a fresh copy. The menu stays open. */
    public static void openStaffToolMenu(Player player) {
        if (!StaffAccess.require(player)) return;
        MenuHolder holder = new MenuHolder();
        int size = Math.max(9, (staffTools.size() + 8) / 9 * 9);
        Inventory inventory = Bukkit.createInventory(holder, size, Component.text("Cinderweaver • Staff Tools"));
        holder.inventory = inventory;
        for (int i = 0; i < staffTools.size(); i++) inventory.setItem(i, staffTools.get(i).create());
        player.openInventory(inventory);
    }

    public static void setupStaffToolMenu() {
        Events.addEventListener(new Listener() {
            @EventHandler
            public void onClick(InventoryClickEvent event) {
                if (!(event.getInventory().getHolder(false) instanceof MenuHolder)) return;
                // nothing moves in or out of the menu, from either inventory
                event.setCancelled(true);
                if (!(event.getWhoClicked() instanceof Player player)) return;
                if (event.getClickedInventory() != event.getView().getTopInventory()) return;
                int slot = event.getSlot();
                if (slot < 0 || slot >= staffTools.size()) return;
                give(player, staffTools.get(slot));
            }

            @EventHandler
            public void onDrag(InventoryDragEvent event) {
                if (event.getInventory().getHolder(false) instanceof MenuHolder) event.setCancelled(true);
            }
        });
    }

    private static void give(Player player, StaffTool tool) {
        if (!StaffAccess.require(player)) {
            player.closeInventory();
            return;
        }
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(tool.create());
        for (ItemStack item : leftover.values()) player.getWorld().dropItem(player.getLocation(), item);
        Overloads.playSound(player.getWorld(), Overloads.getPosition(player), Sound.ENTITY_ITEM_PICKUP, .5f, 1.2f);
        player.sendActionBar(Component.text("Received " + tool.getName(), NamedTextColor.GRAY));
    }

    /** The text as lore lines of at most {@link #LORE_WIDTH} characters, broken between words. */
    static List<Component> loreLines(String text, NamedTextColor color) {
        List<Component> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            if (!line.isEmpty() && line.length() + 1 + word.length() > LORE_WIDTH) {
                lines.add(loreLine(line.toString(), color));
                line.setLength(0);
            }
            if (!line.isEmpty()) line.append(' ');
            line.append(word);
        }
        if (!line.isEmpty()) lines.add(loreLine(line.toString(), color));
        return lines;
    }

    private static Component loreLine(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
