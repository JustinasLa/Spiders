package net.tfminecraft.spiders.utilities.custom_items;

import io.papermc.paper.datacomponent.DataComponentTypes;
import net.kyori.adventure.text.Component;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.UtilitiesTestSupport;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Crafter;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
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
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomItemsTest {
    private UtilitiesTestSupport support;

    @BeforeEach
    void setup() {
        support = UtilitiesTestSupport.install();
    }

    @AfterEach
    void reset() {
        support.close();
    }

    static ItemStack item(Material material, boolean tagged) {
        ItemStack item = mock(ItemStack.class);
        when(item.getType()).thenReturn(material);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        when(item.getPersistentDataContainer()).thenReturn(pdc);
        when(pdc.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN))).thenReturn(tagged ? true : null);
        return item;
    }

    private static Player player(boolean staff) {
        Player player = mock(Player.class);
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(staff);
        when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
        return player;
    }

    @Test
    @DisplayName("a tool's tag is a namespaced key named after its id, set to true")
    void attachingSetsTheTag() {
        CustomItemComponent component = new CustomItemComponent("sight", Material.BONE);
        assertEquals("sight", component.getId());
        assertEquals(Material.BONE, component.getMaterial());
        ItemStack item = mock(ItemStack.class);
        PersistentDataContainer pdc = mock(PersistentDataContainer.class);
        doAnswer(call -> {
            Consumer<PersistentDataContainer> edit = call.getArgument(0);
            edit.accept(pdc);
            return true;
        }).when(item).editPersistentDataContainer(any());

        assertSame(item, CustomItems.attach(item, component));
        component.attach(item);
        verify(pdc, times(2)).set(new NamespacedKey("tfmcspiders", "item_component_sight"), PersistentDataType.BOOLEAN, true);
    }

    @Test
    @DisplayName("a new tool is a named item of the tool's material, tagged as the tool")
    void newToolsAreNamedAndTagged() {
        CustomItemComponent component = new CustomItemComponent("sight", Material.BONE);
        List<List<?>> arguments = new ArrayList<>();
        try (MockedConstruction<ItemStack> items = mockConstruction(ItemStack.class, (mock, context) -> arguments.add(context.arguments()))) {
            ItemStack item = component.create("Broodkeeper's Sight");
            assertSame(items.constructed().getFirst(), item);
            assertEquals(List.of(Material.BONE), arguments.getFirst());
            verify(item).setData(DataComponentTypes.ITEM_NAME, Component.text("Broodkeeper's Sight"));
            verify(item).editPersistentDataContainer(any());
        }
    }

    @Test
    @DisplayName("staff tools are matched by tag and type, and nothing else is one")
    void staffToolsAreMatchedExactly() {
        new CustomItemComponent("sight", Material.BONE);
        new CustomItemComponent("charm", Material.GREEN_DYE);
        assertTrue(CustomItems.isStaffTool(item(Material.GREEN_DYE, true)));
        assertFalse(CustomItems.isStaffTool(item(Material.BONE, false)));
        assertFalse(CustomItems.isStaffTool(item(Material.STICK, true)));
        assertFalse(CustomItems.isStaffTool(null));

        StaffToolsReset.clear();
        assertFalse(CustomItems.isStaffTool(item(Material.GREEN_DYE, true)), "forgotten after a reset");
    }

    @Test
    @DisplayName("only staff holding the tool in either hand count as holding it")
    void playersHoldingTheTool() {
        CustomItemComponent component = new CustomItemComponent("beacon", Material.REDSTONE_TORCH);
        Player mainHand = player(true);
        ItemStack held1 = item(Material.REDSTONE_TORCH, true);
        when(mainHand.getInventory().getItemInMainHand()).thenReturn(held1);
        Player offHand = player(true);
        ItemStack held2 = item(Material.STICK, false);
        when(offHand.getInventory().getItemInMainHand()).thenReturn(held2);
        ItemStack held3 = item(Material.REDSTONE_TORCH, true);
        when(offHand.getInventory().getItemInOffHand()).thenReturn(held3);
        Player neither = player(true);
        ItemStack held4 = item(Material.STICK, false);
        when(neither.getInventory().getItemInMainHand()).thenReturn(held4);
        ItemStack held5 = item(Material.STICK, false);
        when(neither.getInventory().getItemInOffHand()).thenReturn(held5);
        Player regular = player(false);

        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            Collection<Player> online = List.of(mainHand, offHand, neither, regular);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(online);
            assertEquals(List.of(mainHand, offHand), component.getPlayersHoldingItem());
        }
    }

    @Test
    @DisplayName("gesture use only fires for the tool")
    void gestureUseOnlyFiresForTheTool() {
        CustomItemComponent component = new CustomItemComponent("spawn", Material.FERMENTED_SPIDER_EYE);
        List<ItemStack> used = new ArrayList<>();
        component.onGestureUse((player, item) -> used.add(item));
        Player staff = player(true);

        support.fire("onPlayerInteract", PlayerInteractEvent.class, new PlayerInteractEvent(staff, Action.RIGHT_CLICK_AIR, item(Material.STICK, true), null, BlockFace.SELF, EquipmentSlot.HAND));
        assertTrue(used.isEmpty());
        ItemStack tool = item(Material.FERMENTED_SPIDER_EYE, true);
        support.fire("onPlayerInteract", PlayerInteractEvent.class, new PlayerInteractEvent(staff, Action.RIGHT_CLICK_AIR, tool, null, BlockFace.SELF, EquipmentSlot.HAND));
        assertEquals(List.of(tool), used);
    }

    @Test
    @DisplayName("using the tool on an entity needs the tool in that hand and staff access")
    void interactEntityNeedsTheToolAndStaff() {
        CustomItemComponent component = new CustomItemComponent("leash", Material.LEAD);
        List<Entity> used = new ArrayList<>();
        component.onInteractEntity((player, entity, item) -> used.add(entity));
        Entity target = mock(Entity.class);

        Player staff = player(true);
        ItemStack held6 = item(Material.LEAD, true);
        when(staff.getInventory().getItem(EquipmentSlot.OFF_HAND)).thenReturn(held6);
        ItemStack held7 = item(Material.STICK, false);
        when(staff.getInventory().getItem(EquipmentSlot.HAND)).thenReturn(held7);
        Player regular = player(false);
        ItemStack held8 = item(Material.LEAD, true);
        when(regular.getInventory().getItem(EquipmentSlot.OFF_HAND)).thenReturn(held8);

        fireInteract(staff, target, EquipmentSlot.HAND);
        fireInteract(regular, target, EquipmentSlot.OFF_HAND);
        assertTrue(used.isEmpty());
        verify(regular).sendActionBar(any(Component.class));
        fireInteract(staff, target, EquipmentSlot.OFF_HAND);
        assertEquals(List.of(target), used);
    }

    private void fireInteract(Player player, Entity target, EquipmentSlot hand) {
        PlayerInteractEntityEvent event = mock(PlayerInteractEntityEvent.class);
        when(event.getPlayer()).thenReturn(player);
        when(event.getRightClicked()).thenReturn(target);
        when(event.getHand()).thenReturn(hand);
        support.fire("onInteract", PlayerInteractEntityEvent.class, event);
    }

    private static Block block(BlockData data) {
        Block block = mock(Block.class);
        when(block.getBlockData()).thenReturn(data);
        when(block.getType()).thenReturn(Material.OAK_SIGN);
        when(block.getState(false)).thenReturn(mock(BlockState.class));
        return block;
    }

    @Test
    @DisplayName("a held tool is never used as its item, but doors still open unless sneaking")
    void toolsAreNeverUsedAsItems() {
        CustomItems.setupStaffTools();
        new CustomItemComponent("charm", Material.GREEN_DYE);
        Player player = player(true);
        Player sneaking = player(true);
        when(sneaking.isSneaking()).thenReturn(true);
        ItemStack tool = item(Material.GREEN_DYE, true);

        PlayerInteractEvent plain = use(player, Action.RIGHT_CLICK_BLOCK, item(Material.GREEN_DYE, false), block(mock(BlockData.class)));
        assertNotEquals(Event.Result.DENY, plain.useItemInHand());
        assertNotEquals(Event.Result.DENY, plain.useInteractedBlock());

        PlayerInteractEvent inAir = use(player, Action.RIGHT_CLICK_AIR, tool, null);
        assertEquals(Event.Result.DENY, inAir.useItemInHand());

        PlayerInteractEvent onDoor = use(player, Action.RIGHT_CLICK_BLOCK, tool, block(mock(Door.class)));
        assertEquals(Event.Result.DENY, onDoor.useItemInHand());
        assertNotEquals(Event.Result.DENY, onDoor.useInteractedBlock());

        PlayerInteractEvent sneakingOnDoor = use(sneaking, Action.RIGHT_CLICK_BLOCK, tool, block(mock(Door.class)));
        assertEquals(Event.Result.DENY, sneakingOnDoor.useInteractedBlock());

        PlayerInteractEvent onSign = use(player, Action.RIGHT_CLICK_BLOCK, tool, block(mock(BlockData.class)));
        assertEquals(Event.Result.DENY, onSign.useInteractedBlock());
    }

    private PlayerInteractEvent use(Player player, Action action, ItemStack item, Block block) {
        PlayerInteractEvent event = new PlayerInteractEvent(player, action, item, block, BlockFace.UP, EquipmentSlot.HAND);
        support.fire("onUse", PlayerInteractEvent.class, event);
        return event;
    }

    private static Item dropped(ItemStack stack) {
        Item item = mock(Item.class);
        when(item.getItemStack()).thenReturn(stack);
        return item;
    }

    @Test
    @DisplayName("only staff pick tools up; anyone else's pickup removes it, and mobs and hoppers can't take it")
    void toolPickups() {
        CustomItems.setupStaffTools();
        new CustomItemComponent("sight", Material.BONE);
        ItemStack tool = item(Material.BONE, true);

        EntityPickupItemEvent ordinary = new EntityPickupItemEvent(player(false), dropped(item(Material.BONE, false)), 0);
        support.fire("onPickup", EntityPickupItemEvent.class, ordinary);
        assertFalse(ordinary.isCancelled());

        Item kept = dropped(tool);
        EntityPickupItemEvent byStaff = new EntityPickupItemEvent(player(true), kept, 0);
        support.fire("onPickup", EntityPickupItemEvent.class, byStaff);
        assertFalse(byStaff.isCancelled());

        Item taken = dropped(tool);
        EntityPickupItemEvent byPlayer = new EntityPickupItemEvent(player(false), taken, 0);
        support.fire("onPickup", EntityPickupItemEvent.class, byPlayer);
        assertTrue(byPlayer.isCancelled());
        verify(taken).remove();

        Item left = dropped(tool);
        EntityPickupItemEvent byMob = new EntityPickupItemEvent(mock(Zombie.class), left, 0);
        support.fire("onPickup", EntityPickupItemEvent.class, byMob);
        assertTrue(byMob.isCancelled());
        verify(left, never()).remove();
        verify(kept, never()).remove();

        Item plainForHopper = dropped(item(Material.BONE, false));
        InventoryPickupItemEvent hopperPlain = new InventoryPickupItemEvent(mock(Inventory.class), plainForHopper);
        support.fire("onHopperPickup", InventoryPickupItemEvent.class, hopperPlain);
        assertFalse(hopperPlain.isCancelled());
        Item toolForHopper = dropped(tool);
        InventoryPickupItemEvent hopperTool = new InventoryPickupItemEvent(mock(Inventory.class), toolForHopper);
        support.fire("onHopperPickup", InventoryPickupItemEvent.class, hopperTool);
        assertTrue(hopperTool.isCancelled());
        verify(toolForHopper).remove();
    }

    @Test
    @DisplayName("tools are never crafted, brewed, placed, consumed or used on entities")
    void toolsHaveNoVanillaUses() {
        CustomItems.setupStaffTools();
        new CustomItemComponent("sight", Material.BONE);
        ItemStack tool = item(Material.BONE, true);
        ItemStack plain = item(Material.BONE, false);

        CraftingInventory withTool = mock(CraftingInventory.class);
        when(withTool.getMatrix()).thenReturn(new ItemStack[]{null, plain, tool});
        PrepareItemCraftEvent craftTool = mock(PrepareItemCraftEvent.class);
        when(craftTool.getInventory()).thenReturn(withTool);
        support.fire("onPrepareCraft", PrepareItemCraftEvent.class, craftTool);
        verify(withTool).setResult(null);
        CraftingInventory withoutTool = mock(CraftingInventory.class);
        when(withoutTool.getMatrix()).thenReturn(new ItemStack[]{null, plain});
        PrepareItemCraftEvent craftPlain = mock(PrepareItemCraftEvent.class);
        when(craftPlain.getInventory()).thenReturn(withoutTool);
        support.fire("onPrepareCraft", PrepareItemCraftEvent.class, craftPlain);
        verify(withoutTool, never()).setResult(any());

        assertTrue(crafterCancels(new ItemStack[]{plain, tool}));
        assertFalse(crafterCancels(new ItemStack[]{plain}));
        CrafterCraftEvent notACrafter = mock(CrafterCraftEvent.class);
        Block block = mock(Block.class);
        when(block.getState(false)).thenReturn(mock(BlockState.class));
        when(notACrafter.getBlock()).thenReturn(block);
        support.fire("onCrafterCraft", CrafterCraftEvent.class, notACrafter);
        verify(notACrafter, never()).setCancelled(true);

        for (ItemStack ingredient : new ItemStack[]{tool, plain}) {
            BrewEvent brew = mock(BrewEvent.class);
            BrewerInventory contents = mock(BrewerInventory.class);
            when(contents.getIngredient()).thenReturn(ingredient);
            when(brew.getContents()).thenReturn(contents);
            support.fire("onBrew", BrewEvent.class, brew);
            verify(brew, ingredient == tool ? times(1) : never()).setCancelled(true);

            BlockPlaceEvent place = mock(BlockPlaceEvent.class);
            when(place.getItemInHand()).thenReturn(ingredient);
            support.fire("onPlace", BlockPlaceEvent.class, place);
            verify(place, ingredient == tool ? times(1) : never()).setCancelled(true);

            PlayerItemConsumeEvent consume = mock(PlayerItemConsumeEvent.class);
            when(consume.getItem()).thenReturn(ingredient);
            support.fire("onConsume", PlayerItemConsumeEvent.class, consume);
            verify(consume, ingredient == tool ? times(1) : never()).setCancelled(true);

            Player player = player(true);
            when(player.getInventory().getItem(EquipmentSlot.HAND)).thenReturn(ingredient);
            PlayerInteractEntityEvent onEntity = mock(PlayerInteractEntityEvent.class);
            when(onEntity.getPlayer()).thenReturn(player);
            when(onEntity.getHand()).thenReturn(EquipmentSlot.HAND);
            support.fire("onUseOnEntity", PlayerInteractEntityEvent.class, onEntity);
            verify(onEntity, ingredient == tool ? times(1) : never()).setCancelled(true);
        }
    }

    private boolean crafterCancels(ItemStack[] contents) {
        Crafter crafter = mock(Crafter.class);
        Inventory inventory = mock(Inventory.class);
        when(inventory.getContents()).thenReturn(contents);
        when(crafter.getInventory()).thenReturn(inventory);
        Block block = mock(Block.class);
        when(block.getState(false)).thenReturn(crafter);
        CrafterCraftEvent event = mock(CrafterCraftEvent.class);
        when(event.getBlock()).thenReturn(block);
        boolean[] cancelled = {false};
        doAnswer(call -> cancelled[0] = call.getArgument(0)).when(event).setCancelled(anyBoolean());
        support.fire("onCrafterCraft", CrafterCraftEvent.class, event);
        return cancelled[0];
    }
}
