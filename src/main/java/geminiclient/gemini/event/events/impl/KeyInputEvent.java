package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;

public record KeyInputEvent(int key, int scancode, int modifiers) implements Event {}
