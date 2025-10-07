package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;

public class SlowDownEvent extends CancellableEvent {
    public float getFactor() {
        return factor;
    }

    public void setFactor(float factor) {
        this.factor = factor;
    }

    private float factor;
    public SlowDownEvent(float forward) {
        this.factor = forward;
    }
}
