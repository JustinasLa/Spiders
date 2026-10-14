package com.heledron.spideranimation.spider.components.body

import com.heledron.spideranimation.spider.configuration.LegPlan
import com.heledron.spideranimation.spider.configuration.SpiderOptions
import com.heledron.spideranimation.utilities.*
import com.heledron.spideranimation.utilities.ecs.ECS
import com.heledron.spideranimation.utilities.ecs.ECSEntity
import com.heledron.spideranimation.utilities.raycastGround
import com.heledron.spideranimation.utilities.resolveCollision
import com.heledron.spideranimation.utilities.maths.DOWN_VECTOR
import com.heledron.spideranimation.utilities.maths.UP_VECTOR
import com.heledron.spideranimation.utilities.maths.lerp
import com.heledron.spideranimation.utilities.maths.moveTowards
import com.heledron.spideranimation.utilities.maths.rotate
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor


class LegStepEvent(val entity: ECSEntity, val spider: SpiderBody, val leg: Leg)

class Leg(
    val ecs: ECS,
    val entity: ECSEntity,
    val spider: SpiderBody,
    val legPlan: LegPlan,
    val options: SpiderOptions,
) {
    // memo
    lateinit var triggerZone: Capsule; private set
    lateinit var comfortZone: Capsule; private set

    lateinit var restPosition: Vector; private set
    lateinit var lookAheadPosition: Vector; private set
    lateinit var scanLine: LineSegment; private set
    /** Surface normal the leg steps against: world up, or the body's up while climbing. */
    lateinit var surfaceUp: Vector; private set

    init {
        updateMemo()
    }

    // state
    var groundTarget = locateGroundTarget(); private set
    var target = groundTarget ?: strandedTarget()
    var endEffector = target.position.clone()
    var previousEndEffector = endEffector.clone()

    val ik = createSpiderLegIK(legPlan)

    var touchingGround = target.isGrounded; private set
    var isMoving = false; private set
    var timeSinceBeginMove = 0; private set
    var timeSinceStopMove = 0; private set

    private var stepStart = Vector(0, 0, 0)
    private var stepProgress = 0.0 // separate from timeSinceBeginMove since progress can soft-reset on re-target

    var isDisabled = false
    var isPrimary = false
    var canMove = false

    // utils
    val isOutsideTriggerZone: Boolean; get () { return !triggerZone.contains(endEffector) }
    val isUncomfortable: Boolean; get () { return !comfortZone.contains(endEffector) && !isSettledOnClimbingFoothold() }

    /** A climbing foot already on its best (reachable but awkward) foothold can't do better, so it mustn't stall the body. */
    private fun isSettledOnClimbingFoothold() =
        options.gait.grip && target.isGrounded && !isMoving && endEffector.distanceSquared(target.position) < 0.01

    fun isGrounded(): Boolean {
        return touchingGround && !isMoving && !isDisabled
    }

    fun updateMemo() {
        val posture = spider.posture(options.gait)
        val scanOrientation = options.gait.scanOrientation.get(spider)


        // rest position
        restPosition = legPlan.restPosition.clone()
        restPosition.add(UP_VECTOR.clone().multiply(-posture.bodyHeight))
        restPosition.rotate(scanOrientation).add(spider.position)
        surfaceUp = UP_VECTOR.rotate(scanOrientation)

        // lookahead
        lookAheadPosition = lookAheadPosition(restPosition, posture.triggerZoneRadius)

        // scan (from lookahead position)
        val upVector = UP_VECTOR.rotate(scanOrientation)
        val scanStartAxis = upVector.clone().multiply(posture.bodyHeight * 1.6)
        val scanAxis = upVector.clone().multiply(-posture.bodyHeight * 2.5)
        scanLine = LineSegment.fromOffset(lookAheadPosition.clone().add(scanStartAxis), scanAxis)

        // trigger/comfort zone capsules (from rest position; same axis as scan)
        val zoneStart = restPosition.clone().add(scanStartAxis)
        val zoneEnd = zoneStart.clone().add(scanAxis)
        triggerZone = Capsule(zoneStart, zoneEnd, posture.triggerZoneRadius)
        comfortZone = Capsule(zoneStart, zoneEnd, options.gait.comfortZoneRadius)
    }

    fun update() {
        updateMovement()
        ik.solve(spider.position, spider.orientation, endEffector)
    }

    private fun updateMovement() {
        previousEndEffector = endEffector.clone()

        timeSinceBeginMove += 1
        timeSinceStopMove += 1

        // update target
        val oldTargetPosition = target.position.clone()
        val ground = locateGroundTarget()
        groundTarget = ground

        if (isDisabled) {
            target = disabledTarget(ground?.position)
        } else if (spider.leapTicks > 0) {
            // mid-leap: legs reach to their pose under the flying body
            target = strandedTarget()
        } else {
            if (ground != null) target = ground

            if (!target.isGrounded || !canReach(target.position) || !canStandOn(target.position)) {
                target = strandedTarget()
            }
        }

        // reset step if target has changed
        val targetChanged = oldTargetPosition.distanceSquared(target.position) > 1e-6
        if (targetChanged) {
            softResetStep()
        }

        // inherit parent motion while free
        if (!isGrounded()) {
            applyBodyMotion(endEffector)
            if (isMoving) applyBodyMotion(stepStart)
        }

        var didStep = false
        if (isMoving) {
            didStep = updateMove()
        } else {
            canMove = options.gait.type.canMoveLeg(this)
            if (canMove) beginMove()
        }


        // resolve ground collision
        val scanOrientation = options.gait.scanOrientation.get(spider)
        val down = DOWN_VECTOR.rotate(scanOrientation)
        val collision = spider.world.resolveCollision(endEffector, down)
        if (collision != null) {
            // ignore collision if it would push the end effector further from the target
            // e.g. when grazing an obstacle
            val isFurtherFromTarget = collision.position.distance(target.position) > endEffector.distance(target.position)
            if (!isFurtherFromTarget) {
                didStep = true
                touchingGround = true
                endEffector.copy(collision.position)
            }
        }

        if (didStep) ecs.emit(LegStepEvent(entity = entity, spider = spider, leg = this))
    }

    private fun applyBodyMotion(point: Vector) {
        point.add(spider.velocity)
        rotateWithBody(point)
    }

    /** Follow the body's turn about the surface normal (yaw on flat ground). */
    private fun rotateWithBody(point: Vector) {
        val angle = spider.rotationalVelocity.dot(surfaceUp.toVector3f()).toDouble()
        if (angle == 0.0) return
        point.subtract(spider.position).rotateAroundAxis(surfaceUp, angle).add(spider.position)
    }

    /** Without grip a foot can only stand on an upward face; one left on a wall or ceiling lets go. */
    private fun canStandOn(point: Vector): Boolean {
        if (options.gait.grip) return true
        // upright spiders only ever find upward faces, so skip the probe
        if (UP_VECTOR.rotate(spider.orientation).y > UPRIGHT_MIN_UP) return true
        // the probe must start in air just above the foot and meet a face below it
        val above = point.clone().add(Vector(0.0, FLOOR_PROBE, 0.0))
        val hit = spider.world.raycastGround(above, DOWN_VECTOR, FLOOR_PROBE * 2) ?: return false
        return hit.hitPosition.distanceSquared(above) > (FLOOR_PROBE / 10) * (FLOOR_PROBE / 10)
    }

    /** Footholds must lie in the comfort zone; climbing legs may also reach any face within leg length. */
    private fun canReach(point: Vector): Boolean {
        if (comfortZone.contains(point)) return true
        if (!options.gait.grip) return false
        val hip = legPlan.attachmentPosition.clone().rotate(spider.orientation).add(spider.position)
        val reach = legPlan.segments.sumOf { it.length } * CLIMB_REACH_FRACTION
        return hip.distanceSquared(point) <= reach * reach
    }

    private fun beginMove() {
        isMoving = true
        timeSinceBeginMove = 0
        stepStart = endEffector.clone()
        stepProgress = 0.0
        touchingGround = false
    }

    private fun completeMove(): Boolean {
        isMoving = false
        timeSinceStopMove = 0
        endEffector.copy(target.position)
        touchingGround = touchingGround()
        return touchingGround
    }

    /** Parabolic height envelope: 0 at ends, 1 at mid-step. */
    private fun stepLiftFactor(t: Double) = 4.0 * t * (1.0 - t)

    private fun sampleStepPosition(t: Double): Vector {
        val position = Vector(
            stepStart.x.lerp(target.position.x, t),
            stepStart.y.lerp(target.position.y, t),
            stepStart.z.lerp(target.position.z, t),
        )
        position.add(surfaceUp.clone().multiply(options.gait.legLiftHeight * stepLiftFactor(t)))
        return position
    }

    private fun updateMove(): Boolean {
		val gait = options.gait
    	
        val distance = planarDistance(stepStart, target.position).coerceAtLeast(1e-4)
        stepProgress = stepProgress.moveTowards(1.0, gait.legMoveSpeed / distance)

        endEffector.copy(sampleStepPosition(stepProgress))

        if (stepProgress >= 1.0) {
            return completeMove()
        }

        return false
    }

    private fun softResetStep() {
        stepStart = endEffector.clone()
        stepProgress = 0.0
    }

    private fun touchingGround(): Boolean {
        val scanOrientation = options.gait.scanOrientation.get(spider)
        return spider.world.raycastGround(endEffector, DOWN_VECTOR.rotate(scanOrientation), 0.001) != null
    }

    private fun lookAheadPosition(restPosition: Vector, triggerZoneRadius: Double): Vector {
        if (!spider.isWalking) return restPosition

        val direction = if (spider.velocity.isZero) spider.forwardDirection() else spider.velocity.clone().normalize()

        val lookAhead = direction.multiply(triggerZoneRadius * options.gait.legLookAheadFraction).add(restPosition)
        rotateWithBody(lookAhead)
        return lookAhead
    }


    fun locateGroundTarget(): LegTarget? {
        if (!options.gait.grip) return locateScanTarget()

        // Climbing: a face between the body and the foothold (wall or ceiling ahead) comes first,
        // then the normal scan, then the face around a convex edge.
        return locateObstructedTarget() ?: locateScanTarget() ?: locateWrappedTarget()
    }

    private fun locateScanTarget(): LegTarget? {
        val up = surfaceUp
        val scanStartPosition = scanLine.point1
        val scanVector = scanLine.vector()
        val scanLength = scanVector.length()

        // alternative candidates snap to block edges along the two axes lying in the surface
        val (axisA, axisB) = surfaceAxes(up)

        var id = 0
        val world = spider.world
        fun rayCast(a: Double, b: Double): LegTarget? {
            id += 1

            val start = scanStartPosition.clone().withAxis(axisA, a).withAxis(axisB, b)
            val hit = world.raycastGround(start, scanVector, scanLength) ?: return null

            // a climbing scan that starts inside a wall is not a foothold
            if (options.gait.grip && hit.hitPosition.distanceSquared(start) < 1e-6) return null

            return LegTarget(position = hit.hitPosition, isGrounded = true, id = id)
        }

        val a = scanStartPosition.axis(axisA)
        val b = scanStartPosition.axis(axisB)

        val mainCandidate = rayCast(a, b)

        if (!options.gait.legScanAlternativeGround) return mainCandidate

        if (mainCandidate != null) {
            val height = mainCandidate.position.clone().subtract(lookAheadPosition).dot(up)
            if (height in -.24 .. 1.5) {
                return mainCandidate
            }
        }

        val margin = 2 / 16.0
        val na = floor(a) - margin
        val nb = floor(b) - margin
        val pb = ceil(b) + margin
        val pa = ceil(a) + margin

        val candidates = listOf(
            rayCast(na, nb), rayCast(na, b), rayCast(na, pb),
            rayCast(a, nb),  mainCandidate,  rayCast(a, pb),
            rayCast(pa, nb), rayCast(pa, b), rayCast(pa, pb),
        )

        val preferredPosition = lookAheadPosition.clone()

        val frontBlock = lookAheadPosition.clone().add(spider.forwardDirection()).toLocation(world).block
        if (!frontBlock.isPassable) preferredPosition.add(up.clone().multiply(options.gait.legScanHeightBias))

        val best = candidates
            .filterNotNull()
            .minByOrNull { it.position.distanceSquared(preferredPosition) }

        if (best != null && !canReach(best.position)) {
            return null
        }

        return best
    }

    /** A wall or ceiling face crossing the line from the body to the desired foothold. */
    private fun locateObstructedTarget(): LegTarget? {
        val toFoothold = lookAheadPosition.clone().subtract(spider.position)
        val distance = toFoothold.length()
        if (distance < 1e-6) return null

        val hit = spider.world.raycastGround(spider.position, toFoothold, distance * OBSTRUCTION_FRACTION) ?: return null
        if (hit.hitPosition.distanceSquared(spider.position) < 1e-6) return null
        // the surface already underfoot is left to the normal scan
        val normal = hit.hitBlockFace?.direction ?: return null
        if (normal.dot(surfaceUp) > SAME_SURFACE_MIN_DOT) return null
        if (!canReach(hit.hitPosition)) return null

        return LegTarget(position = hit.hitPosition, isGrounded = true, id = OBSTRUCTED_TARGET_ID)
    }

    /** Past a convex edge the scan misses, so look back under the body for the face around the corner. */
    private fun locateWrappedTarget(): LegTarget? {
        // from the end of the scan, back towards the body parallel to the surface it stands on
        val start = scanLine.point2
        val toBody = planarOffset(start, spider.position)
        val distance = toBody.length()
        if (distance < 1e-6) return null

        val hit = spider.world.raycastGround(start, toBody, distance) ?: return null
        if (hit.hitPosition.distanceSquared(start) < 1e-6) return null
        if (!canReach(hit.hitPosition)) return null

        return LegTarget(position = hit.hitPosition, isGrounded = true, id = WRAPPED_TARGET_ID)
    }

    /** Offset from [from] to [to] along the surface, with the component along its normal removed. */
    private fun planarOffset(from: Vector, to: Vector): Vector {
        val offset = to.clone().subtract(from)
        return offset.subtract(surfaceUp.clone().multiply(offset.dot(surfaceUp)))
    }

    private fun planarDistance(from: Vector, to: Vector) = planarOffset(from, to).length()

    private fun strandedTarget(): LegTarget {
        return LegTarget(position = lookAheadPosition.clone(), isGrounded = false, id = -1)
    }

    private fun disabledTarget(groundPosition: Vector?): LegTarget {
        val posture = spider.posture(options.gait)
        val upVector = UP_VECTOR.rotate(spider.orientation)

        val target = strandedTarget()
        target.position.add(upVector.clone().multiply(posture.bodyHeight * .5))

        // keep the raised foot clear of the surface it stood on
        if (groundPosition != null) {
            val minHeight = groundPosition.dot(surfaceUp) + posture.bodyHeight * .1
            val height = target.position.dot(surfaceUp)
            if (height < minHeight) target.position.add(surfaceUp.clone().multiply(minHeight - height))
        }

        return target
    }

    private companion object {
        const val OBSTRUCTION_FRACTION = .9
        const val SAME_SURFACE_MIN_DOT = .7
        const val UPRIGHT_MIN_UP = .7
        const val FLOOR_PROBE = .01
        const val CLIMB_REACH_FRACTION = .95
        const val OBSTRUCTED_TARGET_ID = -2
        const val WRAPPED_TARGET_ID = -3

        /** The two block axes closest to lying in a surface with this normal (x and z on flat ground). */
        fun surfaceAxes(up: Vector): Pair<Int, Int> {
            val x = abs(up.x)
            val y = abs(up.y)
            val z = abs(up.z)
            return when {
                y >= x && y >= z -> 0 to 2
                x >= z -> 1 to 2
                else -> 0 to 1
            }
        }

        fun Vector.axis(axis: Int) = when (axis) { 0 -> x; 1 -> y; else -> z }

        fun Vector.withAxis(axis: Int, value: Double): Vector = when (axis) {
            0 -> setX(value)
            1 -> setY(value)
            else -> setZ(value)
        }
    }
}

class LegTarget(
    val position: Vector,
    val isGrounded: Boolean,
    val id: Int,
)
