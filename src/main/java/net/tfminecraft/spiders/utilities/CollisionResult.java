package net.tfminecraft.spiders.utilities;

import org.bukkit.util.Vector;

import java.util.Objects;

public final class CollisionResult {
    private final Vector position;
    private final Vector offset;
    private final Vector normal;

    public CollisionResult(Vector position, Vector offset, Vector normal) {
        this.position = position;
        this.offset = offset;
        this.normal = normal;
    }

    public Vector getPosition() {
        return position;
    }

    public Vector getOffset() {
        return offset;
    }

    public Vector getNormal() {
        return normal;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof CollisionResult that)) return false;
        return Objects.equals(position, that.position) && Objects.equals(offset, that.offset) && Objects.equals(normal, that.normal);
    }

    @Override
    public int hashCode() {
        int result = Objects.hashCode(position);
        result = 31 * result + Objects.hashCode(offset);
        result = 31 * result + Objects.hashCode(normal);
        return result;
    }

    @Override
    public String toString() {
        return "CollisionResult(position=" + position + ", offset=" + offset + ", normal=" + normal + ")";
    }
}
