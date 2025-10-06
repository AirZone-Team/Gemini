package geminiclient.gemini.events.impl;

import com.cubk.event.impl.CancellableEvent;

public class ChatEvent extends CancellableEvent {
    public String message;

    public ChatEvent(String message) {
        this.message = message;
    }
}
