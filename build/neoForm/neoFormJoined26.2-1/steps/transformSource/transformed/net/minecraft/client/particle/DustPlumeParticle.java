package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DustPlumeParticle extends BaseAshSmokeParticle {
    private static final int COLOR_RGB24 = 12235202;

    public DustPlumeParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, float scale, SpriteSet sprites) {
        super(level, x, y, z, 0.7F, 0.6F, 0.7F, xa, ya + 0.15F, za, scale, sprites, 0.5F, 7, 0.5F, false);
        float colorShift = this.random.nextFloat() * 0.2F;
        this.rCol = ARGB.red(12235202) / 255.0F - colorShift;
        this.gCol = ARGB.green(12235202) / 255.0F - colorShift;
        this.bCol = ARGB.blue(12235202) / 255.0F - colorShift;
    }

    @Override
    public void tick() {
        this.gravity = 0.88F * this.gravity;
        this.friction = 0.92F * this.friction;
        super.tick();
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new DustPlumeParticle(level, x, y, z, xAux, yAux, zAux, 1.0F, this.sprites);
        }
    }
}
