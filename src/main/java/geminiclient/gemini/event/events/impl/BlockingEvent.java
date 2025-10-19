package geminiclient.gemini.event.events.impl;

import geminiclient.gemini.event.impl.CancellableEvent;
import geminiclient.gemini.event.impl.Event;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BlockingEvent extends CancellableEvent {
    public Vec3 getVec3() {
        return vec3;
    }

    public void setVec3(Vec3 vec3) {
        this.vec3 = vec3;
    }

    public BlockState getBlockState() {
        return blockState;
    }

    public void setBlockState(BlockState blockState) {
        this.blockState = blockState;
    }

    private BlockState blockState;
    private Vec3 vec3;
    public BlockingEvent(BlockState blockState,Vec3 vec3) {
        this.blockState = blockState;
        this.vec3 = vec3;
    }
}