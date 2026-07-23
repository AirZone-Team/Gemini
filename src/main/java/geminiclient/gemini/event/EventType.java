package geminiclient.gemini.event;

import geminiclient.gemini.event.impl.Event;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stable integer key for an event class. Resolve it once and reuse it at every
 * publish site so dispatch never needs a class-to-list map lookup.
 */
public final class EventType<T extends Event> {

    private static final AtomicInteger NEXT_ID = new AtomicInteger();
    private static final ClassValue<EventType<?>> TYPES = new ClassValue<>() {
        @Override
        protected EventType<?> computeValue(Class<?> type) {
            if (!Event.class.isAssignableFrom(type)) {
                throw new IllegalArgumentException(type.getName() + " is not an Event");
            }
            @SuppressWarnings("unchecked")
            Class<? extends Event> eventClass = (Class<? extends Event>) type;
            return new EventType<>(NEXT_ID.getAndIncrement(), eventClass);
        }
    };

    private final int id;
    private final Class<T> eventClass;

    private EventType(int id, Class<T> eventClass) {
        this.id = id;
        this.eventClass = eventClass;
    }

    @SuppressWarnings("unchecked")
    public static <T extends Event> EventType<T> of(Class<T> eventClass) {
        return (EventType<T>) TYPES.get(eventClass);
    }

    int id() {
        return id;
    }

    Class<T> eventClass() {
        return eventClass;
    }
}
