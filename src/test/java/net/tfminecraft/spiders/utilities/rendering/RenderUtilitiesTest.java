package net.tfminecraft.spiders.utilities.rendering;

import net.tfminecraft.spiders.testing.RenderTestSupport;
import net.tfminecraft.spiders.utilities.Geometry;
import net.tfminecraft.spiders.utilities.LineSegment;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class RenderUtilitiesTest {
    private final RenderTestSupport server = new RenderTestSupport();
    private final World world = server.world;

    @AfterEach
    void close() {
        server.close();
    }

    private BlockDisplay display() {
        return server.entity(BlockDisplay.class, new Location(world, 0.0, 0.0, 0.0));
    }

    @Test
    @DisplayName("a new transformation is applied and interpolated from now; the same one again is not resent")
    void interpolateTransformation() {
        BlockDisplay display = display();
        Transformation centred = Geometry.centredTransform(1f, 1f, 1f);

        RenderUtilities.interpolateTransform(display, centred);
        RenderUtilities.interpolateTransform(display, Geometry.centredTransform(1f, 1f, 1f));

        verify(display, times(1)).setTransformation(centred);
        verify(display, times(1)).setInterpolationDelay(0);
    }

    @Test
    @DisplayName("a matrix is applied once until it changes, and a copy is kept so later edits to it still count")
    void interpolateMatrix() {
        BlockDisplay display = display();
        Matrix4f matrix = new Matrix4f().translation(1f, 2f, 3f);

        RenderUtilities.interpolateTransform(display, matrix);
        RenderUtilities.interpolateTransform(display, new Matrix4f(matrix));
        verify(display, times(1)).setTransformationMatrix(any());
        verify(display, times(1)).setInterpolationDelay(0);
        assertEquals(new Vector3f(1f, 2f, 3f), display.getTransformation().getTranslation());

        matrix.translate(1f, 0f, 0f);
        RenderUtilities.interpolateTransform(display, matrix);
        verify(display, times(2)).setTransformationMatrix(any());
        verify(display, times(2)).setInterpolationDelay(0);
    }

    @Test
    @DisplayName("a matrix that leaves the display's transformation as it was starts no interpolation")
    void unchangedMatrixNotInterpolated() {
        BlockDisplay display = display();
        RenderUtilities.interpolateTransform(display, new Matrix4f());

        verify(display).setTransformationMatrix(new Matrix4f());
        verify(display, never()).setInterpolationDelay(anyInt());
    }

    @Test
    @DisplayName("setting a transformation directly forgets the cached matrix, so the matrix is applied again")
    void transformationForgetsCachedMatrix() {
        BlockDisplay display = display();
        Matrix4f matrix = new Matrix4f().translation(1f, 2f, 3f);

        RenderUtilities.interpolateTransform(display, matrix);
        RenderUtilities.interpolateTransform(display, Geometry.centredTransform(1f, 1f, 1f));
        RenderUtilities.interpolateTransform(display, matrix);

        verify(display, times(2)).setTransformationMatrix(matrix);
        assertEquals(new Vector3f(1f, 2f, 3f), display.getTransformation().getTranslation());
    }

    private record Factory<T extends Entity>(Class<T> type, Function<Object[], RenderEntity<T>> make) {
    }

    @Test
    @DisplayName("every block, text and item overload places the display and keeps the given or no-op callbacks")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void entityOverloads() {
        Location location = new Location(world, 1.0, 2.0, 3.0);
        Vector position = new Vector(1.0, 2.0, 3.0);
        Consumer init = it -> {};
        Consumer update = it -> {};

        List<RenderEntity<?>> full = List.of(
            RenderUtilities.renderBlock(location, init, update),
            RenderUtilities.renderBlock(world, position, init, update),
            RenderUtilities.renderText(location, init, update),
            RenderUtilities.renderText(world, position, init, update),
            RenderUtilities.renderItem(location, init, update),
            RenderUtilities.renderItem(world, position, init, update)
        );
        List<Class<?>> types = List.of(BlockDisplay.class, BlockDisplay.class, TextDisplay.class, TextDisplay.class, ItemDisplay.class, ItemDisplay.class);
        for (int index = 0; index < full.size(); index++) {
            assertSame(types.get(index), full.get(index).getClazz());
            assertEquals(location, full.get(index).getLocation());
            assertSame(init, full.get(index).getInit());
            assertSame(update, full.get(index).getUpdate());
        }

        List<RenderEntity<?>> initOnly = List.of(
            RenderUtilities.renderBlock(location, init),
            RenderUtilities.renderBlock(world, position, init),
            RenderUtilities.renderText(location, init),
            RenderUtilities.renderText(world, position, init),
            RenderUtilities.renderItem(location, init),
            RenderUtilities.renderItem(world, position, init)
        );
        List<RenderEntity<?>> bare = List.of(
            RenderUtilities.renderBlock(location),
            RenderUtilities.renderBlock(world, position),
            RenderUtilities.renderText(location),
            RenderUtilities.renderText(world, position),
            RenderUtilities.renderItem(location),
            RenderUtilities.renderItem(world, position)
        );
        for (int index = 0; index < bare.size(); index++) {
            Display display = (Display) mock(types.get(index));
            RenderEntity initRender = initOnly.get(index);
            RenderEntity bareRender = bare.get(index);
            assertSame(types.get(index), initRender.getClazz());
            assertSame(types.get(index), bareRender.getClazz());
            assertEquals(location, initRender.getLocation());
            assertEquals(location, bareRender.getLocation());
            assertSame(init, initRender.getInit());
            initRender.getUpdate().accept(display);
            bareRender.getInit().accept(display);
            bareRender.getUpdate().accept(display);
            verifyNoInteractions(display);
        }
    }

    private Matrix4f lineMatrix(RenderEntity<BlockDisplay> line, Object handle) {
        line.submit(handle);
        ArgumentCaptor<Matrix4f> matrix = ArgumentCaptor.forClass(Matrix4f.class);
        verify(server.spawned(BlockDisplay.class).getLast()).setTransformationMatrix(matrix.capture());
        return matrix.getValue();
    }

    private static void assertClose(Vector3f expected, Vector3f actual) {
        assertTrue(expected.distance(actual) < 1e-5, () -> "expected " + expected + " but was " + actual);
    }

    @Test
    @DisplayName("a line is a block stretched from its first point along the line, thickness wide, with its up side up")
    void horizontalLine() {
        int[] calls = {0, 0};
        LineSegment segment = new LineSegment(new Vector(1.0, 2.0, 3.0), new Vector(3.0, 2.0, 3.0));
        Matrix4f matrix = lineMatrix(RenderUtilities.renderLine(world, segment, null, .2f, 4, it -> calls[0]++, it -> calls[1]++), "line");

        BlockDisplay display = server.spawned(BlockDisplay.class).getFirst();
        assertEquals(new Location(world, 1.0, 2.0, 3.0), display.getLocation());
        verify(display).setTeleportDuration(4);
        verify(display).setInterpolationDuration(4);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);

        assertClose(new Vector3f(2f, 0f, 0f), matrix.transformDirection(new Vector3f(0f, 0f, 1f)));
        assertClose(new Vector3f(0f, .2f, 0f), matrix.transformDirection(new Vector3f(0f, 1f, 0f)));
        // centred on the line across its width
        assertClose(new Vector3f(1f, 0f, 0f), matrix.transformPosition(new Vector3f(.5f, .5f, .5f)));
    }

    @Test
    @DisplayName("a vertical line, which has no horizontal part, uses forward as its up side")
    void verticalLine() {
        LineSegment segment = new LineSegment(new Vector(0.0, 0.0, 0.0), new Vector(0.0, 3.0, 0.0));
        Matrix4f matrix = lineMatrix(RenderUtilities.renderLine(world, segment), "line");

        assertClose(new Vector3f(0f, 3f, 0f), matrix.transformDirection(new Vector3f(0f, 0f, 1f)));
        assertClose(new Vector3f(0f, 0f, .1f), matrix.transformDirection(new Vector3f(0f, 1f, 0f)));
    }

    @Test
    @DisplayName("the shorter line overloads default to the given up, 0.1 thickness and 1 tick of interpolation")
    void lineOverloads() {
        LineSegment segment = new LineSegment(new Vector(0.0, 0.0, 0.0), new Vector(0.0, 0.0, 2.0));
        Vector right = new Vector(1.0, 0.0, 0.0);
        int[] inits = {0};

        Matrix4f withInit = lineMatrix(RenderUtilities.renderLine(world, segment, right, .3f, 5, it -> inits[0]++), 1);
        verify(server.spawned(BlockDisplay.class).getLast()).setInterpolationDuration(5);
        assertEquals(1, inits[0]);
        assertClose(new Vector3f(.3f, 0f, 0f), withInit.transformDirection(new Vector3f(0f, 1f, 0f)));

        lineMatrix(RenderUtilities.renderLine(world, segment, right, .3f, 6), 2);
        verify(server.spawned(BlockDisplay.class).getLast()).setInterpolationDuration(6);

        Matrix4f thick = lineMatrix(RenderUtilities.renderLine(world, segment, right, .4f), 3);
        verify(server.spawned(BlockDisplay.class).getLast()).setInterpolationDuration(1);
        assertClose(new Vector3f(.4f, 0f, 0f), thick.transformDirection(new Vector3f(0f, 1f, 0f)));

        Matrix4f defaults = lineMatrix(RenderUtilities.renderLine(world, segment, right), 4);
        verify(server.spawned(BlockDisplay.class).getLast()).setInterpolationDuration(1);
        assertClose(new Vector3f(.1f, 0f, 0f), defaults.transformDirection(new Vector3f(0f, 1f, 0f)));
        assertClose(new Vector3f(0f, 0f, 2f), defaults.transformDirection(new Vector3f(0f, 0f, 1f)));
    }
}
