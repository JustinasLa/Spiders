package com.heledron.spideranimation.spider.presets

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.mockito.MockedStatic
import kotlin.test.*

class CinderweaverTest {
    companion object {
        private lateinit var bukkit: MockedStatic<Bukkit>
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() {
            bukkit = mockStatic(Bukkit::class.java)
            bukkit.`when`<BlockData> { Bukkit.createBlockData(any(Material::class.java)) }.thenAnswer { blockData(it.getArgument(0)) }
            bukkit.`when`<BlockData> { Bukkit.createBlockData(anyString()) }.thenAnswer {
                blockData(Material.matchMaterial(it.getArgument<String>(0).substringBefore('['))!!)
            }
        }

        @JvmStatic
        @AfterAll
        fun restoreBukkit() { if (::bukkit.isInitialized) bukkit.close() }

        private fun blockData(material: Material): BlockData {
            val data = mock(BlockData::class.java)
            `when`(data.material).thenReturn(material)
            `when`(data.clone()).thenAnswer { blockData(material) }
            return data
        }
    }

    @Test
    fun `torso has finite geometry six ember eyes and the intended iron and copper materials`() {
        val pieces = cinderweaverTorso().pieces
        assertTrue(pieces.size in 25..45)
        assertTrue(pieces.all { it.transform.isFinite })
        assertEquals(6, pieces.count { "eye" in it.tags })
        val materials = pieces.map { it.block.material }.toSet()
        assertTrue(materials.containsAll(listOf(Material.NETHERITE_BLOCK, Material.POLISHED_BLACKSTONE,
            Material.WAXED_EXPOSED_COPPER, Material.WAXED_WEATHERED_COPPER, Material.SHROOMLIGHT, Material.BLAST_FURNACE)))
    }

    @Test
    fun `separately spawned spiders do not share mutable geometry`() {
        val first = cinderweaverTorso()
        val second = cinderweaverTorso()
        val before = first.pieces[0].transform.m00()
        second.scale(2.0f)
        assertEquals(before, first.pieces[0].transform.m00())
        assertEquals(before * 2, second.pieces[0].transform.m00())
    }
}
