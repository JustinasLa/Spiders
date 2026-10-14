package net.tfminecraft.spiders.spider.components

import io.papermc.paper.event.player.PlayerArmSwingEvent
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.ArmorStand
import org.bukkit.entity.Arrow
import org.bukkit.entity.Entity
import org.bukkit.entity.Pig
import org.bukkit.entity.Player
import org.bukkit.entity.Projectile
import org.bukkit.entity.Skeleton
import org.bukkit.entity.Snowball
import org.bukkit.entity.Trident
import org.bukkit.entity.WindCharge
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.projectiles.ProjectileSource
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

class HitDetectorTest {
    companion object {
        @JvmStatic
        @BeforeAll
        fun installBlockDataFactory() = BlockDataMocks.install()

        @JvmStatic
        @AfterAll
        fun restoreBukkit() = BlockDataMocks.restore()
    }

    /** A projectile the test moves itself, as the server would after plugin ticks. */
    private class Flying<T : Projectile>(type: Class<T>, world: World, var position: Vector, var velocity: Vector, shooter: ProjectileSource?) {
        var teleported = false
        val mock: T = mock(type).also { projectile ->
            `when`(projectile.isValid).thenReturn(true)
            `when`(projectile.shooter).thenReturn(shooter)
            `when`(projectile.location).thenAnswer { position.toLocation(world) }
            `when`(projectile.velocity).thenAnswer { velocity.clone() }
            doAnswer { velocity = it.getArgument<Vector>(0).clone(); null }.`when`(projectile).velocity = any(Vector::class.java)
            `when`(projectile.teleport(any(Location::class.java))).thenAnswer {
                position = it.getArgument<Location>(0).toVector()
                teleported = true
                true
            }
        }

        fun step() { position.add(velocity) }
    }

    private fun field() = Room { x, y, z -> x in -20..20 && z in -20..20 && y in -3..-1 }.world()

    /** A settled spider with hit detection running and [nearby] entities around it. */
    private fun standingSpider(scale: Double = .8, world: World = field(), nearby: () -> List<Entity> = { emptyList() }): Run {
        `when`(world.getNearbyEntities(any(Location::class.java), anyDouble(), anyDouble(), anyDouble())).thenAnswer { nearby() }
        val run = spawn(world, scale, gallop = false, Vector(.5, 0.0, .5), climbing = false)
        run.entity.addComponent(HitDetector())
        setupProjectileCollisions(run.app)
        repeat(30) { run.app.update() }
        return run
    }

    private class Shot(val event: SpiderHitEvent?, val knock: Vector)

    private fun Run.collectHits(): MutableList<SpiderHitEvent> = mutableListOf<SpiderHitEvent>().also { hits -> app.onEvent<SpiderHitEvent> { hits += it } }

    /** Flies [projectile] for up to 10 ticks; returns the hit and the spider's velocity change on the hit tick. */
    private fun Run.fly(projectile: Flying<*>): Shot {
        val hits = collectHits()
        repeat(10) {
            val before = body.velocity.clone()
            app.update()
            if (hits.isNotEmpty()) return Shot(hits.first(), body.velocity.clone().subtract(before))
            projectile.step()
        }
        return Shot(null, Vector())
    }

    /** A projectile coming at the middle of the body from 6 blocks along +z. */
    private fun <T : Projectile> Run.shoot(type: Class<T>, shooter: ProjectileSource?, height: Double = 0.0): Flying<T> =
        Flying(type, body.world, body.position.clone().add(Vector(0.0, height, 6.0)), Vector(0.0, 0.0, -2.5), shooter)

    @Test
    fun `any archer's arrow hits the body, bounces off and pushes the spider`() {
        val shooters = listOf(mock(Player::class.java), mock(Skeleton::class.java), null)
        for (shooter in shooters) {
            var nearby = emptyList<Entity>()
            val run = standingSpider { nearby }
            val arrow = run.shoot(Arrow::class.java, shooter)
            nearby = listOf(arrow.mock)

            val shot = run.fly(arrow)
            val event = assertNotNull(shot.event, "The arrow from $shooter flew through the body")
            assertTrue(event.forceful)
            assertTrue(arrow.teleported)
            val body = run.body.position
            assertTrue(event.point.z > body.z && event.point.z < body.z + 1.5, "Hit at ${event.point}, body at $body")
            assertTrue(arrow.velocity.z > 0 && arrow.velocity.length() < .5, "Arrow should bounce back weakly: ${arrow.velocity}")
            assertTrue(shot.knock.z < -.05, "Arrow from $shooter did not push the spider: ${shot.knock}")
        }
    }

