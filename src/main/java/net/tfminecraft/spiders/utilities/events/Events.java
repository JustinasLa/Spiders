package net.tfminecraft.spiders.utilities.events;

import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Disposable;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntitySpawnEvent;
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

    public static Disposable onSpawnEntity(Consumer<Entity> listener) {
        return addEventListener(new Listener() {
            @EventHandler
            public void onSpawn(EntitySpawnEvent event) {
                listener.accept(event.getEntity());
            }
        });
    }

    public static Disposable onGestureUseItem(BiConsumer<Player, ItemStack> listener) {
        return addEventListener(new Listener() {
            @EventHandler
            public void onPlayerInteract(PlayerInteractEvent event) {
                if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !(isNotInteractable(event.getClickedBlock()) || event.getPlayer().isSneaking())) return;
                Player player = event.getPlayer();
                ItemStack item = event.getItem();
                if (item == null) return;
                listener.accept(player, item);
            }
        });
    }

    private static boolean isNotInteractable(Block block) {
        return block != null && !block.getType().isInteractable();
    }
}
