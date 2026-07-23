package net.minecraft.client.model.geom;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public record PartPose(float x, float y, float z, float xRot, float yRot, float zRot, float xScale, float yScale, float zScale) {
    public static final PartPose ZERO = offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F);

    public static PartPose offset(float x, float y, float z) {
        return offsetAndRotation(x, y, z, 0.0F, 0.0F, 0.0F);
    }

    public static PartPose rotation(float x, float y, float z) {
        return offsetAndRotation(0.0F, 0.0F, 0.0F, x, y, z);
    }

    public static PartPose offsetAndRotation(float offsetX, float offsetY, float offsetZ, float rotationX, float rotationY, float rotationZ) {
        return new PartPose(offsetX, offsetY, offsetZ, rotationX, rotationY, rotationZ, 1.0F, 1.0F, 1.0F);
    }

    public PartPose translated(float x, float y, float z) {
        return new PartPose(this.x + x, this.y + y, this.z + z, this.xRot, this.yRot, this.zRot, this.xScale, this.yScale, this.zScale);
    }

    public PartPose withScale(float scale) {
        return new PartPose(this.x, this.y, this.z, this.xRot, this.yRot, this.zRot, scale, scale, scale);
    }

    public PartPose scaled(float factor) {
        return factor == 1.0F ? this : this.scaled(factor, factor, factor);
    }

    public PartPose scaled(float scaleX, float scaleY, float scaleZ) {
        return new PartPose(
            this.x * scaleX,
            this.y * scaleY,
            this.z * scaleZ,
            this.xRot,
            this.yRot,
            this.zRot,
            this.xScale * scaleX,
            this.yScale * scaleY,
            this.zScale * scaleZ
        );
    }
}
