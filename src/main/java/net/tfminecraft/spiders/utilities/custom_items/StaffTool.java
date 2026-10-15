package net.tfminecraft.spiders.utilities.custom_items;

import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.object.ObjectContents;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * A tool in the staff menu.
 *
 * @param sprite the item's texture in the block atlas, shown on its menu button.
 * @param does what the tool does, one sentence.
 * @param use how to use it.
 */
public class StaffTool {
    private final CustomItemComponent component;
    private final String name;
    private final String sprite;
    private final String does;
    private final String use;

    public StaffTool(
        CustomItemComponent component,
        String name,
        String sprite,
        String does,
        String use
    ) {
        this.component = component;
        this.name = name;
        this.sprite = sprite;
        this.does = does;
        this.use = use;
    }

    public CustomItemComponent getComponent() {
        return component;
    }

    public String getName() {
        return name;
    }

    public String getSprite() {
        return sprite;
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
        item.lore(List.of(StaffToolMenu.loreLine(does, NamedTextColor.GRAY), StaffToolMenu.loreLine(use, NamedTextColor.DARK_AQUA)));
        return item;
    }

    public Component icon() {
        return Component.object(ObjectContents.sprite(Key.key(sprite)));
    }
}
