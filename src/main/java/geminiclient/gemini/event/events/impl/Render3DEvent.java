package geminiclient.gemini.event.events.impl;

import com.mojang.blaze3d.vertex.PoseStack;
import geminiclient.gemini.event.impl.Event;

public record Render3DEvent(PoseStack poseStack, float partialTick) implements Event {}
