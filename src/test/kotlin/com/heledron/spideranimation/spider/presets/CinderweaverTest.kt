package com.heledron.spideranimation.spider.presets

import com.heledron.spideranimation.spider.components.body.createSpiderLegIK
import com.heledron.spideranimation.utilities.BlockDisplayModelPiece
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.data.BlockData
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import org.mockito.MockedStatic
import java.nio.file.Files
import java.nio.file.Path
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
    fun `torso has eight steady eyes and only organic matte materials`() {
        val pieces = cinderweaverTorso().pieces
        assertTrue(pieces.size in 60..95)
        assertTrue(pieces.all { it.transform.isFinite })
        assertEquals(8, pieces.count { "eye" in it.tags })
        val materials = pieces.map { it.block.material }.toSet()
        assertEquals(setOf(Material.BLACK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.BLACK_CONCRETE, Material.RED_TERRACOTTA), materials)
        assertTrue(pieces.filter { "eye" in it.tags }.all { it.brightness?.blockLight == 5 })
    }

    @Test
    fun `separately spawned spiders do not share mutable geometry`() {
        val first = cinderweaverBodyPlan(CinderweaverSize.HUNTER)
        val second = cinderweaverBodyPlan(CinderweaverSize.HUNTER)
        val torso = first.bodyModel.pieces[0].transform.m00()
        val leg = first.legs[0].segments[0].model.pieces[0].transform.m00()
        second.scale(2.0)
        assertEquals(torso, first.bodyModel.pieces[0].transform.m00())
        assertEquals(leg, first.legs[0].segments[0].model.pieces[0].transform.m00())
        assertEquals(torso * 2, second.bodyModel.pieces[0].transform.m00())
        assertEquals(leg * 2, second.legs[0].segments[0].model.pieces[0].transform.m00())
    }

    @Test
    fun `legacy seals receive organic geometry while retaining their scale and edited rig`() {
        val plan = cinderweaverBodyPlan(CinderweaverSize.BROODMOTHER)
        plan.bodyModel.pieces[0].block = blockData(Material.BLAST_FURNACE)
        plan.legs[0].restPosition.x += .2
        plan.legs[0].segments[0].length *= .9
        val length = plan.legs[0].segments[0].length
        val rest = plan.legs[0].restPosition.clone()
        assertTrue(upgradeClockworkAppearance(plan))
        assertEquals(1.5, plan.scale)
        assertEquals(rest, plan.legs[0].restPosition)
        assertEquals(length, plan.legs[0].segments[0].length)
        assertEquals(8, plan.bodyModel.pieces.count { "eye" in it.tags })
        assertTrue(plan.bodyModel.pieces.none { it.block.material == Material.BLAST_FURNACE })
        assertFalse(upgradeClockworkAppearance(plan), "Current organic plans should be left alone")
    }

    @Test
    fun `all sizes produce bounded finite geometry through the actual leg solver`() {
        for (size in CinderweaverSize.entries) {
            val plan = cinderweaverBodyPlan(size)
            assertEquals(8, plan.legs.size)
            assertTrue(plan.legs.all { it.segments.size == 3 })
            assertEquals(1, plan.eyePalette.size)
            val posed = mutableListOf<Pair<BlockDisplayModelPiece, Matrix4f>>()
            val position = Vector(0.0, 1.1 * size.scale, 0.0)
            plan.bodyModel.pieces.forEach { posed += it to Matrix4f().translation(0f, position.y.toFloat(), 0f).mul(it.transform) }
            for (leg in plan.legs) {
                val ik = createSpiderLegIK(leg)
                repeat(5) { ik.solve(position, Quaternionf(), leg.restPosition) }
                for ((index, segment) in leg.segments.withIndex()) {
                    val parent = ik.nodes[index].position
                    val transform = Matrix4f().translation(parent.x.toFloat(), parent.y.toFloat(), parent.z.toFloat())
                        .rotate(ik.segmentModelRotations[index])
                    segment.model.pieces.forEach { posed += it to Matrix4f(transform).mul(it.transform) }
                }
            }
            assertTrue(posed.size <= 175, "Display budget exceeded: ${posed.size}")
            assertTrue(posed.all { (_, matrix) -> matrix.isFinite && matrix.determinant() > 0f })
            // Diagnostic mesh uses the same transforms as the in-game renderer for visual review.
            if (size == CinderweaverSize.HUNTER) {
                val cubes = posed.map { (piece, matrix) ->
                    val vertices = (0..1).flatMap { x -> (0..1).flatMap { y -> (0..1).map { z ->
                        val v = matrix.transformPosition(Vector3f(x.toFloat(), y.toFloat(), z.toFloat()))
                        assertTrue(v.isFinite)
                        "[${v.x},${v.y},${v.z}]"
                    } } }.joinToString(",")
                    "{\"material\":\"${piece.block.material}\",\"vertices\":[$vertices]}"
                }
                val output = Path.of("build", "organic-spider-preview.json")
                Files.createDirectories(output.parent)
                Files.writeString(output, cubes.joinToString(",", "[", "]"))
            }
        }
    }
}
