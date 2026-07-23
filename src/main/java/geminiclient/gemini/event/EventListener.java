package geminiclient.gemini.event;

import geminiclient.gemini.event.impl.Event;

/** Runtime-generated direct call target used by {@link EventManager}. */
@FunctionalInterface
public interface EventListener {
    void invoke(Event event) throws Throwable;
}
