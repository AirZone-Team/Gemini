package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;
import net.minecraft.world.entity.Entity;

public record EntityRemoveEvent(Entity entity, boolean dead) implements Event {}
