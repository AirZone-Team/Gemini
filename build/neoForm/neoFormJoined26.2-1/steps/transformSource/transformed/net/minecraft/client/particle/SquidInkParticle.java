package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class SquidInkParticle extends SimpleAnimatedParticle {
    public SquidInkParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, int color, SpriteSet sprites) {
        super(level, x, y, z, sprites, 0.0F);
        this.friction = 0.92F;
        this.quadSize = 0.5F;
        this.setAlpha(1.0F);
        this.setColor(ARGB.redFloat(color), ARGB.greenFloat(color), ARGB.blueFloat(color));
        this.lifetime = (int)(this.quadSize * 12.0F / (this.random.nextFloat() * 0.8F + 0.2F));
        this.setSpriteFromAge(sprites);
        this.hasPhysics = false;
        this.xd = xa;
        this.yd = ya;
        this.zd = za;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.removed) {
            this.setSpriteFromAge(this.sprites);
            if (this.age > this.lifetime / 2) {
                this.setAlpha(1.0F - ((float)this.age - this.lifetime / 2) / this.lifetime);
            }

            if (this.level.getBlockState(BlockPos.containing(this.x, this.y, this.z)).isAir()) {
                this.yd -= 0.0074F;
            }
        }
    }

    public static class GlowInkProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public GlowInkProvider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new SquidInkParticle(level, x, y, z, xAux, yAux, zAux, ARGB.colorFromFloat(1.0F, 0.2F, 0.8F, 0.6F), this.sprites);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new SquidInkParticle(level, x, y, z, xAux, yAux, zAux, -16777216, this.sprites);
        }
    }
}
