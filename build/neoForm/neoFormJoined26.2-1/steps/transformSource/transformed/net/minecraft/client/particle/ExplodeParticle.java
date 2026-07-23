package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ExplodeParticle extends SingleQuadParticle {
    private final SpriteSet sprites;

    public ExplodeParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, SpriteSet sprites) {
        super(level, x, y, z, sprites.first());
        this.gravity = -0.1F;
        this.friction = 0.9F;
        this.sprites = sprites;
        this.xd = xa + (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F;
        this.yd = ya + (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F;
        this.zd = za + (this.random.nextFloat() * 2.0F - 1.0F) * 0.05F;
        float col = this.random.nextFloat() * 0.3F + 0.7F;
        this.rCol = col;
        this.gCol = col;
        this.bCol = col;
        this.quadSize = 0.1F * (this.random.nextFloat() * this.random.nextFloat() * 6.0F + 1.0F);
        this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2)) + 2;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public void tick() {
        super.tick();
        this.setSpriteFromAge(this.sprites);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new ExplodeParticle(level, x, y, z, xAux, yAux, zAux, this.sprites);
        }
    }
}