    @Test
    fun `a weak arrow glances off without pushing`() {
        var nearby = emptyList<Entity>()
        val run = standingSpider { nearby }
        val arrow = Flying(Arrow::class.java, run.body.world, run.body.position.clone().add(Vector(0.0, 0.0, 2.0)), Vector(0.0, 0.0, -.6), null)
        nearby = listOf(arrow.mock)

        val shot = run.fly(arrow)
        assertFalse(assertNotNull(shot.event).forceful)
        assertTrue(shot.knock.length() < .03, "Spider was pushed: ${shot.knock}")
    }

    @Test
    fun `a trident pushes harder than an arrow`() {
        var nearby = emptyList<Entity>()
        val tridentRun = standingSpider { nearby }
        val trident = tridentRun.shoot(Trident::class.java, mock(Player::class.java))
        nearby = listOf(trident.mock)
        val tridentKnock = -tridentRun.fly(trident).knock.z

        var arrows = emptyList<Entity>()
        val arrowRun = standingSpider { arrows }
        val arrow = arrowRun.shoot(Arrow::class.java, mock(Player::class.java))
        arrows = listOf(arrow.mock)
        val arrowKnock = -arrowRun.fly(arrow).knock.z

        assertTrue(tridentKnock > 2 * arrowKnock, "Trident $tridentKnock vs arrow $arrowKnock")
        verify(trident.mock).setHasDealtDamage(true)
    }

    @Test
    fun `snowballs break on the spider with a light thump`() {
        var nearby = emptyList<Entity>()
        val run = standingSpider { nearby }
        val snowball = run.shoot(Snowball::class.java, mock(Player::class.java))
        nearby = listOf(snowball.mock)

        val shot = run.fly(snowball)
        assertTrue(assertNotNull(shot.event).forceful)
        verify(snowball.mock).remove()
        assertTrue(shot.knock.z < 0 && shot.knock.z > -.05, "Snowball push ${shot.knock}")
    }

    @Test
    fun `wind charges burst on the spider and shove it`() {
        var nearby = emptyList<Entity>()
        val run = standingSpider { nearby }
        val charge = run.shoot(WindCharge::class.java, mock(Player::class.java))
        nearby = listOf(charge.mock)

        val shot = run.fly(charge)
        assertNotNull(shot.event)
        verify(charge.mock).explode()
        assertTrue(shot.knock.length() > .3, "Wind charge shove ${shot.knock}")
    }

    @Test
    fun `a rider's own shots pass through their mount`() {
        var nearby = emptyList<Entity>()
        val run = standingSpider { nearby }
        val rider = mock(Player::class.java)
        val marker = mock(ArmorStand::class.java)
        `when`(marker.passengers).thenReturn(listOf(rider))
        run.entity.addComponent(Mountable().apply { currentMarker = marker })
        val arrow = run.shoot(Arrow::class.java, rider)
        nearby = listOf(arrow.mock)

        assertNull(run.fly(arrow).event)
    }

    @Test
    fun `arrows hit the legs too`() {
        var nearby = emptyList<Entity>()
        val run = standingSpider { nearby }
        val leg = run.body.legs[0]
        val target = leg.ik.nodes[1].position.clone().add(leg.ik.nodes[2].position).multiply(.5)
        val side = if (target.x > run.body.position.x) 1.0 else -1.0
        val arrow = Flying(Arrow::class.java, run.body.world, target.clone().add(Vector(side * 6, 0.0, 0.0)), Vector(-side * 2.5, 0.0, 0.0), null)
        nearby = listOf(arrow.mock)

        val event = assertNotNull(run.fly(arrow).event, "The arrow passed through the leg")
        assertTrue(event.point.distance(target) < .4, "Hit ${event.point}, leg at $target")
    }

    @Test
    fun `arrows over the spider fly on`() {
        var nearby = emptyList<Entity>()
        val run = standingSpider { nearby }
        val arrow = run.shoot(Arrow::class.java, null, height = 3.0)
        nearby = listOf(arrow.mock)

        assertNull(run.fly(arrow).event)
        assertFalse(arrow.teleported)
    }

