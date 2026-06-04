package geminiclient.gemini.event.events.impl.moveFixEvent;

import geminiclient.gemini.event.impl.Event;

public class FallFlyingEvent implements Event {

    private float pitch;

    public FallFlyingEvent(float pitch) {
        this.pitch = pitch;
    }

    public void setPitch(float pitch) {
        this.pitch = pitch;
    }

    public float getPitch() {
        return this.pitch;
    }

}