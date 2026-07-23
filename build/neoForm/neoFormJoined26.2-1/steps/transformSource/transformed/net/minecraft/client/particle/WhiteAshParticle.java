package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class WhiteAshParticle extends BaseAshSmokeParticle {
    private static final int COLOR_RGB24 = 12235202;

    public WhiteAshParticle(ClientLevel level, double x, double y, double z, double xa, double ya, double za, float scale, SpriteSet sprites) {
        super(level, x, y, z, 0.1F, -0.1F, 0.1F, xa, ya, za, scale, sprites, 0.0F, 20, 0.0125F, false);
        this.rCol = ARGB.red(12235202) / 255.0F;
        this.gCol = ARGB.green(12235202) / 255.0F;
        this.bCol = ARGB.blue(12235202) / 255.0F;
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            double xa = random.nextFloat() * -1.9 * random.nextFloat() * 0.1;
            double ya = random.nextFloat() * -0.5 * random.nextFloat() * 0.1 * 5.0;
            double za = random.nextFloat() * -1.9 * random.nextFloat() * 0.1;
            return new WhiteAshParticle(level, x, y, z, xa, ya, za, 1.0F, this.sprites);
        }
    }
}
