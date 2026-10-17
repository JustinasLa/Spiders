package io.papermc.paper.plugin.lifecycle.event.types;

import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent;
import io.papermc.paper.plugin.lifecycle.event.LifecycleEventOwner;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Stands in for the server's lifecycle event types in unit tests, found through {@code META-INF/services}, so
 * {@code LifecycleEvents.COMMANDS} can initialise without a server. Each type is a mock that knows its name.
 * It lives in Paper's package because the provider interface is package-private.
 */
public final class TestLifecycleEventTypeProvider implements LifecycleEventTypeProvider {
    @Override
    @SuppressWarnings("unchecked")
    public <O extends LifecycleEventOwner, E extends LifecycleEvent> LifecycleEventType.Monitorable<O, E> monitor(String name, Class<? extends O> ownerType) {
        LifecycleEventType.Monitorable<O, E> type = mock(LifecycleEventType.Monitorable.class);
        when(type.name()).thenReturn(name);
        return type;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <O extends LifecycleEventOwner, E extends LifecycleEvent> LifecycleEventType.Prioritizable<O, E> prioritized(String name, Class<? extends O> ownerType) {
        LifecycleEventType.Prioritizable<O, E> type = mock(LifecycleEventType.Prioritizable.class);
        when(type.name()).thenReturn(name);
        return type;
    }

    @Override
    public TagEventTypeProvider tagProvider() {
        return mock(TagEventTypeProvider.class);
    }
}
