package net.tfminecraft.spiders.utilities.custom_items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.events.Events;
import org.bukkit.Material;
import org.bukkit.block.Crafter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CustomItems {
    private CustomItems() {
    }

    public static void setupStaffTools() {
        // A tool is only ever a tool: no placing the Brood Guide's torch, dyeing signs or sheep with
        // a charm, steering pigs with the whistle, feeding animals or using it on any block or entity.
        // Doors, levers and chests still open while one is held (sneak to use the tool on them).
        Events.addEventListener(new Listener() {
            @EventHandler(priority = EventPriority.LOWEST)
            public void onUse(PlayerInteractEvent event) {
                if (!isStaffTool(event.getItem())) return;
                event.setUseItemInHand(Event.Result.DENY);
                if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
                boolean opens = Events.opensWithoutItem(event.getClickedBlock()) && !event.getPlayer().isSneaking();
                if (!opens) event.setUseInteractedBlock(Event.Result.DENY);
            }

            // tools never reach players without staff access, mobs or hoppers
            @EventHandler(priority = EventPriority.LOWEST)
            public void onPickup(EntityPickupItemEvent event) {
                if (!isStaffTool(event.getItem().getItemStack())) return;
                if (event.getEntity() instanceof Player player && StaffAccess.canUse(player)) return;
                event.setCancelled(true);
                if (event.getEntity() instanceof Player) event.getItem().remove();
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onHopperPickup(InventoryPickupItemEvent event) {
                if (!isStaffTool(event.getItem().getItemStack())) return;
                event.setCancelled(true);
                event.getItem().remove();
            }

            // nor are they crafted or brewed into vanilla items
            @EventHandler(priority = EventPriority.LOWEST)
            public void onPrepareCraft(PrepareItemCraftEvent event) {
                if (containsTool(event.getInventory().getMatrix())) event.getInventory().setResult(null);
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onCrafterCraft(CrafterCraftEvent event) {
                if (event.getBlock().getState(false) instanceof Crafter crafter && containsTool(crafter.getInventory().getContents())) event.setCancelled(true);
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onBrew(BrewEvent event) {
                if (isStaffTool(event.getContents().getIngredient())) event.setCancelled(true);
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onPlace(BlockPlaceEvent event) {
                if (isStaffTool(event.getItemInHand())) event.setCancelled(true);
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onUseOnEntity(PlayerInteractEntityEvent event) {
                if (isStaffTool(event.getPlayer().getInventory().getItem(event.getHand()))) event.setCancelled(true);
            }

            @EventHandler(priority = EventPriority.LOWEST)
            public void onConsume(PlayerItemConsumeEvent event) {
                if (isStaffTool(event.getItem())) event.setCancelled(true);
            }
        });
    }

    /** Every tool, so their vanilla uses can be blocked wherever they are held. */
    private static final List<CustomItemComponent> toolComponents = new ArrayList<>();

    static void addToolComponent(CustomItemComponent component) {
        toolComponents.add(component);
    }

    private static boolean containsTool(ItemStack[] items) {
        for (ItemStack item : items) if (isStaffTool(item)) return true;
        return false;
    }

    /** True for any staff tool, matched exactly (tag and item type). */
    public static boolean isStaffTool(ItemStack item) {
        if (item == null) return false;
        for (CustomItemComponent component : toolComponents) {
            if (component.isAttached(item)) return true;
        }
        return false;
    }

    public static ItemStack createNamedItem(Material material, String name) {
        ItemStack item = new ItemStack(material);
        item.setData(DataComponentTypes.ITEM_NAME, Component.text(name));
        return item;
    }

    public static ItemStack attach(ItemStack item, CustomItemComponent component) {
        component.attach(item);
        return item;
    }
}
