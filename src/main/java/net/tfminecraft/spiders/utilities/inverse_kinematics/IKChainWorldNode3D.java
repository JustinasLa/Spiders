package net.tfminecraft.spiders.utilities.inverse_kinematics;

import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public final class IKChainWorldNode3D {
    private final Vector position;
    private final Quaternionf worldRotation;
    private final Quaternionf localRotation;

    public IKChainWorldNode3D(Vector position, Quaternionf worldRotation, Quaternionf localRotation) {
        this.position = position;
        this.worldRotation = worldRotation;
        this.localRotation = localRotation;
    }

    public Vector getPosition() {
        return position;
    }

    public Quaternionf getWorldRotation() {
        return worldRotation;
    }

    public Quaternionf getLocalRotation() {
        return localRotation;
    }
}
