package net.tfminecraft.spiders;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.configuration.PluginMeta;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import io.papermc.paper.plugin.provider.classloader.ConfiguredPluginClassLoader;
import io.papermc.paper.plugin.provider.classloader.PluginClassLoaderGroup;
import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.testing.AppTestSupport;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.custom_items.StaffTool;
import net.tfminecraft.spiders.utilities.custom_items.StaffToolMenu;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SpiderAnimationPluginTest {
    private final SpiderAnimationPlugin plugin = mock(SpiderAnimationPlugin.class);
    @SuppressWarnings("unchecked")
    private final LifecycleEventManager<Plugin> lifecycle = mock(LifecycleEventManager.class);
    private AppTestSupport app;

    @BeforeEach
    void setup() {
        doCallRealMethod().when(plugin).onEnable();
        doCallRealMethod().when(plugin).onDisable();
        when(plugin.getLifecycleManager()).thenReturn(lifecycle);
        app = new AppTestSupport(plugin);
    }

    @AfterEach
    void restore() {
        app.close();
    }

    private World world(boolean chunksLoaded) {
        World world = mock(World.class);
        when(world.getName()).thenReturn("world");
        when(world.getPlayers()).thenReturn(List.of());
        when(world.isChunkLoaded(anyInt(), anyInt())).thenReturn(chunksLoaded);
        return world;
    }

    @Test
    @DisplayName("enabling clears leftover displays, registers the command, tools and listeners, and announces itself")
    void enablingWiresThePluginUp() {
        World world = world(true);
        Entity leftover = mock(Entity.class);
        when(leftover.getScoreboardTags()).thenReturn(Set.of("tfmc_spider_render"));
        Entity unrelated = mock(Entity.class);
        when(unrelated.getScoreboardTags()).thenReturn(Set.of());
        when(world.getEntities()).thenReturn(List.of(leftover, unrelated));
        app.worlds.add(world);

        plugin.onEnable();

        assertSame(plugin, Core.getCurrentPlugin());
        verify(leftover, atLeastOnce()).remove();
        verify(unrelated, never()).remove();

        List<String> tools = new ArrayList<>();
        for (StaffTool tool : StaffToolMenu.getStaffTools()) tools.add(tool.getName());
        assertTrue(tools.containsAll(List.of("Cinderweaver Summoning Seal", "Limb Binding Charm", "Broodkeeper's Sight",
            "Spectral Form", "Shroud Charm", "Hunting Instinct", "Clinging Charm")), tools.toString());
        assertTrue(app.listeners.size() >= 3, "tool, menu and world listeners");
        verify(app.logger).info(argThat((String message) -> message.contains(StaffAccess.PERMISSION) && message.contains("limit: " + AppState.MAX_SPIDERS)));
    }

    @Test
    @DisplayName("the /spider command is registered when the server asks for commands")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void theSpiderCommandIsRegistered() throws Exception {
        plugin.onEnable();

        ArgumentCaptor<LifecycleEventHandler> handler = ArgumentCaptor.forClass(LifecycleEventHandler.class);
        verify(lifecycle).registerEventHandler(eq(LifecycleEvents.COMMANDS), handler.capture());
        ReloadableRegistrarEvent<Commands> event = mock(ReloadableRegistrarEvent.class);
        Commands commands = mock(Commands.class);
        when(event.registrar()).thenReturn(commands);
        handler.getValue().run(event);

        ArgumentCaptor<LiteralCommandNode<CommandSourceStack>> node = ArgumentCaptor.forClass(LiteralCommandNode.class);
        verify(commands).register(node.capture(), eq("Cinderweaver spiders"));
        assertEquals("spider", node.getValue().getLiteral());
    }

    @Test
    @DisplayName("each server tick puts unwatched spiders to sleep and clears broken ones")
    void eachTickSleepsAndClearsSpiders() {
        plugin.onEnable();
        World world = world(true);
        ECSEntity unwatched = AppState.getEcs().spawn(SpiderBody.create(world, new Vector(0.5, 64.0, 0.5)));
        SpiderBody brokenBody = SpiderBody.create(world, new Vector(0.5, 64.0, 0.5));
        brokenBody.getPosition().setY(Double.NaN);
        ECSEntity broken = AppState.getEcs().spawn(brokenBody);
        int tick = Core.getCurrentTick();

        app.runNewTickHandlers();

        assertEquals(tick + 1, Core.getCurrentTick());
        assertTrue(unwatched.isAsleep());
        assertTrue(AppState.getEcs().getEntities().contains(unwatched));
        assertFalse(AppState.getEcs().getEntities().contains(broken));
        verify(app.logger).warning(contains("simulation broke"));
    }

    @Test
    @DisplayName("unloading a world removes only the spiders in it")
    void unloadingAWorldRemovesItsSpiders() {
        plugin.onEnable();
        World unloaded = world(true);
        World other = world(true);
        ECSEntity inside = AppState.getEcs().spawn(SpiderBody.create(unloaded, new Vector(0.5, 64.0, 0.5)));
        ECSEntity outside = AppState.getEcs().spawn(SpiderBody.create(other, new Vector(0.5, 64.0, 0.5)));

        app.fire(new WorldUnloadEvent(unloaded));

        assertTrue(inside.getScheduledForRemoval());
        assertFalse(outside.getScheduledForRemoval());
    }

    @Test
    @DisplayName("disabling runs the shutdown hooks and forgets every spider")
    void disablingRunsShutdownHooksAndForgetsSpiders() {
        boolean[] ran = {false};
        Core.onPluginShutdown(() -> ran[0] = true);
        AppState.getEcs().spawn(SpiderBody.create(world(true), new Vector(0.5, 64.0, 0.5)));

        plugin.onDisable();

        assertTrue(ran[0]);
        assertTrue(AppState.getEcs().getEntities().isEmpty());
    }

    /** Stands in for Paper's plugin class loader, which hands each plugin it creates back to itself. */
    private static final class PaperLikeLoader extends ClassLoader implements ConfiguredPluginClassLoader {
        private final String name = SpiderAnimationPlugin.class.getName();
        private JavaPlugin plugin;

        PaperLikeLoader() {
            super(SpiderAnimationPluginTest.class.getClassLoader());
        }

        @Override
        protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
            if (!className.equals(name)) return super.loadClass(className, resolve);
            synchronized (getClassLoadingLock(className)) {
                Class<?> loaded = findLoadedClass(className);
                if (loaded != null) return loaded;
                try (InputStream in = getParent().getResourceAsStream(className.replace('.', '/') + ".class")) {
                    byte[] bytes = in.readAllBytes();
                    return defineClass(className, bytes, 0, bytes.length, SpiderAnimationPlugin.class.getProtectionDomain());
                } catch (IOException e) {
                    throw new ClassNotFoundException(className, e);
                }
            }
        }

        @Override
        public Class<?> loadClass(String className, boolean resolve, boolean checkGlobal, boolean checkLibraries) throws ClassNotFoundException {
            return loadClass(className, resolve);
        }

        @Override
        public void init(JavaPlugin plugin) {
            this.plugin = plugin;
        }

        @Override
        public JavaPlugin getPlugin() {
            return plugin;
        }

        @Override
        public PluginMeta getConfiguration() {
            return null;
        }

        @Override
        public PluginClassLoaderGroup getGroup() {
            return null;
        }

        @Override
        public void close() {
        }
    }

    @Test
    @DisplayName("Paper's plugin class loader can create the plugin")
    void paperCanCreateThePlugin() throws Exception {
        PaperLikeLoader loader = new PaperLikeLoader();
        Object created = loader.loadClass(SpiderAnimationPlugin.class.getName()).getDeclaredConstructor().newInstance();
        assertSame(created, loader.getPlugin());
        assertEquals(SpiderAnimationPlugin.class.getName(), created.getClass().getName());
    }
}
