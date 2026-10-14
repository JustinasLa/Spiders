package net.tfminecraft.tfmcspiders.utilities.inverse_kinematics

import net.tfminecraft.tfmcspiders.utilities.maths.lerp
import org.bukkit.util.Vector
import org.joml.Quaternionf

class IKTarget3D(
    val position: Vector,
    var orientation: Quaternionf? = null,
) {
    fun clone() = IKTarget3D(position.clone(), orientation?.let { Quaternionf(it) })

    fun lerp(other: IKTarget3D, amount: Double) {
        position.copy(position.lerp(other.position, amount))

        val current = orientation
        val target = other.orientation
        when {
            current != null && target != null -> current.slerp(target, amount.toFloat())
            target != null -> orientation = Quaternionf(target)
        }
    }
}

typealias IKSolver3D = (chain: IKChain3D, pose: IKChainPose3D, target: IKTarget3D) -> Unit
