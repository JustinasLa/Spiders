package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

public final class IKTarget3D {
    private final Vector position;
    private Quaternionf orientation;

    public IKTarget3D(Vector position, Quaternionf orientation) {
        this.position = position;
        this.orientation = orientation;
    }

    public IKTarget3D(Vector position) {
        this(position, null);
    }

    public Vector getPosition() {
        return position;
    }

    public Quaternionf getOrientation() {
        return orientation;
    }

    public void setOrientation(Quaternionf orientation) {
        this.orientation = orientation;
    }

    @Override
    public IKTarget3D clone() {
        return new IKTarget3D(position.clone(), orientation != null ? new Quaternionf(orientation) : null);
    }

    public void lerp(IKTarget3D other, double amount) {
        position.copy(Maths.lerp(position, other.position, amount));

        Quaternionf current = orientation;
        Quaternionf target = other.orientation;
        if (current != null && target != null) {
            current.slerp(target, (float) amount);
        } else if (target != null) {
            orientation = new Quaternionf(target);
        }
    }
}
