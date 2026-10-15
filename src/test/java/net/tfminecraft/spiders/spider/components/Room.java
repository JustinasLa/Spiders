package net.tfminecraft.spiders.spider.components;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.stream.DoubleStream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class Room {
    @FunctionalInterface
    interface Solidity {
        boolean isSolid(int x, int y, int z);
    }

    private final Solidity isSolid;

    Room(Solidity isSolid) {
        this.isSolid = isSolid;
    }

    Solidity getIsSolid() {
        return isSolid;
    }

    World world() {
        World world = mock(World.class);
        when(world.getBlockAt(any(Location.class))).thenAnswer(call -> block(call.getArgument(0)));
        when(world.rayTraceBlocks(any(Location.class), any(Vector.class), anyDouble(),
            eq(FluidCollisionMode.NEVER), eq(true))).thenAnswer(call ->
            rayTrace(call.<Location>getArgument(0).toVector(), call.getArgument(1), call.<Double>getArgument(2)));
        return world;
    }

    private Block block(Location location) {
        Block block = mock(Block.class);
        boolean solid = isSolid.isSolid((int) Math.floor(location.getX()), (int) Math.floor(location.getY()), (int) Math.floor(location.getZ()));
        when(block.isPassable()).thenReturn(!solid);
        return block;
    }

    /** Mirrors vanilla clipping, including a hit at the start when it begins inside a block. */
    RayTraceResult rayTrace(Vector start, Vector direction, double maxDistance) {
        assertTrue(DoubleStream.of(start.getX(), start.getY(), start.getZ(), direction.getX(), direction.getY(), direction.getZ()).allMatch(Double::isFinite));
        Vector dir = direction.clone().normalize();
        if (isSolid.isSolid((int) Math.floor(start.getX()), (int) Math.floor(start.getY()), (int) Math.floor(start.getZ()))) {
            return new RayTraceResult(start.clone(), faceAgainst(dir));
        }
        double step = 0.01;
        double travelled = 0.0;
        Vector previous = start.clone();
        while (travelled < maxDistance) {
            travelled = Math.min(travelled + step, maxDistance);
            Vector point = start.clone().add(dir.clone().multiply(travelled));
            int bx = (int) Math.floor(point.getX());
            int by = (int) Math.floor(point.getY());
            int bz = (int) Math.floor(point.getZ());
            if (isSolid.isSolid(bx, by, bz)) return entry(previous, dir, bx, by, bz);
            previous = point;
        }
        return null;
    }

    private RayTraceResult entry(Vector from, Vector dir, int bx, int by, int bz) {
        Entry e = new Entry(from, dir, bx, by, bz);
        if (dir.getX() > 0) e.test(0, bx, BlockFace.WEST); else e.test(0, bx + 1.0, BlockFace.EAST);
        if (dir.getY() > 0) e.test(1, by, BlockFace.DOWN); else e.test(1, by + 1.0, BlockFace.UP);
        if (dir.getZ() > 0) e.test(2, bz, BlockFace.NORTH); else e.test(2, bz + 1.0, BlockFace.SOUTH);
        if (e.best == Double.MAX_VALUE) e.best = 0.0;
        return new RayTraceResult(from.clone().add(dir.clone().multiply(e.best)), e.face);
    }

    private static final class Entry {
        final Vector from;
        final Vector dir;
        final int bx;
        final int by;
        final int bz;
        double best = Double.MAX_VALUE;
        BlockFace face = BlockFace.UP;

        Entry(Vector from, Vector dir, int bx, int by, int bz) {
            this.from = from;
            this.dir = dir;
            this.bx = bx;
            this.by = by;
            this.bz = bz;
        }

        void test(int axis, double plane, BlockFace hitFace) {
            double d = switch (axis) { case 0 -> dir.getX(); case 1 -> dir.getY(); default -> dir.getZ(); };
            if (Math.abs(d) < 1e-12) return;
            double o = switch (axis) { case 0 -> from.getX(); case 1 -> from.getY(); default -> from.getZ(); };
            double t = (plane - o) / d;
            if (t < -1e-9 || t > best) return;
            Vector p = from.clone().add(dir.clone().multiply(t));
            boolean inside = (axis == 0 || (p.getX() >= bx - 1e-6 && p.getX() <= bx + 1 + 1e-6)) &&
                (axis == 1 || (p.getY() >= by - 1e-6 && p.getY() <= by + 1 + 1e-6)) &&
                (axis == 2 || (p.getZ() >= bz - 1e-6 && p.getZ() <= bz + 1 + 1e-6));
            if (inside) {
                best = t;
                face = hitFace;
            }
        }
    }

    private BlockFace faceAgainst(Vector dir) {
        double ax = Math.abs(dir.getX());
        double ay = Math.abs(dir.getY());
        double az = Math.abs(dir.getZ());
        if (ay >= ax && ay >= az) return dir.getY() > 0 ? BlockFace.DOWN : BlockFace.UP;
        if (ax >= az) return dir.getX() > 0 ? BlockFace.WEST : BlockFace.EAST;
        return dir.getZ() > 0 ? BlockFace.NORTH : BlockFace.SOUTH;
    }
}
