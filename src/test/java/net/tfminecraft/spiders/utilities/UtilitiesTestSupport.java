package net.tfminecraft.spiders.utilities;

import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;
import org.mockito.ArgumentCaptor;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.logging.Logger;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** A mocked plugin installed as {@link Core}'s current plugin, with a server, plugin manager, scheduler and logger. */
public final class UtilitiesTestSupport {
    public final JavaPlugin plugin = mock(JavaPlugin.class);
    public final Server server = mock(Server.class);
    public final PluginManager manager = mock(PluginManager.class);
    public final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    public final Logger logger = mock(Logger.class);

    public static UtilitiesTestSupport install() {
        UtilitiesTestSupport support = new UtilitiesTestSupport();
        when(support.plugin.getName()).thenReturn("TFMCSpiders");
        when(support.plugin.namespace()).thenReturn("tfmcspiders");
        when(support.plugin.getServer()).thenReturn(support.server);
        when(support.plugin.getLogger()).thenReturn(support.logger);
        when(support.server.getPluginManager()).thenReturn(support.manager);
        when(support.server.getScheduler()).thenReturn(support.scheduler);
        Core.setCurrentPlugin(support.plugin);
        return support;
    }

    /** Every listener registered with this plugin so far. */
    public List<Listener> listeners() {
        ArgumentCaptor<Listener> captor = ArgumentCaptor.forClass(Listener.class);
        verify(manager, atLeastOnce()).registerEvents(captor.capture(), eq(plugin));
        return captor.getAllValues();
    }

    /** The only listener registered with this plugin so far. */
    public Listener listener() {
        List<Listener> listeners = listeners();
        if (listeners.size() != 1) throw new AssertionError("expected one listener, got " + listeners.size());
        return listeners.getFirst();
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
}
