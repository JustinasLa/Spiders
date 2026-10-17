package net.tfminecraft.spiders.spider.components;

import net.tfminecraft.spiders.spider.components.body.SpiderBody;
import net.tfminecraft.spiders.spider.configuration.SpiderOptions;
import net.tfminecraft.spiders.utilities.Core;
import net.tfminecraft.spiders.utilities.Disposable;
import net.tfminecraft.spiders.utilities.Pair;
import net.tfminecraft.spiders.utilities.ecs.ECS;
import net.tfminecraft.spiders.utilities.ecs.ECSEntity;
import net.tfminecraft.spiders.utilities.rendering.RenderEntityTracker;
import org.bukkit.World;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Pig;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static net.tfminecraft.spiders.spider.components.SimulationHarness.spawn;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SplayTest {
    private ComponentServer server;
    private final World world = mock(World.class);
    private final List<Object> handles = new ArrayList<>();

    private boolean hookAddedBefore;
    private List<BlockDisplay> debrisBefore;

    @BeforeEach
    void setUp() throws IllegalAccessException {
        // the server support puts Core's shutdown hooks back; Splay's own static state is put back here
        server = new ComponentServer();
        hookAddedBefore = hookAdded().getBoolean(null);
        debrisBefore = new ArrayList<>(debris());
    }

    @AfterEach
    void tearDown() throws IllegalAccessException {
        for (Object handle : handles) RenderEntityTracker.detach(handle);
        hookAdded().setBoolean(null, hookAddedBefore);
        debris().clear();
        debris().addAll(debrisBefore);
        server.close();
    }

    private static Field staticField(Class<?> owner, String name) {
        try {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Field hookAdded() {
        return staticField(Splay.class, "shutdownHookAdded");
    }

    @SuppressWarnings("unchecked")
    private static Set<BlockDisplay> debris() {
        try {
            return (Set<BlockDisplay>) staticField(Splay.class, "debris").get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Disposable> shutdownHooks() {
        try {
            return (List<Disposable>) staticField(Core.class, "closeableList").get(null);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A drawn block display whose transformation the test can read back. */
    private static final class Piece {
        final BlockDisplay entity = mock(BlockDisplay.class);
        Transformation transformation = new Transformation(new Vector3f(.1f, .2f, .3f), new AxisAngle4f(.5f, 0f, 1f, 0f), new Vector3f(1f), new AxisAngle4f());

        Piece(World world, Vector position) {
            when(entity.getLocation()).thenAnswer(call -> position.toLocation(world));
            when(entity.getTransformation()).thenAnswer(call -> Splay.clone(transformation));
            doAnswer(call -> {
                transformation = call.getArgument(0);
                return null;
            }).when(entity).setTransformation(any(Transformation.class));
        }
    }

    private <T extends Entity> T render(Object handle, T entity) {
        handles.add(handle);
        return RenderEntityTracker.put(handle, entity);
    }

    private Run spider() {
        return spawn(world, .8, false, new Vector(.5, 64.0, .5), false);
    }

    private List<Piece> drawn(Run run, int count) {
        List<Piece> pieces = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Piece piece = new Piece(world, run.body().getPosition().clone().add(new Vector(i % 3 - 1.0, .5, i / 3.0)));
            render(i % 2 == 0 ? new Pair<>(run.body(), i) : new Pair<>(new Pair<>(run.body(), "leg"), i), piece.entity);
            pieces.add(piece);
        }
        return pieces;
    }

    @Test
    @DisplayName("a killed spider falls apart: its pieces scatter, shrink, lie a while and vanish")
    void aKilledSpiderFallsApart() {
        Run run = spider();
        List<Piece> pieces = drawn(run, 9);
        BlockDisplay stranger = new Piece(world, new Vector()).entity;
        render(new Pair<>(new Object(), 0), stranger);
        Pig seat = mock(Pig.class);
        render(new Pair<>(run.body(), "mountable.pig"), seat);

        Splay.splay(run.entity());
        assertTrue(run.entity().getScheduledForRemoval());
        List<Entity> stillDrawn = RenderEntityTracker.getAll().stream().map(Pair::second).toList();
        assertTrue(stillDrawn.contains(stranger) && stillDrawn.contains(seat), "Only this spider's block displays come loose");
        for (Piece piece : pieces) assertFalse(stillDrawn.contains(piece.entity));

        server.advance(60);
        for (Piece piece : pieces) {
            Transformation end = piece.transformation;
            assertEquals(.35f, end.getScale().x, 1e-6f);
            assertTrue(new Quaternionf().equals(end.getLeftRotation(), 1e-6f), "Still turned: " + end.getLeftRotation());
            verify(piece.entity).setInterpolationDuration(1);
            Vector offset = piece.entity.getLocation().toVector().subtract(run.body().getPosition());
            float flung = new Vector3f(end.getTranslation()).add(offset.toVector3f()).length();
            assertTrue(flung >= 1f - 1e-4f && flung <= 3f + 1e-4f, "Flung " + flung);
            verify(piece.entity, never()).remove();
        }

        server.advance(Splay.DEBRIS_TICKS);
        for (Piece piece : pieces) verify(piece.entity).remove();
        verify(stranger, never()).remove();
        assertEquals(0, server.pending());
    }

    @Test
    @DisplayName("debris still lying when the plugin stops is cleared away")
    void debrisStillLyingWhenThePluginStopsIsClearedAway() throws IllegalAccessException {
        // as on a fresh start: no hook yet and nothing lying around (both are put back after the test)
        hookAdded().setBoolean(null, false);
        debris().clear();
        List<Disposable> shutdown = shutdownHooks();
        int before = shutdown.size();

        Run first = spider();
        List<Piece> firstPieces = drawn(first, 2);
        Splay.splay(first.entity());
        Run second = spider();
        List<Piece> secondPieces = drawn(second, 2);
        Splay.splay(second.entity());
        assertEquals(before + 1, shutdown.size(), "One hook for all debris");

        shutdown.get(shutdown.size() - 1).close();
        for (Piece piece : firstPieces) verify(piece.entity).remove();
        for (Piece piece : secondPieces) verify(piece.entity).remove();
        assertTrue(debris().isEmpty());
    }

    @Test
    @DisplayName("only a whole spider can be splayed")
    void onlyAWholeSpiderCanBeSplayed() {
        ECS app = new ECS();
        ECSEntity bodiless = app.spawn(mock(SpiderOptions.class));
        Splay.splay(bodiless);
        assertFalse(bodiless.getScheduledForRemoval());
        ECSEntity optionless = app.spawn(SpiderBody.create(world, new Vector()));
        Splay.splay(optionless);
        assertFalse(optionless.getScheduledForRemoval());
        assertEquals(0, server.pending());
    }

    @Test
    @DisplayName("a cloned transformation shares nothing with the original")
    void aClonedTransformationSharesNothing() {
        Transformation original = new Piece(world, new Vector()).transformation;
        Transformation copy = Splay.clone(original);
        assertEquals(original, copy);
        assertNotSame(original.getTranslation(), copy.getTranslation());
        assertNotSame(original.getLeftRotation(), copy.getLeftRotation());
        assertNotSame(original.getScale(), copy.getScale());
        assertNotSame(original.getRightRotation(), copy.getRightRotation());
    }
}
