package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SimpleVerticalParticle extends SingleQuadParticle {
    public SimpleVerticalParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, TextureAtlasSprite sprite, boolean upwards) {
        super(level, x, y, z, xa, ya, za, sprite);
        this.xd = xa;
        this.zd = za;
        this.yd = ya;
        this.gravity = 0.0F;
        this.yd += upwards ? 0.03 : -0.03;
        this.quadSize = this.quadSize * (this.random.nextFloat() * 0.6F + 0.5F);
        this.lifetime = 8;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    public record PauseMobGrowthProvider(SpriteSet sprite) implements ParticleProvider<SimpleParticleType> {
        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new SimpleVerticalParticle(level, x, y, z, xAux, yAux, zAux, this.sprite.get(random), false);
        }
    }

    public record ResetMobGrowthProvider(SpriteSet sprite) implements ParticleProvider<SimpleParticleType> {
        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new SimpleVerticalParticle(level, x, y, z, xAux, yAux, zAux, this.sprite.get(random), true);
        }
    }
}
