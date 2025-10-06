package geminiclient.gemini.events.impl;

import com.cubk.event.impl.Event;

public record KeyEvent(int key, int scancode, int modifiers) implements Event {}
