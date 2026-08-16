package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;

/** 鼠标按键事件，action 与键盘一致：PRESS=1 / RELEASE=0 / REPEAT=2。 */
public record MouseButtonInputEvent(int button, int modifiers, int action) implements Event {}
