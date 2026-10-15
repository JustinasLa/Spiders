package net.tfminecraft.spiders.utilities.custom_items;

import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.events.Events;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * A staff tool. An item only counts as this tool if it carries the tool's tag *and* is still the
 * tool's [material]: a plain item of the same type, or a tagged item of another type, does nothing.
 */
public class CustomItemComponent {
    @FunctionalInterface
    public interface InteractEntityAction {
        void accept(Player player, Entity entity, ItemStack item);
    }

    private final String id;
    private final Material material;
    private NamespacedKey key;

    public CustomItemComponent(String id, Material material) {
        this.id = id;
        this.material = material;
        CustomItems.addToolComponent(this);
    }

    public String getId() {
        return id;
    }

    public Material getMaterial() {
        return material;
    }

    private synchronized NamespacedKey getKey() {
        if (key == null) key = Core.namespacedID("item_component_" + id);
        return key;
    }

    public boolean isAttached(ItemStack item) {
        if (item.getType() != material) return false;
        return Boolean.TRUE.equals(item.getPersistentDataContainer().get(getKey(), PersistentDataType.BOOLEAN));
    }

    /** A fresh tool for the staff tools menu. */
    public ItemStack create(String name) {
        return CustomItems.attach(CustomItems.createNamedItem(material, name), this);
    }

    public void attach(ItemStack item) {
        item.editPersistentDataContainer(it -> it.set(getKey(), PersistentDataType.BOOLEAN, true));
    }

    public List<Player> getPlayersHoldingItem() {
        List<Player> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!StaffAccess.canUse(player)) continue;
            ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
            ItemStack itemInOffHand = player.getInventory().getItemInOffHand();
            if (isAttached(itemInMainHand) || isAttached(itemInOffHand)) out.add(player);
        }
        return out;
    }

    public void onGestureUse(BiConsumer<Player, ItemStack> action) {
        Events.onGestureUseItem((player, item) -> {
            if (isAttached(item) && StaffAccess.require(player)) action.accept(player, item);
        });
    }

    public void onInteractEntity(InteractEntityAction action) {
        Events.onInteractEntity((Player player, Entity entity, EquipmentSlot hand) -> {
            ItemStack item = player.getInventory().getItem(hand);
            if (isAttached(item) && StaffAccess.require(player)) action.accept(player, entity, item);
        });
    }

    public void onHeldTick(BiConsumer<Player, ItemStack> action) {
        Scheduler.onTick(it -> {
            for (Player player : Bukkit.getServer().getOnlinePlayers()) {
                if (!StaffAccess.canUse(player)) continue;
                ItemStack itemInMainHand = player.getInventory().getItemInMainHand();
                ItemStack itemInOffHand = player.getInventory().getItemInOffHand();
                if (isAttached(itemInMainHand)) action.accept(player, itemInMainHand);
                if (isAttached(itemInOffHand)) action.accept(player, itemInOffHand);
            }
        });
    }
}
