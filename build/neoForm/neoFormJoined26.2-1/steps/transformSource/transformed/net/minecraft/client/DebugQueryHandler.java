package net.minecraft.client;

import java.util.function.Consumer;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundBlockEntityTagQueryPacket;
import net.minecraft.network.protocol.game.ServerboundEntityTagQueryPacket;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class DebugQueryHandler {
    private final ClientPacketListener connection;
    private int transactionId = -1;
    private @Nullable Consumer<CompoundTag> callback;

    public DebugQueryHandler(ClientPacketListener connection) {
        this.connection = connection;
    }

    public boolean handleResponse(int transactionId, @Nullable CompoundTag tag) {
        if (this.transactionId == transactionId && this.callback != null) {
            this.callback.accept(tag);
            this.callback = null;
            return true;
        } else {
            return false;
        }
    }

    private int startTransaction(Consumer<CompoundTag> callback) {
        this.callback = callback;
        return ++this.transactionId;
    }

    public void queryEntityTag(int entityId, Consumer<CompoundTag> callback) {
        int transactionId = this.startTransaction(callback);
        this.connection.send(new ServerboundEntityTagQueryPacket(transactionId, entityId));
    }

    public void queryBlockEntityTag(BlockPos blockPos, Consumer<CompoundTag> callback) {
        int transactionId = this.startTransaction(callback);
        this.connection.send(new ServerboundBlockEntityTagQueryPacket(transactionId, blockPos));
    }
}
