package net.tfminecraft.spiders

import net.tfminecraft.spiders.commands.spiderCommandTree
import net.tfminecraft.spiders.spider.configuration.SpiderOptions
import net.tfminecraft.spiders.utilities.ecs.ECSEntity
import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.exceptions.CommandSyntaxException
import io.papermc.paper.command.brigadier.CommandSourceStack
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.test.*

class StaffAccessTest {
    @AfterEach
    fun cleanState() { AppState.ecs.entities.clear() }

    @Test
    fun `permission is checked rather than assuming operators are always authorised`() {
        val player = mock(Player::class.java)
        `when`(player.isOp).thenReturn(true)
        assertFalse(StaffAccess.canUse(player))
        `when`(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true)
        assertTrue(StaffAccess.canUse(player))
        `when`(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(false)
        assertFalse(StaffAccess.canUse(player))
    }

    @Test
    fun `spawn factory denies a caller before reading the world or options`() {
        val actor = mock(CommandSender::class.java)
        val world = mock(World::class.java)
        val options = mock(SpiderOptions::class.java)
        assertNull(AppState.createSpider(actor, world, Vector(), options))
        assertEquals(0, AppState.spiderCount())
        verifyNoInteractions(world, options)
    }

    @Test
    fun `every command branch is unreachable without staff permission`() {
        val source = mock(CommandSourceStack::class.java)
        val player = mock(Player::class.java)
        `when`(source.sender).thenReturn(player)
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(spiderCommandTree {})
        for (command in listOf("spider", "spider spawn hunter", "spider tools", "spider items",
            "spider remove all", "spider list", "spider scale 1", "spider splay", "spider walk 20",
            "spider options global.showLaser set false")) {
            assertFailsWith<CommandSyntaxException>(command) { dispatcher.execute(command, source) }
        }
        assertEquals(0, AppState.spiderCount())
    }

    @Test
    fun `staff can use commands and losing permission blocks the same source immediately`() {
        val source = mock(CommandSourceStack::class.java)
        val actor = mock(CommandSender::class.java)
        `when`(source.sender).thenReturn(actor)
        `when`(actor.hasPermission(StaffAccess.PERMISSION)).thenReturn(true)
        val dispatcher = CommandDispatcher<CommandSourceStack>()
        dispatcher.register(spiderCommandTree {})
        assertEquals(1, dispatcher.execute("spider list", source))
        `when`(actor.hasPermission(StaffAccess.PERMISSION)).thenReturn(false)
        assertFailsWith<CommandSyntaxException> { dispatcher.execute("spider list", source) }
    }
}
