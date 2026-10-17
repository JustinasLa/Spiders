package net.tfminecraft.spiders.testing;

import net.tfminecraft.spiders.AppState;
import net.tfminecraft.spiders.StaffAccess;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.custom_items.CustomItems;
import net.tfminecraft.spiders.utilities.custom_items.StaffTool;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolMenu;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.events.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Server;
import org.bukkit.UnsafeValues;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.MockedStatic;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * A plugin without a server for tests of the plugin's entry points, staff tools and app state: a mocked plugin,
 * server and scheduler, {@link Bukkit} statics (block data, online players, worlds), the listeners the plugin
 * registers and a way to call them as the server would. Every piece of static state it touches (the current plugin,
 * the shared ECS and its systems, tick handlers, the tool lists and debug viewers) is put back by {@link #close()}.
 */
public final class AppTestSupport implements AutoCloseable {
    public final Server server = mock(Server.class);
    public final PluginManager manager = mock(PluginManager.class);
    public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    public final JavaPlugin plugin;
    public final Logger logger = mock(Logger.class);
    /** Every listener the plugin registered, in order. */
    public final List<Listener> listeners = new ArrayList<>();
    /** What {@link Bukkit#getOnlinePlayers()} returns. */
    public final List<Player> online = new ArrayList<>();
    /** What the server's world list returns. */
    public final List<World> worlds = new ArrayList<>();

    private final MockedStatic<Bukkit> bukkit;
    private final JavaPlugin previousPlugin;
    private final List<StaffTool> previousTools;
    private final List<Object> previousToolComponents;
    private final List<Runnable> previousTickHandlers;
    private final List<Runnable> previousTickEndHandlers;
    private final Set<Object> previousDebugViewers;

    public AppTestSupport() {
        this(mock(JavaPlugin.class));
    }

    public AppTestSupport(JavaPlugin plugin) {
        this.plugin = plugin;
        when(plugin.getName()).thenReturn("TFMCSpiders");
        when(plugin.namespace()).thenReturn("tfmcspiders");
        when(plugin.getServer()).thenReturn(server);
        when(plugin.getLogger()).thenReturn(logger);
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getScheduler()).thenReturn(scheduler);
        when(server.getWorlds()).thenReturn(worlds);
        doAnswer(call -> listeners.add(call.getArgument(0))).when(manager).registerEvents(any(Listener.class), eq(plugin));

        previousPlugin = (JavaPlugin) read(Core.class, "currentPlugin", null);
        Core.setCurrentPlugin(plugin);

        bukkit = mockStatic(Bukkit.class);
        bukkit.when(() -> Bukkit.createBlockData(any(Material.class))).thenAnswer(call -> blockData(call.getArgument(0)));
        bukkit.when(() -> Bukkit.createBlockData(anyString())).thenAnswer(call -> {
            String id = call.getArgument(0);
            int bracket = id.indexOf('[');
            return blockData(Objects.requireNonNull(Material.matchMaterial(bracket < 0 ? id : id.substring(0, bracket))));
        });
        bukkit.when(Bukkit::getServer).thenReturn(server);
        bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
        bukkit.when(Bukkit::getUnsafe).thenReturn(mock(UnsafeValues.class));
        bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
        bukkit.when(Bukkit::getOnlinePlayers).thenAnswer(call -> online);
        bukkit.when(Bukkit::getWorlds).thenAnswer(call -> worlds);
        bukkit.when(() -> Bukkit.getPlayer(any(UUID.class))).thenAnswer(call -> online.stream()
            .filter(player -> call.getArgument(0).equals(player.getUniqueId())).findFirst().orElse(null));

        previousTools = new ArrayList<>(StaffToolMenu.getStaffTools());
        previousToolComponents = new ArrayList<>(toolComponents());
        previousTickHandlers = new ArrayList<>(tickHandlers("main"));
        previousTickEndHandlers = new ArrayList<>(tickHandlers("end"));
        previousDebugViewers = new HashSet<>(debugViewers());
        clearSharedEcs();
    }

    @Override
    public void close() {
        clearSharedEcs();
        AppState.getEcs().setLogger(Logger.getLogger(ECS.class.getName()));
        restore(StaffToolMenu.getStaffTools(), previousTools);
        restore(toolComponents(), previousToolComponents);
        restore(tickHandlers("main"), previousTickHandlers);
        restore(tickHandlers("end"), previousTickEndHandlers);
        debugViewers().clear();
        debugViewers().addAll(previousDebugViewers);
        bukkit.close();
        Core.setCurrentPlugin(previousPlugin);
    }

    /** Block data that knows its material and how it is written, without a server registry. */
    public static BlockData blockData(Material material) {
        BlockData data = mock(BlockData.class);
        when(data.getMaterial()).thenReturn(material);
        when(data.getAsString()).thenReturn(material.getKey().toString());
        when(data.clone()).thenAnswer(call -> blockData(material));
        return data;
    }

    /** An online staff member standing at {@code location}, holding nothing. */
    public Player staff(Location location) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID());
        when(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true);
        when(player.getWorld()).thenReturn(location.getWorld());
        when(player.getLocation()).thenAnswer(call -> location.clone());
        when(player.getEyeLocation()).thenAnswer(call -> location.clone().add(0.0, 1.62, 0.0));
        PlayerInventory inventory = mock(PlayerInventory.class);
        when(inventory.getItemInMainHand()).thenReturn(mock(ItemStack.class));
        when(inventory.getItemInOffHand()).thenReturn(mock(ItemStack.class));
        when(player.getInventory()).thenReturn(inventory);
        online.add(player);
        return player;
    }

    /**
     * A staff tool of {@code material} whose meta keeps what is written to it, like a real item. The tool's own tag
     * is read from the item's container; values written through {@link ItemStack#setItemMeta} land in {@link #store}.
     */
    public static Tool tool(Material material) {
        return new Tool(material);
    }

    public static final class Tool {
        public final ItemStack item = mock(ItemStack.class);
        public final ItemMeta meta = mock(ItemMeta.class);
        /** The item meta's persistent data, by key. */
        public final Map<NamespacedKey, Object> store = new HashMap<>();

        private Tool(Material material) {
            when(item.getType()).thenReturn(material);
            PersistentDataContainer tag = mock(PersistentDataContainer.class);
            when(item.getPersistentDataContainer()).thenReturn(tag);
            when(tag.get(any(NamespacedKey.class), eq(PersistentDataType.BOOLEAN))).thenReturn(true);
            PersistentDataContainer data = mock(PersistentDataContainer.class);
            when(meta.getPersistentDataContainer()).thenReturn(data);
            when(data.get(any(NamespacedKey.class), any(PersistentDataType.class))).thenAnswer(call -> store.get(call.<NamespacedKey>getArgument(0)));
            doAnswer(call -> store.put(call.getArgument(0), call.getArgument(2))).when(data).set(any(NamespacedKey.class), any(PersistentDataType.class), any());
            doAnswer(call -> store.remove(call.<NamespacedKey>getArgument(0))).when(data).remove(any(NamespacedKey.class));
            when(item.getItemMeta()).thenReturn(meta);
        }

        /** The stored value whose key ends in {@code key}. */
        public Object stored(String key) {
            for (Map.Entry<NamespacedKey, Object> entry : store.entrySet()) {
                if (entry.getKey().getKey().equals(key)) return entry.getValue();
            }
            return null;
        }
    }

    /** Calls every registered {@link EventHandler} that takes {@code event}, as the server would. */
    public void fire(Object event) {
        for (Listener listener : new ArrayList<>(listeners)) {
            for (Method method : listener.getClass().getDeclaredMethods()) {
                if (!method.isAnnotationPresent(EventHandler.class) || method.getParameterCount() != 1) continue;
                if (!method.getParameterTypes()[0].isInstance(event)) continue;
                method.setAccessible(true);
                try {
                    method.invoke(listener, event);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw new IllegalStateException(e.getCause());
                } catch (IllegalAccessException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    /** Runs the tick handlers registered since this support was created, once each. */
    public void runNewTickHandlers() {
        List<Runnable> added = new ArrayList<>(tickHandlers("main"));
        added.removeAll(previousTickHandlers);
        for (Runnable handler : added) handler.run();
    }

    /** How many tick handlers were registered since this support was created. */
    public int newTickHandlerCount() {
        return tickHandlers("main").size() - previousTickHandlers.size();
    }

    /** Empties the shared ECS: its entities and every system and listener registered on it. */
    public static void clearSharedEcs() {
        ECS ecs = AppState.getEcs();
        ecs.getEntities().clear();
        for (String field : List.of("tickSystems", "postTickSystems", "renderSystems", "eventListeners")) {
            ((List<?>) read(ECS.class, field, ecs)).clear();
        }
    }

    /** How many systems of {@code kind} (tickSystems, postTickSystems, renderSystems, eventListeners) an ECS has. */
    public static int systemCount(ECS ecs, String kind) {
        return ((List<?>) read(ECS.class, kind, ecs)).size();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> toolComponents() {
        return (List<Object>) read(CustomItems.class, "toolComponents", null);
    }

    @SuppressWarnings("unchecked")
    private static Set<Object> debugViewers() {
        return (Set<Object>) read(AppState.class, "debugViewers", null);
    }

    @SuppressWarnings("unchecked")
    private static List<Runnable> tickHandlers(String which) {
        try {
            Class<?> schedule = Class.forName(Scheduler.class.getName() + "$TickSchedule");
            return (List<Runnable>) read(schedule, which, null);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        }
    }

    private static <T> void restore(List<T> list, List<T> previous) {
        list.clear();
        list.addAll(previous);
    }

    private static Object read(Class<?> owner, String name, Object instance) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field.get(instance);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
