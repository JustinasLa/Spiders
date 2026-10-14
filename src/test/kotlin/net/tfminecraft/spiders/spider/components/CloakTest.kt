package net.tfminecraft.spiders.spider.components

import org.bukkit.FluidCollisionMode
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Player
import org.bukkit.util.RayTraceResult
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.*
import kotlin.test.assertEquals

class CloakTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() = BlockDataMocks.install()

        @JvmStatic
        @AfterAll
        fun restoreBukkit() = BlockDataMocks.restore()
    }

    private val piece = Vector(0.5, 65.0, 0.5)

    private fun player(eye: Vector, world: World) = mock(Player::class.java).also {
        `when`(it.eyeLocation).thenAnswer { eye.toLocation(world) }
        `when`(it.gameMode).thenReturn(GameMode.SURVIVAL)
    }

    private fun hit(material: Material): RayTraceResult {
        val data = mock(BlockData::class.java)
        `when`(data.material).thenReturn(material)
        val block = mock(Block::class.java)
        `when`(block.blockData).thenReturn(data)
        return RayTraceResult(Vector(), block, BlockFace.UP)
    }

    /**
     * Looking along +z past the piece there is water over a sand bed; any other direction finds stone.
     * Only a fluid-aware cast sees the water.
     */
    private fun world(players: (World) -> List<Player>): World {
        val world = mock(World::class.java)
        val list = players(world)
        `when`(world.players).thenReturn(list)
        `when`(world.rayTraceBlocks(any(Location::class.java), any(Vector::class.java), anyDouble(),
            any(FluidCollisionMode::class.java), eq(true))).thenAnswer { call ->
            val direction = call.getArgument<Vector>(1).clone().normalize()
            val fluids = call.getArgument<FluidCollisionMode>(3) == FluidCollisionMode.ALWAYS
            when {
                direction.z > .9 -> hit(if (fluids) Material.WATER else Material.SAND)
                else -> hit(Material.STONE)
            }
        }
        return world
    }

    @Test
    fun `sees the water behind the spider from the nearest player, not the bed under it`() {
        // the far player is listed first and looks along -x; the near one looks along +z through the piece
        val world = world { w -> listOf(player(Vector(200.5, 65.0, 0.5), w), player(Vector(0.5, 65.0, -5.0), w)) }
        assertEquals(Material.WATER, shroudSees(world, piece)?.material)
    }

    @Test
    fun `without a nearby player it copies the ground`() {
        val world = world { w -> listOf(player(Vector(0.5, 65.0, -500.0), w)) }
        assertEquals(Material.STONE, shroudSees(world, piece)?.material)
    }

    @Test
    fun `spectators do not steer the shroud`() {
        val world = world { w -> listOf(player(Vector(0.5, 65.0, -5.0), w).also { `when`(it.gameMode).thenReturn(GameMode.SPECTATOR) }) }
        assertEquals(Material.STONE, shroudSees(world, piece)?.material)
    }
}
