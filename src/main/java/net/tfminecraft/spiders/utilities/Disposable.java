package net.tfminecraft.spiders.utilities;

import java.io.Closeable;

/** A {@link Closeable} that never throws, for scheduled tasks, listeners and shutdown hooks. */
@FunctionalInterface
public interface Disposable extends Closeable {
    @Override
    void close();
}
