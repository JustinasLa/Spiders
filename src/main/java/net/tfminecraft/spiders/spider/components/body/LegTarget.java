package net.tfminecraft.spiders.spider.components.body;

import org.bukkit.util.Vector;

public class LegTarget {
    private final Vector position;
    private final boolean isGrounded;
    private final int id;

    public LegTarget(Vector position, boolean isGrounded, int id) {
        this.position = position;
        this.isGrounded = isGrounded;
        this.id = id;
    }

    public Vector getPosition() {
        return position;
    }

    public boolean isGrounded() {
        return isGrounded;
    }

    public int getId() {
        return id;
    }
}
