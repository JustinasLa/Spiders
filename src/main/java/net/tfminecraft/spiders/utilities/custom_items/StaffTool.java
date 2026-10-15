package net.tfminecraft.spiders.utilities.custom_items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * A tool in the staff menu.
 *
 * @param does what the tool does, one sentence.
 * @param use how to use it.
 */
public class StaffTool {
    private final CustomItemComponent component;
    private final String name;
    private final String does;
    private final String use;

    public StaffTool(
        CustomItemComponent component,
        String name,
        String does,
        String use
    ) {
        this.component = component;
        this.name = name;
        this.does = does;
        this.use = use;
    }

    public CustomItemComponent getComponent() {
        return component;
    }

    public String getName() {
        return name;
    }

    public String getDoes() {
        return does;
    }

    public String getUse() {
        return use;
    }

    /** A fresh tool, with what it does and how to use it in its lore. */
    public ItemStack create() {
        ItemStack item = component.create(name);
        List<Component> lore = new ArrayList<>(StaffToolMenu.loreLines(does, NamedTextColor.GRAY));
        lore.addAll(StaffToolMenu.loreLines(use, NamedTextColor.DARK_AQUA));
        item.lore(lore);
        return item;
    }
}
