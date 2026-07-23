package com.mojang.blaze3d.opengl;

import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.platform.MacosUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.jtracy.Plot;
import com.mojang.jtracy.TracyClient;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.IntStream;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

@OnlyIn(Dist.CLIENT)
public class GlStateManager {
    private static final Plot PLOT_TEXTURES = TracyClient.createPlot("GPU Textures");
    private static int numTextures = 0;
    private static final Plot PLOT_BUFFERS = TracyClient.createPlot("GPU Buffers");
    private static int numBuffers = 0;
    private static final GlStateManager.BlendState[] BLEND = new GlStateManager.BlendState[8];
    private static final GlStateManager.DepthState DEPTH = new GlStateManager.DepthState();
    private static final GlStateManager.CullState CULL = new GlStateManager.CullState();
    private static final GlStateManager.PolygonOffsetState POLY_OFFSET = new GlStateManager.PolygonOffsetState();
    private static final GlStateManager.ColorLogicState COLOR_LOGIC = new GlStateManager.ColorLogicState();
    private static final GlStateManager.StencilState STENCIL = new GlStateManager.StencilState();
    private static final GlStateManager.ScissorState SCISSOR = new GlStateManager.ScissorState();
    private static int activeTexture;
    private static final int TEXTURE_COUNT = 12;
    private static final GlStateManager.TextureState[] TEXTURES = IntStream.range(0, 12)
        .mapToObj(i -> new GlStateManager.TextureState())
        .toArray(GlStateManager.TextureState[]::new);
    private static final @ColorTargetState.WriteMask int[] COLOR_MASK = new int[8];
    private static int readFbo;
    private static int writeFbo;

    public static void _disableScissorTest() {
        RenderSystem.assertOnRenderThread();
        SCISSOR.mode.disable();
    }

    public static void _enableScissorTest() {
        RenderSystem.assertOnRenderThread();
        SCISSOR.mode.enable();
    }

    public static void _scissorBox(int x, int y, int width, int height) {
        RenderSystem.assertOnRenderThread();
        GL33C.glScissor(x, y, width, height);
    }

    public static void _disableDepthTest() {
        RenderSystem.assertOnRenderThread();
        DEPTH.mode.disable();
    }

    public static void _enableDepthTest() {
        RenderSystem.assertOnRenderThread();
        DEPTH.mode.enable();
    }

    public static void _depthFunc(int func) {
        RenderSystem.assertOnRenderThread();
        if (func != DEPTH.func) {
            DEPTH.func = func;
            GL33C.glDepthFunc(func);
        }
    }

    public static void _depthMask(boolean mask) {
        RenderSystem.assertOnRenderThread();
        if (mask != DEPTH.mask) {
            DEPTH.mask = mask;
            GL33C.glDepthMask(mask);
        }
    }

    public static void _disableBlend(int index) {
        RenderSystem.assertOnRenderThread();
        BLEND[index].mode.disable();
    }

    public static void _enableBlend(int index) {
        RenderSystem.assertOnRenderThread();
        BLEND[index].mode.enable();
    }

    public static void _blendFuncSeparate(int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
        RenderSystem.assertOnRenderThread();
        GlStateManager.BlendState firstBlend = BLEND[0];
        if (srcRgb != firstBlend.srcRgb || dstRgb != firstBlend.dstRgb || srcAlpha != firstBlend.srcAlpha || dstAlpha != firstBlend.dstAlpha) {
            firstBlend.srcRgb = srcRgb;
            firstBlend.dstRgb = dstRgb;
            firstBlend.srcAlpha = srcAlpha;
            firstBlend.dstAlpha = dstAlpha;
            glBlendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
        }
    }

    public static void _blendEquationSeparate(int modeRgb, int modeAlpha) {
        RenderSystem.assertOnRenderThread();
        GlStateManager.BlendState firstBlend = BLEND[0];
        if (modeRgb != firstBlend.modeRgb || modeAlpha != firstBlend.modeAlpha) {
            firstBlend.modeRgb = modeRgb;
            firstBlend.modeAlpha = modeAlpha;
            glBlendEquationSeparate(modeRgb, modeAlpha);
        }
    }

