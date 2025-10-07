package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;

public record RenderRotationEvent(float yaw,float pitch) implements Event {}