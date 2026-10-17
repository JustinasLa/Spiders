package net.tfminecraft.spiders.spider.components;

import org.mockito.MockedStatic;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntBinaryOperator;

import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.RETURNS_DEFAULTS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Loaded dice for {@link ThreadLocalRandom}, on this thread only, so the mimics' chance-driven choices
 * (twitches, cries, peeks, which sound haunts you) can be pinned down. Every roll is the low end of
 * its range unless a test says otherwise.
 */
final class MimicDice implements AutoCloseable {
    /** What {@code nextFloat()} rolls once {@link #floats} is empty. */
    float nextFloat = .5f;
    final Deque<Float> floats = new ArrayDeque<>();
    /** What {@code nextInt(bound)} rolls once {@link #ints} is empty (kept below the bound). */
    int nextInt = 0;
    final Deque<Integer> ints = new ArrayDeque<>();
    /** What {@code nextInt(origin, bound)} rolls. */
    IntBinaryOperator range = (origin, bound) -> origin;

    private final MockedStatic<ThreadLocalRandom> mocked;

    MimicDice() {
        ThreadLocalRandom random = mock(ThreadLocalRandom.class, call -> {
            Object[] args = call.getArguments();
            return switch (call.getMethod().getName()) {
                case "nextFloat" -> floats.isEmpty() ? nextFloat : floats.poll();
                case "nextInt" -> switch (args.length) {
                    case 1 -> Math.min((int) args[0] - 1, ints.isEmpty() ? nextInt : ints.poll());
                    case 2 -> range.applyAsInt((int) args[0], (int) args[1]);
                    default -> 0;
                };
                case "nextDouble" -> switch (args.length) {
                    case 2 -> (double) args[0];
                    default -> 0.0;
                };
                default -> RETURNS_DEFAULTS.answer(call);
            };
        });
        mocked = mockStatic(ThreadLocalRandom.class, CALLS_REAL_METHODS);
        mocked.when(ThreadLocalRandom::current).thenReturn(random);
    }

    @Override
    public void close() {
        mocked.close();
    }
}
