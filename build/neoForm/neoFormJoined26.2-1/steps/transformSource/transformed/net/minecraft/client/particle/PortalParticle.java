package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class PortalParticle extends SingleQuadParticle {
    private final double xStart;
    private final double yStart;
    private final double zStart;

    public PortalParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.x = x;
        this.y = y;
        this.z = z;
        this.xStart = this.x;
        this.yStart = this.y;
        this.zStart = this.z;
        this.quadSize = 0.1F * (this.random.nextFloat() * 0.2F + 0.5F);
        float br = this.random.nextFloat() * 0.6F + 0.4F;
        this.rCol = br * 0.9F;
        this.gCol = br * 0.3F;
        this.bCol = br;
        this.lifetime = (int)(this.random.nextFloat() * 10.0F) + 40;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public void move(double xa, double ya, double za) {
        this.setBoundingBox(this.getBoundingBox().move(xa, ya, za));
        this.setLocationFromBoundingbox();
    }

    @Override
    public float getQuadSize(float a) {
        float s = (this.age + a) / this.lifetime;
        s = 1.0F - s;
        s *= s;
        s = 1.0F - s;
        return this.quadSize * s;
    }

    @Override
    public int getLightCoords(float a) {
        float brightness = (float)this.age / this.lifetime;
        brightness *= brightness;
        brightness *= brightness;
        return LightCoordsUtil.addSmoothBlockEmission(super.getLightCoords(a), brightness);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            float pos = (float)this.age / this.lifetime;
            float a = pos;
            pos = -pos + pos * pos * 2.0F;
            pos = 1.0F - pos;
            this.x = this.xStart + this.xd * pos;
            this.y = this.yStart + this.yd * pos + (1.0F - a);
            this.z = this.zStart + this.zd * pos;
            this.setPos(this.x, this.y, this.z); // FORGE: update the particle's bounding box
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new PortalParticle(level, x, y, z, xAux, yAux, zAux, this.sprite.get(random));
        }
    }
}
