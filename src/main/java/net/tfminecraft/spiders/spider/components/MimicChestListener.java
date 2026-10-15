package net.tfminecraft.spiders.spider.components;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.tfminecraft.spiders.StaffAccess;
import org.bukkit.GameMode;
import org.bukkit.block.Block;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

final class MimicChestListener implements Listener {
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        if (!MimicChests.isMimic(block)) return;
        // staff in creative mode can break a dormant mimic to remove it
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && event.getPlayer().getGameMode() == GameMode.CREATIVE && StaffAccess.canUse(event.getPlayer())) return;

        event.setUseInteractedBlock(Event.Result.DENY);
        event.setUseItemInHand(Event.Result.DENY);
        if (event.getHand() != EquipmentSlot.HAND) return;
        MimicChests.wake(block, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBreak(BlockBreakEvent event) {
        if (!MimicChests.isMimic(event.getBlock())) return;
        if (!StaffAccess.canUse(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }
        event.setDropItems(false);
        event.getPlayer().sendActionBar(Component.text("Dormant mimic removed.", NamedTextColor.GRAY));
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(MimicChests::isMimic);
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(MimicChests::isMimic);
    }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) {
        if (MimicChests.isMimic(event.getInventory().getHolder(false))) event.setCancelled(true);
    }

    @EventHandler
    public void onHopper(InventoryMoveItemEvent event) {
        if (MimicChests.isMimic(event.getDestination().getHolder(false)) || MimicChests.isMimic(event.getSource().getHolder(false))) {
            event.setCancelled(true);
        }
    }
}
