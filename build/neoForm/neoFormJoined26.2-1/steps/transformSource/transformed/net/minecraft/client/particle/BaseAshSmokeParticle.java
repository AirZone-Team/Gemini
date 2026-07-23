package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public abstract class BaseAshSmokeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    public BaseAshSmokeParticle(
        ClientLevel level,
        double x,
        double y,
        double z,
        float dirX,
        float dirY,
        float dirZ,
        double xa,
        double ya,
        double za,
        float scale,
        SpriteSet sprites,
        float colorRandom,
        int maxLifetime,
        float gravity,
        boolean hasPhysics
    ) {
        super(level, x, y, z, 0.0, 0.0, 0.0, sprites.first());
        this.friction = 0.96F;
        this.gravity = gravity;
        this.speedUpWhenYMotionIsBlocked = true;
        this.sprites = sprites;
        this.xd *= dirX;
        this.yd *= dirY;
        this.zd *= dirZ;
        this.xd += xa;
        this.yd += ya;
        this.zd += za;
        float col = this.random.nextFloat() * colorRandom;
        this.rCol = col;
        this.gCol = col;
        this.bCol = col;
        this.quadSize *= 0.75F * scale;
        this.lifetime = (int)(maxLifetime / (this.random.nextFloat() * 0.8 + 0.2) * scale);
        this.lifetime = Math.max(this.lifetime, 1);
        this.setSpriteFromAge(sprites);
        this.hasPhysics = hasPhysics;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public float getQuadSize(float a) {
        return this.quadSize * Mth.clamp((this.age + a) / this.lifetime * 32.0F, 0.0F, 1.0F);
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }
}
