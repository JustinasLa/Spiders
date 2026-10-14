package com.heledron.spideranimation.spider.components

import com.heledron.spideranimation.StaffAccess
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Arrow
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Entity
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.util.Vector
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyDouble
import org.mockito.Mockito.*
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProjectileHitTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() = BlockDataMocks.install()

        @JvmStatic
        @AfterAll
        fun restoreBukkit() = BlockDataMocks.restore()
    }

    /** An arrow the test moves itself, as the server would after plugin ticks. */
    private class FlyingArrow(world: World, var position: Vector, var velocity: Vector, shooter: Player?) {
        var teleported = false
        val mock: Arrow = mock(Arrow::class.java).also { arrow ->
            `when`(arrow.isValid).thenReturn(true)
            `when`(arrow.isInBlock).thenReturn(false)
            `when`(arrow.shooter).thenReturn(shooter)
            `when`(arrow.location).thenAnswer { position.toLocation(world) }
            `when`(arrow.velocity).thenAnswer { velocity.clone() }
            doAnswer { velocity = it.getArgument<Vector>(0).clone(); null }.`when`(arrow).velocity = any(Vector::class.java)
            `when`(arrow.teleport(any(Location::class.java))).thenAnswer {
                position = it.getArgument<Location>(0).toVector()
                teleported = true
                true
            }
        }

        fun step() { position.add(velocity) }
    }

    private fun player(staff: Boolean) = mock(Player::class.java).also {
        `when`(it.hasPermission(StaffAccess.PERMISSION)).thenReturn(staff)
    }

    private fun field() = Room { x, y, z -> x in -20..20 && z in -20..20 && y in -3..-1 }.world()

    /** A settled spider with the collision system running and [arrows] around it. */
    private fun standingSpider(scale: Double = .8, arrows: () -> List<Entity>): Run {
        val world = field()
        `when`(world.getNearbyEntities(any(Location::class.java), anyDouble(), anyDouble(), anyDouble())).thenAnswer { arrows() }
        val run = spawn(world, scale, gallop = false, Vector(.5, 0.0, .5), climbing = false)
        run.entity.addComponent(ProjectileHitDetector())
        setupProjectileCollisions(run.app)
        repeat(30) { run.app.update() }
        return run
    }

    private class Shot(val event: SpiderProjectileHitEvent?, val knock: Vector)

    /** Flies [arrow] for up to 10 ticks; returns the hit event and the spider's velocity change on the hit tick. */
    private fun Run.fly(arrow: FlyingArrow): Shot {
        var event: SpiderProjectileHitEvent? = null
        app.onEvent<SpiderProjectileHitEvent> { event = it }
        repeat(10) {
            val before = body.velocity.clone()
            app.update()
            if (event != null) return Shot(event, body.velocity.clone().subtract(before))
            arrow.step()
        }
        return Shot(null, Vector())
    }

    @Test
    fun `a staff arrow hits the body, bounces off and pushes the spider`() {
        var arrows = emptyList<Entity>()
        val run = standingSpider { arrows }
        val body = run.body.position.clone()
        val arrow = FlyingArrow(run.body.world, body.clone().add(Vector(0.0, 0.0, 6.0)), Vector(0.0, 0.0, -2.5), player(staff = true))
        arrows = listOf(arrow.mock)

        val shot = run.fly(arrow)
        val event = assertNotNull(shot.event, "The arrow flew through the body")
        assertTrue(event.forceful)
        assertTrue(arrow.teleported)
        // stopped at the front of the body, not inside or past it
        assertTrue(event.point.z > body.z && event.point.z < body.z + 1.5, "Hit at ${event.point}, body at $body")
        assertTrue(arrow.velocity.z > 0 && arrow.velocity.length() < .5, "Arrow should bounce back weakly: ${arrow.velocity}")
        assertTrue(shot.knock.z < -.05, "Spider was not pushed along the arrow: ${shot.knock}")
    }

    @Test
    fun `other players' arrows bounce off without pushing`() {
        var arrows = emptyList<Entity>()
        val run = standingSpider { arrows }
        val body = run.body.position.clone()
        val arrow = FlyingArrow(run.body.world, body.clone().add(Vector(0.0, 0.0, 6.0)), Vector(0.0, 0.0, -2.5), player(staff = false))
        arrows = listOf(arrow.mock)

        val shot = run.fly(arrow)
        val event = assertNotNull(shot.event, "The arrow flew through the body")
        assertFalse(event.forceful)
        assertTrue(arrow.velocity.z > 0, "Arrow should bounce back")
        assertTrue(shot.knock.length() < .03, "Spider was pushed: ${shot.knock}")
    }

    @Test
    fun `arrows hit the legs too`() {
        var arrows = emptyList<Entity>()
        val run = standingSpider { arrows }
        // the middle of a front leg's longest segment, shot from the side
        val leg = run.body.legs[0]
        val knee = leg.ik.nodes[1].position
        val foot = leg.ik.nodes[2].position
        val target = knee.clone().add(foot).multiply(.5)
        val side = if (target.x > run.body.position.x) 1.0 else -1.0
        val arrow = FlyingArrow(run.body.world, target.clone().add(Vector(side * 6, 0.0, 0.0)), Vector(-side * 2.5, 0.0, 0.0), null)
        arrows = listOf(arrow.mock)

        val event = assertNotNull(run.fly(arrow).event, "The arrow passed through the leg")
        assertTrue(event.point.distance(target) < .4, "Hit ${event.point}, leg at $target")
    }

    @Test
    fun `arrows over the spider fly on`() {
        var arrows = emptyList<Entity>()
        val run = standingSpider { arrows }
        val start = run.body.position.clone().add(Vector(0.0, 3.0, 6.0))
        val arrow = FlyingArrow(run.body.world, start, Vector(0.0, 0.0, -2.5), player(staff = true))
        arrows = listOf(arrow.mock)

        assertNull(run.fly(arrow).event)
        assertFalse(arrow.teleported)
    }

    @Test
    fun `every size is solid`() {
        for (scale in listOf(.25, .35, 1.5, 2.5)) {
            var arrows = emptyList<Entity>()
            val run = standingSpider(scale) { arrows }
            val body = run.body.position.clone()
            val arrow = FlyingArrow(run.body.world, body.clone().add(Vector(0.0, 0.0, 8.0)), Vector(0.0, 0.0, -2.5), null)
            arrows = listOf(arrow.mock)
            val event = assertNotNull(run.fly(arrow).event, "Scale $scale: the arrow flew through")
            assertTrue(event.point.distance(body) < 2.0 * scale + .3, "Scale $scale: hit ${event.point}, body $body")
        }
    }

    @Test
    fun `projectiles pass through the hidden seat entities`() {
        val app = com.heledron.spideranimation.utilities.ecs.ECS()
        val pig = mock(Pig::class.java)
        val marker = mock(ArmorStand::class.java)
        app.spawn(Mountable().apply { currentPig = pig; currentMarker = marker })
        val listener = SeatProjectileListener(app)

        for (seat in listOf(pig, marker)) {
            val event = ProjectileHitEvent(mock(Arrow::class.java), seat)
            listener.onHit(event)
            assertTrue(event.isCancelled, "Arrow stopped by $seat")
        }
        val other = ProjectileHitEvent(mock(Arrow::class.java), mock(Pig::class.java))
        listener.onHit(other)
        assertEquals(false, other.isCancelled)
    }
}
