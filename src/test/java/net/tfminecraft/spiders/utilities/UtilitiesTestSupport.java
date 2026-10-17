package net.tfminecraft.spiders.utilities;

import net.tfminecraft.spiders.utilities.custom_items.StaffToolsReset;
import net.tfminecraft.spiders.utilities.events.SchedulerTestSupport;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A mocked plugin installed as {@link Core}'s current plugin, with a server, plugin manager, scheduler and logger.
 * It starts the test with no tick handlers and no staff tools, and {@link #close()} puts back everything static it
 * touched: the current plugin, the tick count, the shutdown hooks, the tick handlers and their error throttle, and
 * the staff tool lists.
 */
public final class UtilitiesTestSupport implements AutoCloseable {
    public final JavaPlugin plugin = mock(JavaPlugin.class);
    public final Server server = mock(Server.class);
    public final PluginManager manager = mock(PluginManager.class);
    public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    public final Logger logger = mock(Logger.class);

    private Object previousPlugin;
    private int previousTick;
    private List<Disposable> previousHooks;
    private Runnable restoreScheduler;
    private Runnable restoreTools;

    private UtilitiesTestSupport() {
    }

    public static UtilitiesTestSupport install() {
        UtilitiesTestSupport support = new UtilitiesTestSupport();
        when(support.plugin.getName()).thenReturn("TFMCSpiders");
        when(support.plugin.namespace()).thenReturn("tfmcspiders");
        when(support.plugin.getServer()).thenReturn(support.server);
        when(support.plugin.getLogger()).thenReturn(support.logger);
        when(support.server.getPluginManager()).thenReturn(support.manager);
        when(support.server.getScheduler()).thenReturn(support.scheduler);
        support.previousPlugin = read("currentPlugin");
        support.previousTick = Core.getCurrentTick();
        support.previousHooks = new ArrayList<>(hooks());
        Core.setCurrentPlugin(support.plugin);
        support.restoreScheduler = SchedulerTestSupport.isolate();
        support.restoreTools = StaffToolsReset.isolate();
        return support;
    }

    @Override
    public void close() {
        restoreTools.run();
        restoreScheduler.run();
        hooks().clear();
        hooks().addAll(previousHooks);
        write("currentTick", previousTick);
        write("currentPlugin", previousPlugin);
    }

    /** Core's shutdown hooks, which a test may change; {@link #close()} restores them. */
    @SuppressWarnings("unchecked")
    public static List<Disposable> hooks() {
        return (List<Disposable>) read("closeableList");
    }

    /** Every listener registered with this plugin so far. */
    public List<Listener> listeners() {
        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(manager, atLeastOnce()).registerEvents(captor.capture(), eq(plugin));
        return captor.getAllValues();
    }

    /** Calls every registered handler with the given name that takes this event type, as the server would. */
    public void fire(String handler, Class<?> type, Object event) {
        for (Listener listener : listeners()) {
            for (Method method : listener.getClass().getDeclaredMethods()) {
                if (!method.getName().equals(handler) || method.getParameterCount() != 1 || method.getParameterTypes()[0] != type) continue;
                method.setAccessible(true);
                try {
                    method.invoke(listener, event);
                } catch (IllegalAccessException e) {
                    throw new AssertionError(e);
                } catch (InvocationTargetException e) {
                    if (e.getCause() instanceof RuntimeException runtime) throw runtime;
                    throw new AssertionError(e.getCause());
                }
            }
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field field = Core.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }

    private static Object read(String name) {
        try {
            return field(name).get(null);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(String name, Object value) {
        try {
            field(name).set(null, value);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
