package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.Event;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2fStack;

public record Render2DEvent(GuiGraphicsExtractor guiGraphics, Matrix3x2fStack stack) implements Event {}
