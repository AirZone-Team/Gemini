package com.mojang.blaze3d.vulkan.checkpoints;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import java.util.List;
import java.util.function.Supplier;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.vulkan.VkCommandBuffer;

@OnlyIn(Dist.CLIENT)
public class NoopCheckpointExtension implements CheckpointExtension {
    public static final NoopCheckpointExtension INSTANCE = new NoopCheckpointExtension();

    @Override
    public CheckpointExtension.CheckpointStorage createStorage(VulkanDevice device, VulkanQueue queue, int maxFramesInFlight) {
        return NoopCheckpointExtension.NoopCheckpointStorage.INSTANCE;
    }

    @Override
    public List<CheckpointExtension.QueueCheckpoints> retrieveCheckpoints(boolean isDeviceLost) {
        return List.of();
    }

    @Override
    public void close() {
    }

    private static class NoopCheckpointStorage implements CheckpointExtension.CheckpointStorage {
        private static final NoopCheckpointExtension.NoopCheckpointStorage INSTANCE = new NoopCheckpointExtension.NoopCheckpointStorage();

        @Override
        public void rotate() {
        }

        @Override
        public void recordCheckpoint(VkCommandBuffer commandBuffer, CheckpointExtension.CheckpointType type, Supplier<String> label) {
        }
    }
}
