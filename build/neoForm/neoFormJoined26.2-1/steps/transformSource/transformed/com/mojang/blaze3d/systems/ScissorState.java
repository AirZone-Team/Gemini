package com.mojang.blaze3d.systems;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ScissorState {
    private boolean enabled;
    private int x;
    private int y;
    private int width;
    private int height;

    public ScissorState() {
    }

    public ScissorState(ScissorState state) {
        this.enabled = state.enabled;
        this.x = state.x;
        this.y = state.y;
        this.width = state.width;
        this.height = state.height;
    }

    public void enable(int x, int y, int width, int height) {
        this.enabled = true;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void disable() {
        this.enabled = false;
    }

    public boolean enabled() {
        return this.enabled;
    }

    public int x() {
        return this.x;
    }

    public int y() {
        return this.y;
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public void setFrom(ScissorState state) {
        this.enabled = state.enabled;
        this.x = state.x;
        this.y = state.y;
        this.width = state.width;
        this.height = state.height;
    }
}
