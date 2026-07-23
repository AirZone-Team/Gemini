package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class RisingParticle extends SingleQuadParticle {
    public RisingParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, xd, yd, zd, sprite);
        this.friction = 0.96F;
        this.xd = this.xd * 0.01F + xd;
        this.yd = this.yd * 0.01F + yd;
        this.zd = this.zd * 0.01F + zd;
        this.x = this.x + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
        this.y = this.y + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
        this.z = this.z + (this.random.nextFloat() - this.random.nextFloat()) * 0.05F;
        this.lifetime = (int)(8.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 4;
    }
}
