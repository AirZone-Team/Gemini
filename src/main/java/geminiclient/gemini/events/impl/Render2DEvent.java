package geminiclient.gemini.events.impl;

import com.cubk.event.impl.Event;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

public record Render2DEvent(GuiGraphics guiGraphics, Matrix3x2fStack stack) implements Event {}
