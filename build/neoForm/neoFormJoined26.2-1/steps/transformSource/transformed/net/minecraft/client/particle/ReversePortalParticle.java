package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ReversePortalParticle extends PortalParticle {
    public ReversePortalParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, TextureAtlasSprite sprite) {
        super(level, x, y, z, xd, yd, zd, sprite);
        this.quadSize *= 1.5F;
        this.lifetime = (int)(this.random.nextFloat() * 2.0F) + 60;
    }

    @Override
    public float getQuadSize(float a) {
        float s = 1.0F - (this.age + a) / (this.lifetime * 1.5F);
        return this.quadSize * s;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            float speedMultiplier = (float)this.age / this.lifetime;
            this.x = this.x + this.xd * speedMultiplier;
            this.y = this.y + this.yd * speedMultiplier;
            this.z = this.z + this.zd * speedMultiplier;
            this.setPos(this.x, this.y, this.z); // Neo: update the particle's bounding box
        }
    }

    public static class ReversePortalProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ReversePortalProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new ReversePortalParticle(level, x, y, z, xAux, yAux, zAux, this.sprite.get(random));
        }
    }
}