    @Test
    fun `every size is solid`() {
        for (scale in listOf(.25, .35, 1.5, 2.5)) {
            var nearby = emptyList<Entity>()
            val run = standingSpider(scale) { nearby }
            val body = run.body.position.clone()
            val arrow = Flying(Arrow::class.java, run.body.world, body.clone().add(Vector(0.0, 0.0, 8.0)), Vector(0.0, 0.0, -2.5), null)
            nearby = listOf(arrow.mock)
            val event = assertNotNull(run.fly(arrow).event, "Scale $scale: the arrow flew through")
            assertTrue(event.point.distance(body) < 2.0 * scale + .3, "Scale $scale: hit ${event.point}, body $body")
        }
    }

    @Test
    fun `projectiles pass through the hidden seat entities`() {
        val app = net.tfminecraft.spiders.utilities.ecs.ECS()
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

    // --- melee

    private fun fighter(world: World, eye: Vector, look: Vector, charge: Float = 1f) = mock(Player::class.java).also {
        `when`(it.world).thenReturn(world)
        `when`(it.gameMode).thenReturn(GameMode.SURVIVAL)
        `when`(it.attackCooldown).thenReturn(charge)
        `when`(it.eyeLocation).thenAnswer { eye.toLocation(world).setDirection(look) }
    }

    private fun Run.swing(player: Player): Pair<SpiderHitEvent?, Vector> {
        val hits = collectHits()
        val before = body.velocity.clone()
        MeleeListener(app) { 3.0 }.onSwing(PlayerArmSwingEvent(player, EquipmentSlot.HAND))
        return hits.firstOrNull() to body.velocity.clone().subtract(before)
    }

    @Test
    fun `a player's swing within reach hits and pushes the spider`() {
        val run = standingSpider()
        val eye = run.body.position.clone().add(Vector(0.0, .3, 3.2))
        val player = fighter(run.body.world, eye, Vector(0.0, -.1, -1.0))

        val (event, knock) = run.swing(player)
        assertNotNull(event, "Swing missed the spider")
        assertTrue(event.forceful)
        assertTrue(knock.z < -.05, "Swing did not push: $knock")
        verify(player).resetCooldown()
    }

    @Test
    fun `a weak swing pushes less and a spam swing does nothing`() {
        val strong = standingSpider()
        val strongKnock = strong.swing(fighter(strong.body.world, strong.body.position.clone().add(Vector(0.0, .3, 3.2)), Vector(0.0, -.1, -1.0))).second
        val weak = standingSpider()
        val weakKnock = weak.swing(fighter(weak.body.world, weak.body.position.clone().add(Vector(0.0, .3, 3.2)), Vector(0.0, -.1, -1.0), charge = .5f)).second
        assertTrue(-weakKnock.z < -strongKnock.z * .7, "Half charge $weakKnock vs full $strongKnock")

        val spam = standingSpider()
        val (event, _) = spam.swing(fighter(spam.body.world, spam.body.position.clone().add(Vector(0.0, .3, 3.2)), Vector(0.0, -.1, -1.0), charge = .1f))
        assertNull(event)
    }

    @Test
    fun `swings out of reach or into a wall miss`() {
        val run = standingSpider()
        val far = fighter(run.body.world, run.body.position.clone().add(Vector(0.0, .3, 6.0)), Vector(0.0, 0.0, -1.0))
        assertNull(run.swing(far).first, "Hit from 6 blocks with a reach of 3")

        // a wall between the player and the spider
        val walled = Room { x, y, z -> x in -20..20 && z in -20..20 && (y in -3..-1 || (z == 3 && y in 0..3)) }.world()
        val behind = standingSpider(world = walled)
        val player = fighter(walled, behind.body.position.clone().add(Vector(0.0, .3, 3.6)), Vector(0.0, -.05, -1.0))
        assertNull(behind.swing(player).first, "Hit through a wall")
    }

    @Test
    fun `attacking the hidden saddle pig counts as hitting the spider`() {
        val run = standingSpider()
        val pig = mock(Pig::class.java)
        `when`(pig.location).thenAnswer { run.body.position.clone().add(Vector(0.0, -.6, 0.0)).toLocation(run.body.world) }
        run.entity.addComponent(Mountable().apply { currentPig = pig })
        // looking straight down at the pig's top from above: the swing ray may miss the drawn shape
        val player = fighter(run.body.world, run.body.position.clone().add(Vector(0.0, 2.5, 1.0)), Vector(0.0, -1.0, 0.0))
        val hits = run.collectHits()

        val event = PrePlayerAttackEntityEvent(player, pig, true)
        MeleeListener(run.app) { 3.0 }.onAttackSeat(event)
        assertTrue(event.isCancelled, "The pig attack should be replaced by a spider hit")
        assertEquals(1, hits.size)
    }
}
