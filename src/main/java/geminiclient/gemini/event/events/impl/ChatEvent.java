package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;

public class ChatEvent extends CancellableEvent {
    public String message;

    public ChatEvent(String message) {
        this.message = message;
    }
}
