package com.mojang.blaze3d.vulkan.init;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.VkDeviceCreateInfo;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties2;

@OnlyIn(Dist.CLIENT)
public record VulkanPNextStruct(int sType, int structSize) {
    public long findOrCreateStructInPNextChain(VkPhysicalDeviceProperties2 properties2, MemoryStack stack) {
        return this.findOrCreateStructInPNextChain(properties2.address(), stack);
    }

    public long findOrCreateStructInPNextChain(VkPhysicalDeviceFeatures2 features2, MemoryStack stack) {
        return this.findOrCreateStructInPNextChain(features2.address(), stack);
    }

    public long findOrCreateStructInPNextChain(long pNextChain, MemoryStack stack) {
        long foundStruct = findStructInPNextChain(pNextChain, this.sType);
        if (foundStruct != 0L) {
            return foundStruct;
        }

        long newStruct = stack.ncalloc(Pointer.POINTER_SIZE, 1, this.structSize);
        VkPhysicalDeviceProperties2.nsType(newStruct, this.sType);
        VkPhysicalDeviceProperties2.npNext(newStruct, VkPhysicalDeviceProperties2.npNext(pNextChain));
        VkPhysicalDeviceProperties2.npNext(pNextChain, newStruct);
        return newStruct;
    }

    public long findStructInPNextChain(long pNextChain) {
        return findStructInPNextChain(pNextChain, this.sType);
    }

    private static long findStructInPNextChain(long pNextChain, int sType) {
        while (pNextChain != 0L) {
            if (VkPhysicalDeviceProperties2.nsType(pNextChain) == sType) {
                return pNextChain;
            }

            pNextChain = MemoryUtil.memGetAddress(pNextChain + VkDeviceCreateInfo.PNEXT);
        }

        return 0L;
    }
}
