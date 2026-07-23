package com.mojang.blaze3d.vertex;

import net.minecraft.core.Direction;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

@OnlyIn(Dist.CLIENT)
public class SheetedDecalTextureGenerator implements VertexConsumer {
    private final VertexConsumer delegate;
    private final Matrix4f cameraInversePose;
    private final Matrix3f normalInversePose;
    private final float textureScale;
    private final Vector3f worldPos = new Vector3f();
    private final Vector3f normal = new Vector3f();
    private float x;
    private float y;
    private float z;

    public SheetedDecalTextureGenerator(VertexConsumer delegate, PoseStack.Pose cameraPose, float textureScale) {
        this.delegate = delegate;
        this.cameraInversePose = new Matrix4f(cameraPose.pose()).invert();
        this.normalInversePose = new Matrix3f(cameraPose.normal()).invert();
        this.textureScale = textureScale;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        this.delegate.setColor(-1);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        this.delegate.setColor(-1);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        this.delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        this.delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        this.delegate.setNormal(x, y, z);
        Vector3f normal = this.normalInversePose.transform(x, y, z, this.normal);
        Direction direction = Direction.getApproximateNearest(normal.x(), normal.y(), normal.z());
        Vector3f worldPos = this.cameraInversePose.transformPosition(this.x, this.y, this.z, this.worldPos);
        worldPos.rotateY((float) Math.PI);
        worldPos.rotateX((float) (-Math.PI / 2));
        worldPos.rotate(direction.getRotation());
        this.delegate.setUv(-worldPos.x() * this.textureScale, -worldPos.y() * this.textureScale);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        this.delegate.setLineWidth(width);
        return this;
    }
}
