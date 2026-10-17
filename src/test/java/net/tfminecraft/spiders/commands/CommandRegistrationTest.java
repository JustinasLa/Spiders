package net.tfminecraft.spiders.commands;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager;
import io.papermc.paper.plugin.lifecycle.event.handler.LifecycleEventHandler;
import io.papermc.paper.plugin.lifecycle.event.registrar.ReloadableRegistrarEvent;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.tfminecraft.spiders.SpiderAnimationPlugin;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommandRegistrationTest {
    @Test
    @DisplayName("/spider is registered whenever the server (re)loads its commands")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void spiderIsRegisteredWithTheServer() {
        SpiderAnimationPlugin plugin = mock(SpiderAnimationPlugin.class);
        LifecycleEventManager<Plugin> manager = mock(LifecycleEventManager.class);
        when(plugin.getLifecycleManager()).thenReturn(manager);

        SetupCommands.setupCommands(plugin);

        ArgumentCaptor<LifecycleEventHandler> handler = ArgumentCaptor.forClass(LifecycleEventHandler.class);
        verify(manager).registerEventHandler(eq(LifecycleEvents.COMMANDS), handler.capture());
        ReloadableRegistrarEvent<Commands> event = mock(ReloadableRegistrarEvent.class);
        Commands registrar = mock(Commands.class);
        when(event.registrar()).thenReturn(registrar);
        handler.getValue().run(event);

        ArgumentCaptor<LiteralCommandNode<CommandSourceStack>> node = ArgumentCaptor.forClass(LiteralCommandNode.class);
        verify(registrar).register(node.capture(), eq("Cinderweaver spiders"));
        assertEquals("spider", node.getValue().getLiteral());
        assertNotNull(node.getValue().getChild("spawn"));
        assertNotNull(node.getValue().getChild("options"));
    }
}