    public static int glGetProgrami(int program, int pname) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetProgrami(program, pname);
    }

    public static void glAttachShader(int program, int shader) {
        RenderSystem.assertOnRenderThread();
        GL33C.glAttachShader(program, shader);
    }

    public static void glDeleteShader(int shader) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDeleteShader(shader);
    }

    public static int glCreateShader(int type) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glCreateShader(type);
    }

    public static void glShaderSource(int shader, String source) {
        RenderSystem.assertOnRenderThread();
        byte[] encoded = source.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buffer = MemoryUtil.memAlloc(encoded.length + 1);
        buffer.put(encoded);
        buffer.put((byte)0);
        buffer.flip();

        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer pointers = stack.mallocPointer(1);
            pointers.put(buffer);
            GL33C.nglShaderSource(shader, 1, pointers.address0(), 0L);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    public static void glCompileShader(int shader) {
        RenderSystem.assertOnRenderThread();
        GL33C.glCompileShader(shader);
    }

    public static int glGetShaderi(int shader, int pname) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetShaderi(shader, pname);
    }

    public static void _glUseProgram(int program) {
        RenderSystem.assertOnRenderThread();
        GL33C.glUseProgram(program);
    }

    public static int glCreateProgram() {
        RenderSystem.assertOnRenderThread();
        return GL33C.glCreateProgram();
    }

    public static void glDeleteProgram(int program) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDeleteProgram(program);
    }

    public static void glLinkProgram(int program) {
        RenderSystem.assertOnRenderThread();
        GL33C.glLinkProgram(program);
    }

    public static int _glGetUniformLocation(int program, CharSequence name) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetUniformLocation(program, name);
    }

    public static void _glUniform1i(int location, int v0) {
        RenderSystem.assertOnRenderThread();
        GL33C.glUniform1i(location, v0);
    }

    public static void _glBindAttribLocation(int program, int location, CharSequence name) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBindAttribLocation(program, location, name);
    }

    static void incrementTrackedBuffers() {
        numBuffers++;
        PLOT_BUFFERS.setValue(numBuffers);
    }

    public static int _glGenBuffers() {
        RenderSystem.assertOnRenderThread();
        incrementTrackedBuffers();
        return GL33C.glGenBuffers();
    }

    public static int _glGenVertexArrays() {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGenVertexArrays();
    }

    public static void _glBindBuffer(int target, int buffer) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBindBuffer(target, buffer);
    }

    public static void _glBindVertexArray(int arrayId) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBindVertexArray(arrayId);
    }

    public static void _glBufferData(int target, ByteBuffer data, int usage) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBufferData(target, data, usage);
    }

    public static void _glBufferSubData(int target, long offset, ByteBuffer data) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBufferSubData(target, offset, data);
    }

    public static void _glBufferData(int target, long size, int usage) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBufferData(target, size, usage);
    }

    public static @Nullable ByteBuffer _glMapBufferRange(int target, long offset, long length, int access) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glMapBufferRange(target, offset, length, access);
    }

    public static void _glUnmapBuffer(int target) {
        RenderSystem.assertOnRenderThread();
        GL33C.glUnmapBuffer(target);
    }

    public static void _glDeleteBuffers(int buffer) {
        RenderSystem.assertOnRenderThread();
        numBuffers--;
        PLOT_BUFFERS.setValue(numBuffers);
        GL33C.glDeleteBuffers(buffer);
    }

    public static void _glBindFramebuffer(int target, int framebuffer) {
        if ((target == 36008 || target == 36160) && readFbo != framebuffer) {
            GL33C.glBindFramebuffer(36008, framebuffer);
            readFbo = framebuffer;
        }

        if ((target == 36009 || target == 36160) && writeFbo != framebuffer) {
            GL33C.glBindFramebuffer(36009, framebuffer);
            writeFbo = framebuffer;
        }
    }

    public static int getFrameBuffer(int target) {
        if (target == 36008) {
            return readFbo;
        } else {
            return target == 36009 ? writeFbo : 0;
        }
    }

    public static void _glBlitFrameBuffer(int srcX0, int srcY0, int srcX1, int srcY1, int dstX0, int dstY0, int dstX1, int dstY1, int mask, int filter) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBlitFramebuffer(srcX0, srcY0, srcX1, srcY1, dstX0, dstY0, dstX1, dstY1, mask, filter);
    }

    public static void _glDeleteFramebuffers(int framebuffer) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDeleteFramebuffers(framebuffer);
        if (readFbo == framebuffer) {
            readFbo = 0;
        }

        if (writeFbo == framebuffer) {
            writeFbo = 0;
        }
    }

    public static int glGenFramebuffers() {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGenFramebuffers();
    }

    public static void _glFramebufferTexture2D(int target, int attachment, int textarget, int texture, int level) {
        RenderSystem.assertOnRenderThread();
        GL33C.glFramebufferTexture2D(target, attachment, textarget, texture, level);
    }

    public static void glBlendFuncSeparate(int srcColor, int dstColor, int srcAlpha, int dstAlpha) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBlendFuncSeparate(srcColor, dstColor, srcAlpha, dstAlpha);
    }

    public static void glBlendEquationSeparate(int modeRgb, int modeAlpha) {
        RenderSystem.assertOnRenderThread();
        GL33C.glBlendEquationSeparate(modeRgb, modeAlpha);
    }

    public static String glGetShaderInfoLog(int shader, int maxLength) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetShaderInfoLog(shader, maxLength);
    }

    public static String glGetProgramInfoLog(int program, int maxLength) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetProgramInfoLog(program, maxLength);
    }

    public static void _enableCull() {
        RenderSystem.assertOnRenderThread();
        CULL.enable.enable();
    }

    public static void _disableCull() {
        RenderSystem.assertOnRenderThread();
        CULL.enable.disable();
    }

    public static void _polygonMode(int face, int mode) {
        RenderSystem.assertOnRenderThread();
        GL33C.glPolygonMode(face, mode);
    }

    public static void _enablePolygonOffset() {
        RenderSystem.assertOnRenderThread();
        POLY_OFFSET.fill.enable();
    }

    public static void _disablePolygonOffset() {
        RenderSystem.assertOnRenderThread();
        POLY_OFFSET.fill.disable();
    }

    public static void _polygonOffset(float factor, float units) {
        RenderSystem.assertOnRenderThread();
        if (factor != POLY_OFFSET.factor || units != POLY_OFFSET.units) {
            POLY_OFFSET.factor = factor;
            POLY_OFFSET.units = units;
            GL33C.glPolygonOffset(factor, units);
        }
    }

    public static void _enableColorLogicOp() {
        RenderSystem.assertOnRenderThread();
        COLOR_LOGIC.enable.enable();
    }

    public static void _disableColorLogicOp() {
        RenderSystem.assertOnRenderThread();
        COLOR_LOGIC.enable.disable();
    }

    public static void _logicOp(int op) {
        RenderSystem.assertOnRenderThread();
        if (op != COLOR_LOGIC.op) {
            COLOR_LOGIC.op = op;
            GL33C.glLogicOp(op);
        }
    }

    public static void _activeTexture(int texture) {
        RenderSystem.assertOnRenderThread();
        if (activeTexture != texture - 33984) {
            activeTexture = texture - 33984;
            GL33C.glActiveTexture(texture);
        }
    }

    public static void _texParameter(int target, int name, int value) {
        RenderSystem.assertOnRenderThread();
        GL33C.glTexParameteri(target, name, value);
    }

    public static int _getTexLevelParameter(int target, int level, int name) {
        return GL33C.glGetTexLevelParameteri(target, level, name);
    }

    public static int _genTexture() {
        RenderSystem.assertOnRenderThread();
        numTextures++;
        PLOT_TEXTURES.setValue(numTextures);
        return GL33C.glGenTextures();
    }

    public static void _deleteTexture(int id) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDeleteTextures(id);

        for (GlStateManager.TextureState state : TEXTURES) {
            if (state.binding == id) {
                state.binding = -1;
            }
        }

        numTextures--;
        PLOT_TEXTURES.setValue(numTextures);
    }

    public static void _bindTexture(int id) {
        RenderSystem.assertOnRenderThread();
        if (id != TEXTURES[activeTexture].binding) {
            TEXTURES[activeTexture].binding = id;
            GL33C.glBindTexture(3553, id);
        }
    }

    public static void _texImage2D(
        int target, int level, int internalformat, int width, int height, int border, int format, int type, @Nullable ByteBuffer pixels
    ) {
        RenderSystem.assertOnRenderThread();
        GL33C.glTexImage2D(target, level, internalformat, width, height, border, format, type, pixels);
    }

    public static void _texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, long pixels) {
        RenderSystem.assertOnRenderThread();
        GL33C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public static void _texSubImage2D(int target, int level, int xoffset, int yoffset, int width, int height, int format, int type, ByteBuffer pixels) {
        RenderSystem.assertOnRenderThread();
        GL33C.glTexSubImage2D(target, level, xoffset, yoffset, width, height, format, type, pixels);
    }

    public static void _viewport(int x, int y, int width, int height) {
        GL33C.glViewport(x, y, width, height);
    }

    public static void _colorMask(@ColorTargetState.WriteMask int writeMask) {
        RenderSystem.assertOnRenderThread();

        for (int i = 0; i < COLOR_MASK.length; i++) {
            if (writeMask != COLOR_MASK[i]) {
                COLOR_MASK[i] = writeMask;
                GL33C.glColorMaski(i, (writeMask & 1) != 0, (writeMask & 2) != 0, (writeMask & 4) != 0, (writeMask & 8) != 0);
            }
        }
    }

    public static void _colorMask(int index, @ColorTargetState.WriteMask int writeMask) {
        RenderSystem.assertOnRenderThread();
        if (writeMask != COLOR_MASK[index]) {
            COLOR_MASK[index] = writeMask;
            GL33C.glColorMaski(index, (writeMask & 1) != 0, (writeMask & 2) != 0, (writeMask & 4) != 0, (writeMask & 8) != 0);
        }
    }

    public static void _clear(int mask) {
        RenderSystem.assertOnRenderThread();
        GL33C.glClear(mask);
        if (MacosUtil.IS_MACOS) {
            _getError();
        }
    }

    public static void _clearBuffer(int index, Vector4fc clearColor) {
        RenderSystem.assertOnRenderThread();
        GL33C.glClearBufferfv(6144, index, new float[]{clearColor.x(), clearColor.y(), clearColor.z(), clearColor.w()});
        if (MacosUtil.IS_MACOS) {
            _getError();
        }
    }

    public static void _clearBuffer(double clearDepth) {
        RenderSystem.assertOnRenderThread();
        GL33C.glClearBufferfv(6145, 0, new float[]{(float)clearDepth});
        if (MacosUtil.IS_MACOS) {
            _getError();
        }
    }

    public static void _vertexAttribPointer(int index, int size, int type, boolean normalized, int stride, long value) {
        RenderSystem.assertOnRenderThread();
        GL33C.glVertexAttribPointer(index, size, type, normalized, stride, value);
    }

    public static void _vertexAttribIPointer(int index, int size, int type, int stride, long value) {
        RenderSystem.assertOnRenderThread();
        GL33C.glVertexAttribIPointer(index, size, type, stride, value);
    }

    public static void _enableVertexAttribArray(int index) {
        RenderSystem.assertOnRenderThread();
        GL33C.glEnableVertexAttribArray(index);
    }

    public static void _drawElements(int mode, int count, int type, long indices) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDrawElements(mode, count, type, indices);
    }

    public static void _drawArrays(int mode, int first, int count) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDrawArrays(mode, first, count);
    }

    public static void _pixelStore(int name, int value) {
        RenderSystem.assertOnRenderThread();
        GL33C.glPixelStorei(name, value);
    }

    public static void _readPixels(int x, int y, int width, int height, int format, int type, long pixels) {
        RenderSystem.assertOnRenderThread();
        GL33C.glReadPixels(x, y, width, height, format, type, pixels);
    }

    public static int _getError() {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetError();
    }

    public static void clearGlErrors() {
        RenderSystem.assertOnRenderThread();

        while (GL33C.glGetError() != 0) {
        }
    }

    public static String _getString(int id) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetString(id);
    }

    public static int _getInteger(int name) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glGetInteger(name);
    }

    public static long _glFenceSync(int condition, int flags) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glFenceSync(condition, flags);
    }

    public static int _glClientWaitSync(long sync, int flags, long timeout) {
        RenderSystem.assertOnRenderThread();
        return GL33C.glClientWaitSync(sync, flags, timeout);
    }

    public static void _glDeleteSync(long sync) {
        RenderSystem.assertOnRenderThread();
        GL33C.glDeleteSync(sync);
    }

    static {
        Arrays.setAll(COLOR_MASK, var0 -> 15);
        Arrays.setAll(BLEND, var0 -> new GlStateManager.BlendState());
    }

    private static class BlendState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3042);
        public int srcRgb = 1;
        public int dstRgb = 0;
        public int modeRgb = 32774;
        public int srcAlpha = 1;
        public int dstAlpha = 0;
        public int modeAlpha = 32774;
    }

    private static class BooleanState {
        private final int state;
        private boolean enabled;

        public BooleanState(int state) {
            this.state = state;
        }

        public void disable() {
            this.setEnabled(false);
        }

        public void enable() {
            this.setEnabled(true);
        }

        public void setEnabled(boolean enabled) {
            RenderSystem.assertOnRenderThread();
            if (enabled != this.enabled) {
                this.enabled = enabled;
                if (enabled) {
                    GL33C.glEnable(this.state);
                } else {
                    GL33C.glDisable(this.state);
                }
            }
        }
    }

    private static class ColorLogicState {
        public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(3058);
        public int op = 5379;
    }

    private static class CullState {
        public final GlStateManager.BooleanState enable = new GlStateManager.BooleanState(2884);
    }

    private static class DepthState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(2929);
        public boolean mask = true;
        public int func = 513;
    }

    private static class PolygonOffsetState {
        public final GlStateManager.BooleanState fill = new GlStateManager.BooleanState(32823);
        public float factor;
        public float units;
    }

    private static class ScissorState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(3089);
    }

    private static class TextureState {
        public int binding;
    }

    public static void _disableStencilTest() {
        RenderSystem.assertOnRenderThread();
        STENCIL.mode.disable();
    }

    public static void _enableStencilTest() {
        RenderSystem.assertOnRenderThread();
        STENCIL.mode.enable();
    }

    public static void _stencilFunc(int func, int ref, int readMask) {
        RenderSystem.assertOnRenderThread();
        if (func != STENCIL.frontFunc || ref != STENCIL.frontRef || readMask != STENCIL.frontReadMask
                || func != STENCIL.backFunc || ref != STENCIL.backRef || readMask != STENCIL.backReadMask) {
            STENCIL.frontFunc = func;
            STENCIL.frontRef = ref;
            STENCIL.frontReadMask = readMask;
            STENCIL.backFunc = func;
            STENCIL.backRef = ref;
            STENCIL.backReadMask = readMask;
            GL33C.glStencilFuncSeparate(GL33C.GL_FRONT_AND_BACK, func, ref, readMask);
        }
    }

    public static void _stencilFuncFront(int func, int ref, int readMask) {
        RenderSystem.assertOnRenderThread();
        if (func != STENCIL.frontFunc || ref != STENCIL.frontRef || readMask != STENCIL.frontReadMask) {
            STENCIL.frontFunc = func;
            STENCIL.frontRef = ref;
            STENCIL.frontReadMask = readMask;
            GL33C.glStencilFuncSeparate(GL33C.GL_FRONT, func, ref, readMask);
        }
    }

    public static void _stencilFuncBack(int func, int ref, int readMask) {
        RenderSystem.assertOnRenderThread();
        if (func != STENCIL.backFunc || ref != STENCIL.backRef || readMask != STENCIL.backReadMask) {
            STENCIL.backFunc = func;
            STENCIL.backRef = ref;
            STENCIL.backReadMask = readMask;
            GL33C.glStencilFuncSeparate(GL33C.GL_BACK, func, ref, readMask);
        }
    }

    public static void _stencilMask(int mask) {
        RenderSystem.assertOnRenderThread();
        if (mask != STENCIL.writeMask) {
            STENCIL.writeMask = mask;
            GL33C.glStencilMask(mask);
        }
    }

    /**
     * @param stencilFail  The action to take if the stencil test fails.
     * @param depthFail The action to take if the depth buffer test fails.
     * @param pass The action to take if both tests pass.
     */
    public static void _stencilOp(int stencilFail, int depthFail, int pass) {
        RenderSystem.assertOnRenderThread();
        if (stencilFail != STENCIL.frontStencilFail || depthFail != STENCIL.frontDepthFail || pass != STENCIL.frontPass
                || stencilFail != STENCIL.backStencilFail || depthFail != STENCIL.backDepthFail || pass != STENCIL.backPass) {
            STENCIL.frontStencilFail = stencilFail;
            STENCIL.frontDepthFail = depthFail;
            STENCIL.frontPass= pass;
            STENCIL.backStencilFail = stencilFail;
            STENCIL.backDepthFail = depthFail;
            STENCIL.backPass = pass;
            GL33C.glStencilOpSeparate(GL33C.GL_FRONT_AND_BACK, stencilFail, depthFail, pass);
        }
    }

    /**
     * Same as {@link #_stencilOp}, but affects only front-faces.
     */
    public static void _stencilOpFront(int stencilFail, int depthFail, int pass) {
        RenderSystem.assertOnRenderThread();
        if (stencilFail != STENCIL.frontStencilFail || depthFail != STENCIL.frontDepthFail || pass != STENCIL.frontPass) {
            STENCIL.frontStencilFail = stencilFail;
            STENCIL.frontDepthFail = depthFail;
            STENCIL.frontPass= pass;
            GL33C.glStencilOpSeparate(GL33C.GL_FRONT, stencilFail, depthFail, pass);
        }
    }

    /**
     * Same as {@link #_stencilOp}, but affects only back-faces.
     */
    public static void _stencilOpBack(int stencilFail, int depthFail, int pass) {
        RenderSystem.assertOnRenderThread();
        if (stencilFail != STENCIL.backStencilFail || depthFail != STENCIL.backDepthFail || pass != STENCIL.backPass) {
            STENCIL.backStencilFail = stencilFail;
            STENCIL.backDepthFail = depthFail;
            STENCIL.backPass = pass;
            GL33C.glStencilOpSeparate(GL33C.GL_BACK, stencilFail, depthFail, pass);
        }
    }

    @OnlyIn(Dist.CLIENT)
    static class StencilState {
        public final GlStateManager.BooleanState mode = new GlStateManager.BooleanState(GL33C.GL_STENCIL_TEST);
        public int frontFunc = GL33C.GL_ALWAYS;
        public int frontRef;
        public int frontReadMask = -1;
        public int backFunc = GL33C.GL_ALWAYS;
        public int backRef;
        public int backReadMask = -1;
        public int writeMask = -1;
        public int frontStencilFail = GL33C.GL_KEEP;
        public int frontDepthFail = GL33C.GL_KEEP;
        public int frontPass = GL33C.GL_KEEP;
        public int backStencilFail = GL33C.GL_KEEP;
        public int backDepthFail = GL33C.GL_KEEP;
        public int backPass = GL33C.GL_KEEP;
     }
}
