package net.tfminecraft.spiders.spider.components

import net.tfminecraft.spiders.StaffAccess
import net.tfminecraft.spiders.utilities.ecs.ECS
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import kotlin.test.*

class StaffMountTest {
    private val app = ECS()
    private val pig = mock(Pig::class.java)
    private val marker = mock(ArmorStand::class.java)
    private val mountable = Mountable().apply { currentPig = pig; currentMarker = marker }
    private val listener = StaffMountListener(app)

    init { app.spawn(mountable) }

    @Test
    fun `nonstaff cannot mount either the pig or hidden seat`() {
        val player = mock(Player::class.java)
        for (seat in listOf(pig, marker)) {
            val event = EntityMountEvent(player, seat)
            listener.onMount(event)
            assertTrue(event.isCancelled)
        }
        verify(marker, never()).addPassenger(any())
    }

    @Test
    fun `nonstaff interaction with a spider is cancelled before saddle access`() {
        val player = mock(Player::class.java)
        val event = PlayerInteractEntityEvent(player, pig, EquipmentSlot.HAND)
        listener.onInteract(event)
        assertTrue(event.isCancelled)
        verify(player, never()).inventory
    }

    @Test
    fun `staff mounting the pig is redirected to its seat`() {
        val player = mock(Player::class.java)
        `when`(player.hasPermission(StaffAccess.PERMISSION)).thenReturn(true)
        `when`(marker.passengers).thenReturn(emptyList())
        val event = EntityMountEvent(player, pig)
        listener.onMount(event)
        assertTrue(event.isCancelled)
        verify(marker).addPassenger(player)
    }

    @Test
    fun `ordinary animals are unaffected`() {
        val event = EntityMountEvent(mock(Player::class.java), mock(Pig::class.java))
        listener.onMount(event)
        assertFalse(event.isCancelled)
    }

    @Test
    fun `nonplayers cannot become spider passengers`() {
        val event = EntityMountEvent(mock(Pig::class.java), marker)
        listener.onMount(event)
        assertTrue(event.isCancelled)
    }
}
