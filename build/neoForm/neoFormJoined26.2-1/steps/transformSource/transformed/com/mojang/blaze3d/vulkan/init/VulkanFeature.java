package com.mojang.blaze3d.vulkan.init;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;

@OnlyIn(Dist.CLIENT)
public record VulkanFeature(VulkanPNextStruct struct, String name, long offset) {
    public VulkanFeature(VulkanPNextStruct struct, String name, long offset) {
        this.name = name;
        this.struct = struct;
        if (struct.sType() == 1000059000) {
            this.offset = offset + VkPhysicalDeviceFeatures2.FEATURES;
        } else {
            this.offset = offset;
        }
    }

    public boolean get(VkPhysicalDeviceFeatures2 features2) {
        return this.get(features2.address());
    }

    public boolean get(long pNextChain) {
        long structAddr = this.struct.findStructInPNextChain(pNextChain);
        return structAddr == 0L ? false : MemoryUtil.memGetInt(structAddr + this.offset) != 0;
    }

    public boolean set(VkPhysicalDeviceFeatures2 features2, boolean value) {
        return this.set(features2.address(), value);
    }

    private boolean set(long pNextChain, boolean value) {
        long structAddr = this.struct.findStructInPNextChain(pNextChain);
        if (structAddr == 0L) {
            return false;
        }

        MemoryUtil.memPutInt(structAddr + this.offset, value ? 1 : 0);
        return true;
    }

    public void set(VkPhysicalDeviceFeatures2 features2, boolean value, MemoryStack stack) {
        this.set(features2.address(), value, stack);
    }

    public void set(long pNextChain, boolean value, MemoryStack stack) {
        long structAddr = this.struct.findOrCreateStructInPNextChain(pNextChain, stack);
        MemoryUtil.memPutInt(structAddr + this.offset, value ? 1 : 0);
    }
}
