package net.minecraft.client.multiplayer.prediction;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlockStatePredictionHandler implements AutoCloseable {
    private final Long2ObjectOpenHashMap<BlockStatePredictionHandler.ServerVerifiedState> serverVerifiedStates = new Long2ObjectOpenHashMap<>();
    private int currentSequenceNr;
    private boolean isPredicting;
    private int lastTeleportSequence = -1;

    public void retainKnownServerState(BlockPos pos, BlockState state, LocalPlayer player) {
        this.serverVerifiedStates
            .compute(
                pos.asLong(),
                (key, serverVerifiedState) -> serverVerifiedState != null
                    ? serverVerifiedState.setSequence(this.currentSequenceNr)
                    : new BlockStatePredictionHandler.ServerVerifiedState(this.currentSequenceNr, state, player.position())
            );
    }

    public boolean updateKnownServerState(BlockPos pos, BlockState blockState) {
        BlockStatePredictionHandler.ServerVerifiedState serverVerifiedState = this.serverVerifiedStates.get(pos.asLong());
        if (serverVerifiedState == null) {
            return false;
        }

        serverVerifiedState.setBlockState(blockState);
        return true;
    }

    public void endPredictionsUpTo(int sequence, ClientLevel clientLevel) {
        ObjectIterator<Entry<BlockStatePredictionHandler.ServerVerifiedState>> stateIterator = this.serverVerifiedStates.long2ObjectEntrySet().iterator();

        while (stateIterator.hasNext()) {
            Entry<BlockStatePredictionHandler.ServerVerifiedState> next = stateIterator.next();
            BlockStatePredictionHandler.ServerVerifiedState serverVerifiedState = next.getValue();
            if (serverVerifiedState.sequence <= sequence) {
                BlockPos pos = BlockPos.of(next.getLongKey());
                stateIterator.remove();
                clientLevel.syncBlockState(pos, serverVerifiedState.blockState, this.lastTeleportSequence < sequence ? serverVerifiedState.playerPos : null);
                // Neo: Restore the BlockEntity if one was present before the break was cancelled.
                // Fixes MC-36093 and permits correct server-side only cancellation of block changes.
                if (serverVerifiedState.snapshot != null && serverVerifiedState.blockState == serverVerifiedState.snapshot.getState()) {
                    if (serverVerifiedState.snapshot.restoreBlockEntity(clientLevel, pos)) {
                        // Attempt a re-render if BE data was loaded, since some blocks may depend on it.
                        clientLevel.sendBlockUpdated(pos, serverVerifiedState.blockState, serverVerifiedState.blockState, 3);
                    }
                }
            }
        }
    }

    public BlockStatePredictionHandler startPredicting() {
        this.currentSequenceNr++;
        this.isPredicting = true;
        return this;
    }

    @Override
    public void close() {
        this.isPredicting = false;
    }

    public int currentSequence() {
        return this.currentSequenceNr;
    }

    public void onTeleport() {
        this.lastTeleportSequence = this.currentSequenceNr;
    }

    public boolean isPredicting() {
        return this.isPredicting;
    }

    /**
     * Sets the stored BlockSnapshot on the ServerVerifiedState for the given position.
     * This method is only called after {@link #retainKnownServerState}, so we are certain a map entry exists.
     */
    public void retainSnapshot(BlockPos pos, net.neoforged.neoforge.common.util.BlockSnapshot snapshot) {
        this.serverVerifiedStates.get(pos.asLong()).snapshot = snapshot;
    }

    private static class ServerVerifiedState {
        /**
         * Neo: Used to hold all data necessary for clientside restoration during break denial.
         */
        net.neoforged.neoforge.common.util.BlockSnapshot snapshot;
        private final Vec3 playerPos;
        private int sequence;
        private BlockState blockState;

        private ServerVerifiedState(int sequence, BlockState blockState, Vec3 playerPos) {
            this.sequence = sequence;
            this.blockState = blockState;
            this.playerPos = playerPos;
        }

        private BlockStatePredictionHandler.ServerVerifiedState setSequence(int sequence) {
            this.sequence = sequence;
            return this;
        }

        private void setBlockState(BlockState blockState) {
            this.blockState = blockState;
        }
    }
}
