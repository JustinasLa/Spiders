package net.tfminecraft.spiders.utilities.custom_items;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.UtilitiesTestSupport;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StaffToolMenuTest {
    private static final Component TITLE = Component.text("Cinderweaver • Staff Tools");

    private UtilitiesTestSupport support;
    private MockedConstruction<ItemStack> items;
    private MockedStatic<Bukkit> bukkit;
    private final World world = mock(World.class);
    private final Inventory menu = mock(Inventory.class);
    private final ArgumentCaptor<InventoryHolder> holder = ArgumentCaptor.forClass(InventoryHolder.class);

    @BeforeEach
    void setup() {
        support = UtilitiesTestSupport.install();
        StaffToolsReset.reset();
        items = mockConstruction(ItemStack.class);
        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createInventory(holder.capture(), anyInt(), any(Component.class))).thenReturn(menu);
    }

    @AfterEach
    void close() {
        items.close();
        bukkit.close();
        StaffToolsReset.reset();
    }

    private static StaffTool tool(String id, String name) {
        return new StaffTool(new CustomItemComponent(id, Material.BONE), name, "Does a thing.", "Right-click.");
    }

    private Player player(boolean staff) {
        Player player = mock(Player.class);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(staff);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new Location(world, 1, 64, 2));
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return player;
    }

    private static Component lore(String text, NamedTextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }

    @Test
    @DisplayName("lore wraps between words at 36 characters")
    void loreWrapsBetweenWords() {
        assertEquals(List.of(
            lore("Binds the leg you look at so it can", NamedTextColor.GRAY),
            lore("no longer move or step", NamedTextColor.GRAY)
        ), StaffToolMenu.loreLines("Binds the leg you look at so it can no longer move or step", NamedTextColor.GRAY));
        assertEquals(List.of(), StaffToolMenu.loreLines("", NamedTextColor.GRAY));
    }

    @Test
    @DisplayName("a tool item is named, tagged and explains itself in its lore")
    void toolItemsExplainThemselves() {
        StaffTool tool = tool("sight", "Broodkeeper's Sight");
        assertEquals("Broodkeeper's Sight", tool.getName());
        assertEquals("Does a thing.", tool.getDoes());
        assertEquals("Right-click.", tool.getUse());
        assertEquals("sight", tool.getComponent().getId());

        ItemStack item = tool.create();
        verify(item).lore(List.of(lore("Does a thing.", NamedTextColor.GRAY), lore("Right-click.", NamedTextColor.DARK_AQUA)));
        verify(item).editPersistentDataContainer(any());
    }

    @Test
    @DisplayName("the menu holds one display copy of each tool, in rows of nine")
    void theMenuHoldsEveryTool() {
        Player staff = player(true);
        StaffToolMenu.openStaffToolMenu(staff);
        bukkit.verify(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(9), eq(TITLE)));
        assertSame(menu, holder.getValue().getInventory());
        verify(staff).openInventory(menu);

        for (int i = 0; i < 10; i++) StaffToolMenu.getStaffTools().add(tool("tool" + i, "Tool " + i));
        StaffToolMenu.openStaffToolMenu(staff);
        bukkit.verify(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(18), eq(TITLE)));
        verify(menu).setItem(eq(0), any(ItemStack.class));
        verify(menu).setItem(eq(9), any(ItemStack.class));
        verify(menu, never()).setItem(eq(10), any(ItemStack.class));
    }

    @Test
    @DisplayName("players without staff access can't open the menu")
    void nonStaffCannotOpenTheMenu() {
        Player regular = player(false);
        StaffToolMenu.openStaffToolMenu(regular);
        verify(regular).sendActionBar(any(Component.class));
        verify(regular, never()).openInventory(any(Inventory.class));
        bukkit.verify(() -> Bukkit.createInventory(any(InventoryHolder.class), anyInt(), any(Component.class)), never());
    }

    /** Opens the menu with two tools and returns its holder. */
    private InventoryHolder openMenu() {
        StaffToolMenu.getStaffTools().add(tool("first", "First"));
        StaffToolMenu.getStaffTools().add(tool("seal", "Seal"));
        StaffToolMenu.openStaffToolMenu(player(true));
        when(menu.getHolder(false)).thenReturn(holder.getValue());
        StaffToolMenu.setupStaffToolMenu();
        return holder.getValue();
    }

    private InventoryClickEvent click(HumanEntity who, Inventory clicked, int slot) {
        InventoryClickEvent event = mock(InventoryClickEvent.class);
        InventoryView view = mock(InventoryView.class);
        when(view.getTopInventory()).thenReturn(menu);
        when(event.getView()).thenReturn(view);
        when(event.getInventory()).thenReturn(menu);
        when(event.getWhoClicked()).thenReturn(who);
        when(event.getClickedInventory()).thenReturn(clicked);
        when(event.getSlot()).thenReturn(slot);
        support.fire("onClick", InventoryClickEvent.class, event);
        return event;
    }

    @Test
    @DisplayName("clicking a tool gives a fresh copy in the first free hotbar slot")
    void clickingGivesTheTool() {
        openMenu();
        Player staff = player(true);
        PlayerInventory inventory = staff.getInventory();
        ItemStack held = mock(ItemStack.class);
        when(inventory.getItem(0)).thenReturn(held);

        InventoryClickEvent event = click(staff, menu, 1);

        verify(event).setCancelled(true);
        verify(inventory).setItem(eq(1), any(ItemStack.class));
        verify(inventory, never()).setItem(eq(0), any(ItemStack.class));
        verify(world).playSound(new Location(world, 1, 64, 2), Sound.ENTITY_ITEM_PICKUP, 0.5f, 1.2f);
        verify(staff).sendActionBar(Component.text("Received Seal", NamedTextColor.GRAY));
    }

    @Test
    @DisplayName("an empty stack counts as a free slot")
    void anEmptyStackIsAFreeSlot() {
        openMenu();
        Player staff = player(true);
        ItemStack air = mock(ItemStack.class);
        when(air.isEmpty()).thenReturn(true);
        when(staff.getInventory().getItem(0)).thenReturn(air);
        click(staff, menu, 0);
        verify(staff.getInventory()).setItem(eq(0), any(ItemStack.class));
    }

    @Test
    @DisplayName("with a full hotbar the tool goes into the inventory, or at the player's feet")
    void aFullHotbarFallsBackToTheInventory() {
        openMenu();
        Player staff = player(true);
        PlayerInventory inventory = staff.getInventory();
        ItemStack held = mock(ItemStack.class);
        for (int slot = 0; slot < 9; slot++) when(inventory.getItem(slot)).thenReturn(held);
        ItemStack leftover = mock(ItemStack.class);
        HashMap<Integer, ItemStack> notStored = new HashMap<>(Map.of(0, leftover));
        when(inventory.addItem(any(ItemStack.class))).thenReturn(notStored);

        click(staff, menu, 0);

        verify(inventory).addItem(any(ItemStack.class));
        verify(world).dropItem(new Location(world, 1, 64, 2), leftover);
    }

    @Test
    @DisplayName("a player who lost staff access gets nothing and the menu closes")
    void lostAccessClosesTheMenu() {
        openMenu();
        Player regular = player(false);
        click(regular, menu, 0);
        verify(regular).closeInventory();
        verify(regular.getInventory(), never()).setItem(anyInt(), any(ItemStack.class));
    }

    @Test
    @DisplayName("clicks outside the tool slots, in the player's inventory or by non-players move nothing and give nothing")
    void otherClicksAreCancelledOnly() {
        openMenu();
        Player staff = player(true);
        HumanEntity notAPlayer = mock(HumanEntity.class);

        assertCancelledOnly(click(notAPlayer, menu, 0), staff);
        assertCancelledOnly(click(staff, mock(Inventory.class), 0), staff);
        assertCancelledOnly(click(staff, menu, -1), staff);
        assertCancelledOnly(click(staff, menu, 2), staff);
    }

    private void assertCancelledOnly(InventoryClickEvent event, Player staff) {
        verify(event).setCancelled(true);
        verify(staff.getInventory(), never()).setItem(anyInt(), any(ItemStack.class));
        verify(staff, never()).sendActionBar(any(Component.class));
    }

    @Test
    @DisplayName("other inventories are left alone, and nothing is dragged into the menu")
    void otherInventoriesAndDrags() {
        InventoryHolder menuHolder = openMenu();
        Inventory chest = mock(Inventory.class);
        when(chest.getHolder(false)).thenReturn(mock(InventoryHolder.class));
        InventoryClickEvent elsewhere = mock(InventoryClickEvent.class);
        when(elsewhere.getInventory()).thenReturn(chest);
        support.fire("onClick", InventoryClickEvent.class, elsewhere);
        verify(elsewhere, never()).setCancelled(true);

        InventoryDragEvent dragInMenu = mock(InventoryDragEvent.class);
        when(dragInMenu.getInventory()).thenReturn(menu);
        support.fire("onDrag", InventoryDragEvent.class, dragInMenu);
        verify(dragInMenu).setCancelled(true);
        InventoryDragEvent dragElsewhere = mock(InventoryDragEvent.class);
        when(dragElsewhere.getInventory()).thenReturn(chest);
        support.fire("onDrag", InventoryDragEvent.class, dragElsewhere);
        verify(dragElsewhere, never()).setCancelled(true);
        assertTrue(menuHolder.getInventory() == menu);
    }
}
