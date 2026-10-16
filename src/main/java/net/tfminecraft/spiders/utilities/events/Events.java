package net.tfminecraft.spiders.utilities.events;

import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Disposable;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class Events {
    private Events() {
    }

    @FunctionalInterface
    public interface InteractEntityListener {
        void accept(Player player, Entity entity, EquipmentSlot hand);
    }

    public static Disposable addEventListener(Listener listener) {
        JavaPlugin plugin = Core.getCurrentPlugin();
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        return () -> HandlerList.unregisterAll(listener);
    }

    public static Disposable onInteractEntity(InteractEntityListener listener) {
        return addEventListener(new Listener() {
            @EventHandler
            public void onInteract(PlayerInteractEntityEvent event) {
                listener.accept(event.getPlayer(), event.getRightClicked(), event.getHand());
            }
        });
    }


    public static Disposable onInteractEntity(Consumer<PlayerInteractEntityEvent> listener) {
        return addEventListener(new Listener() {
            @EventHandler
            public void onInteract(PlayerInteractEntityEvent event) {
                listener.accept(event);
            }
        });
    }

    public static Disposable onGestureUseItem(BiConsumer<Player, ItemStack> listener) {
        return addEventListener(new Listener() {
            @EventHandler
            public void onPlayerInteract(PlayerInteractEvent event) {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
                // a door, lever or chest clicked without sneaking opens instead
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && opensWithoutItem(event.getClickedBlock()) && !event.getPlayer().isSneaking()) return;
                Player player = event.getPlayer();
                ItemStack item = event.getItem();
                if (item == null) return;
                listener.accept(player, item);
            }
        });
    }

    /**
     * Doors, trapdoors, gates, levers, buttons and containers: right-clicking them opens or toggles
     * them without using the item in hand. Other blocks (signs, pots, ...) would take the item.
     */
    public static boolean opensWithoutItem(Block block) {
        if (block == null) return false;
        BlockData data = block.getBlockData();
        if (data instanceof Openable || data instanceof Switch) return true;
        if (block.getType() == Material.ENDER_CHEST) return true;
        return block.getState(false) instanceof Container;
    }
}
