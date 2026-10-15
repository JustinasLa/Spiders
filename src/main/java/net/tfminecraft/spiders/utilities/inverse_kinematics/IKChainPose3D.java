package net.tfminecraft.spiders.utilities.inverse_kinematics;

import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;

import java.util.ArrayList;
import java.util.List;

public final class IKChainPose3D {
    private final Vector position;
    private final Quaternionf orientation;
    private final List<IKJointPose3D> segments;

    public IKChainPose3D(Vector position, Quaternionf orientation, List<IKJointPose3D> segments) {
        this.position = position;
        this.orientation = orientation;
        this.segments = segments;
    }

    public Vector getPosition() {
        return position;
    }

    public Quaternionf getOrientation() {
        return orientation;
    }

    public List<IKJointPose3D> getSegments() {
        return segments;
    }

    @Override
    public IKChainPose3D clone() {
        List<IKJointPose3D> clonedSegments = new ArrayList<>(segments.size());
        for (IKJointPose3D segment : segments) clonedSegments.add(segment.clone());

        return new IKChainPose3D(
            position.clone(),
            new Quaternionf(orientation),
            clonedSegments
        );
    }

    public void copy(IKChainPose3D other) {
        position.copy(other.position);
        orientation.set(other.orientation);

        for (int index = 0; index < segments.size(); index++) {
            segments.get(index).copy(other.segments.get(index));
        }
    }

    public void lerp(IKChainPose3D other, double amount) {
        double t = IKChain3D.coerceIn(amount, 0.0, 1.0);

        position.copy(Maths.lerp(position, other.position, t));
        orientation.slerp(other.orientation, (float) t);

        for (int index = 0; index < segments.size(); index++) {
            segments.get(index).lerpState(other.segments.get(index), t);
        }
    }
}
