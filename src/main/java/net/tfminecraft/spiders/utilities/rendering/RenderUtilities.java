package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.utilities.LineSegment;
import net.tfminecraft.spiders.utilities.maths.Maths;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;

import java.util.Objects;
import java.util.function.Consumer;

public final class RenderUtilities {
    private RenderUtilities() {
    }


    public static void interpolateTransform(Display display, Transformation transformation) {
        if (Objects.equals(display.getTransformation(), transformation)) return;
        display.setTransformation(transformation);
        display.setInterpolationDelay(0);
    }

    public static void interpolateTransform(Display display, Matrix4f matrix) {
        Transformation oldTransformation = display.getTransformation();
        display.setTransformationMatrix(matrix);

        if (Objects.equals(oldTransformation, display.getTransformation())) return;
        display.setInterpolationDelay(0);
    }


    public static RenderEntity<BlockDisplay> renderBlock(
        Location location,
        Consumer<BlockDisplay> init,
        Consumer<BlockDisplay> update
    ) {
        return new RenderEntity<>(
            BlockDisplay.class,
            location,
            init,
            update
        );
    }

    public static RenderEntity<BlockDisplay> renderBlock(Location location, Consumer<BlockDisplay> init) {
        return renderBlock(location, init, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderBlock(Location location) {
        return renderBlock(location, it -> {}, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderBlock(
        World world,
        Vector position,
        Consumer<BlockDisplay> init,
        Consumer<BlockDisplay> update
    ) {
        return new RenderEntity<>(
            BlockDisplay.class,
            position.toLocation(world),
            init,
            update
        );
    }

    public static RenderEntity<BlockDisplay> renderBlock(World world, Vector position, Consumer<BlockDisplay> init) {
        return renderBlock(world, position, init, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderBlock(World world, Vector position) {
        return renderBlock(world, position, it -> {}, it -> {});
    }

    public static RenderEntity<TextDisplay> renderText(
        Location location,
        Consumer<TextDisplay> init,
        Consumer<TextDisplay> update
    ) {
        return new RenderEntity<>(
            TextDisplay.class,
            location,
            init,
            update
        );
    }

    public static RenderEntity<TextDisplay> renderText(Location location, Consumer<TextDisplay> init) {
        return renderText(location, init, it -> {});
    }

    public static RenderEntity<TextDisplay> renderText(Location location) {
        return renderText(location, it -> {}, it -> {});
    }

    public static RenderEntity<TextDisplay> renderText(
        World world,
        Vector position,
        Consumer<TextDisplay> init,
        Consumer<TextDisplay> update
    ) {
        return renderText(
            position.toLocation(world),
            init,
            update
        );
    }

    public static RenderEntity<TextDisplay> renderText(World world, Vector position, Consumer<TextDisplay> init) {
        return renderText(world, position, init, it -> {});
    }

    public static RenderEntity<TextDisplay> renderText(World world, Vector position) {
        return renderText(world, position, it -> {}, it -> {});
    }


    public static RenderEntity<ItemDisplay> renderItem(
        Location location,
        Consumer<ItemDisplay> init,
        Consumer<ItemDisplay> update
    ) {
        return new RenderEntity<>(
            ItemDisplay.class,
            location,
            init,
            update
        );
    }

    public static RenderEntity<ItemDisplay> renderItem(Location location, Consumer<ItemDisplay> init) {
        return renderItem(location, init, it -> {});
    }

    public static RenderEntity<ItemDisplay> renderItem(Location location) {
        return renderItem(location, it -> {}, it -> {});
    }

    public static RenderEntity<ItemDisplay> renderItem(
        World world,
        Vector position,
        Consumer<ItemDisplay> init,
        Consumer<ItemDisplay> update
    ) {
        return renderItem(
            position.toLocation(world),
            init,
            update
        );
    }

    public static RenderEntity<ItemDisplay> renderItem(World world, Vector position, Consumer<ItemDisplay> init) {
        return renderItem(world, position, init, it -> {});
    }

    public static RenderEntity<ItemDisplay> renderItem(World world, Vector position) {
        return renderItem(world, position, it -> {}, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderLine(
        World world,
        LineSegment line,
        Vector upVector,
        float thickness,
        int interpolation,
        Consumer<BlockDisplay> init,
        Consumer<BlockDisplay> update
    ) {
        Vector up = upVector != null ? upVector : upVector(line.vector());
        return renderBlock(
            world,
            line.getPoint1(),
            it -> {
                it.setTeleportDuration(interpolation);
                it.setInterpolationDuration(interpolation);
                init.accept(it);
            },
            it -> {
                Vector vector = line.vector();
                Matrix4f matrix = new Matrix4f().rotateTowards(vector.toVector3f(), up.toVector3f())
                    .translate(-thickness / 2, -thickness / 2, 0f)
                    .scale(thickness, thickness, (float) vector.length());

                interpolateTransform(it, matrix);
                update.accept(it);
            }
        );
    }

    public static RenderEntity<BlockDisplay> renderLine(World world, LineSegment line, Vector upVector, float thickness, int interpolation, Consumer<BlockDisplay> init) {
        return renderLine(world, line, upVector, thickness, interpolation, init, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderLine(World world, LineSegment line, Vector upVector, float thickness, int interpolation) {
        return renderLine(world, line, upVector, thickness, interpolation, it -> {}, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderLine(World world, LineSegment line, Vector upVector, float thickness) {
        return renderLine(world, line, upVector, thickness, 1, it -> {}, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderLine(World world, LineSegment line, Vector upVector) {
        return renderLine(world, line, upVector, .1f, 1, it -> {}, it -> {});
    }

    public static RenderEntity<BlockDisplay> renderLine(World world, LineSegment line) {
        return renderLine(world, line, null, .1f, 1, it -> {}, it -> {});
    }

    private static Vector upVector(Vector vector) {
        return vector.getX() + vector.getZ() != 0.0 ? Maths.upVector() : Maths.forwardVector();
    }
}
