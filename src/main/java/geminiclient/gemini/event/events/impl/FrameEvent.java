package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;

/**
 * Real-time frame update event — fires once per rendered frame, regardless of
 * the game tick rate. This is the frame-rate counterpart of {@link UpdateEvent},
 * which fires only once per game tick (20 TPS).
 *
 * <p>Use this event for smooth, frame-rate-independent animations and logic:
 * the real-time delta tells you how much time actually passed since the last
 * frame, even when the tick rate is lower than the frame rate.</p>
 *
 * @param partialTick        game-time partial ticks ({@code DeltaTracker#getGameTimeDeltaTicks()})
 * @param realTimeDeltaTicks real-time delta since the previous frame, measured in ticks
 *                           (20 ticks = 1 second); divide by 20 to get seconds
 */
@SuppressWarnings({"unused"})
public record FrameEvent(float partialTick, float realTimeDeltaTicks) implements Event {}
