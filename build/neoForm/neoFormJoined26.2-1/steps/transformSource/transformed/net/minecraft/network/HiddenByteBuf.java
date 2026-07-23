package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCounted;

public record HiddenByteBuf(ByteBuf contents) implements ReferenceCounted {
    public HiddenByteBuf(ByteBuf contents) {
        this.contents = ByteBufUtil.ensureAccessible(contents);
    }

    public static Object pack(Object msg) {
        return msg instanceof ByteBuf buf ? new HiddenByteBuf(buf) : msg;
    }

    public static Object unpack(Object msg) {
        return msg instanceof HiddenByteBuf buf ? ByteBufUtil.ensureAccessible(buf.contents) : msg;
    }

    @Override
    public int refCnt() {
        return this.contents.refCnt();
    }

    public HiddenByteBuf retain() {
        this.contents.retain();
        return this;
    }

    public HiddenByteBuf retain(int increment) {
        this.contents.retain(increment);
        return this;
    }

    public HiddenByteBuf touch() {
        this.contents.touch();
        return this;
    }

    public HiddenByteBuf touch(Object hint) {
        this.contents.touch(hint);
        return this;
    }

    @Override
    public boolean release() {
        return this.contents.release();
    }

    @Override
    public boolean release(int decrement) {
        return this.contents.release(decrement);
    }
}
