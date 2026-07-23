package net.minecraft.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class DripParticle extends SingleQuadParticle {
    private final Fluid type;
    protected boolean isGlowing;

    public DripParticle(ClientLevel level, double x, double y, double z, Fluid type, TextureAtlasSprite sprite) {
        super(level, x, y, z, sprite);
        this.setSize(0.01F, 0.01F);
        this.gravity = 0.06F;
        this.type = type;
    }

    protected Fluid getType() {
        return this.type;
    }

    @Override
    public SingleQuadParticle.Layer getLayer() {
        return SingleQuadParticle.Layer.OPAQUE;
    }

    @Override
    public int getLightCoords(float a) {
        return this.isGlowing ? LightCoordsUtil.withBlock(super.getLightCoords(a), 15) : super.getLightCoords(a);
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.preMoveUpdate();
        if (!this.removed) {
            this.yd = this.yd - this.gravity;
            this.move(this.xd, this.yd, this.zd);
            this.postMoveUpdate();
            if (!this.removed) {
                this.xd *= 0.98F;
                this.yd *= 0.98F;
                this.zd *= 0.98F;
                if (this.type != Fluids.EMPTY) {
                    BlockPos pos = BlockPos.containing(this.x, this.y, this.z);
                    FluidState fluidState = this.level.getFluidState(pos);
                    if (fluidState.is(this.type) && this.y < pos.getY() + fluidState.getHeight(this.level, pos)) {
                        this.remove();
                    }
                }
            }
        }
    }

    protected void preMoveUpdate() {
        if (this.lifetime-- <= 0) {
            this.remove();
        }
    }

    protected void postMoveUpdate() {
    }

    private static class CoolingDripHangParticle extends DripParticle.DripHangParticle {
        public CoolingDripHangParticle(ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions fallingParticle, TextureAtlasSprite sprite) {
            super(level, x, y, z, type, fallingParticle, sprite);
        }

        @Override
        protected void preMoveUpdate() {
            this.rCol = 1.0F;
            this.gCol = 16.0F / (40 - this.lifetime + 16);
            this.bCol = 4.0F / (40 - this.lifetime + 8);
            super.preMoveUpdate();
        }
    }

    private static class DripHangParticle extends DripParticle {
        private final ParticleOptions fallingParticle;

        public DripHangParticle(ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions fallingParticle, TextureAtlasSprite sprite) {
            super(level, x, y, z, type, sprite);
            this.fallingParticle = fallingParticle;
            this.gravity *= 0.02F;
            this.lifetime = 40;
        }

        @Override
        protected void preMoveUpdate() {
            if (this.lifetime-- <= 0) {
                this.remove();
                this.level.addParticle(this.fallingParticle, this.x, this.y, this.z, this.xd, this.yd, this.zd);
            }
        }

        @Override
        protected void postMoveUpdate() {
            this.xd *= 0.02;
            this.yd *= 0.02;
            this.zd *= 0.02;
        }
    }

    private static class DripLandParticle extends DripParticle {
        public DripLandParticle(ClientLevel level, double x, double y, double z, Fluid type, TextureAtlasSprite sprite) {
            super(level, x, y, z, type, sprite);
            this.lifetime = (int)(16.0 / (this.random.nextFloat() * 0.8 + 0.2));
        }
    }

    private static class DripstoneFallAndLandParticle extends DripParticle.FallAndLandParticle {
        public DripstoneFallAndLandParticle(
            ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions landParticle, TextureAtlasSprite sprite
        ) {
            super(level, x, y, z, type, landParticle, sprite);
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(this.landParticle, this.x, this.y, this.z, 0.0, 0.0, 0.0);
                SoundEvent sound = this.getType() == Fluids.LAVA ? SoundEvents.POINTED_DRIPSTONE_DRIP_LAVA : SoundEvents.POINTED_DRIPSTONE_DRIP_WATER;
                float volume = Mth.randomBetween(this.random, 0.3F, 1.0F);
                this.level.playLocalSound(this.x, this.y, this.z, sound, SoundSource.BLOCKS, volume, 1.0F, false);
            }
        }
    }

    public static class DripstoneLavaFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public DripstoneLavaFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripstoneFallAndLandParticle(
                level, x, y, z, Fluids.LAVA, ParticleTypes.LANDING_LAVA, this.sprite.get(random)
            );
            particle.setColor(1.0F, 0.2857143F, 0.083333336F);
            return particle;
        }
    }

    public static class DripstoneLavaHangProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public DripstoneLavaHangProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new DripParticle.CoolingDripHangParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.FALLING_DRIPSTONE_LAVA, this.sprite.get(random));
        }
    }

    public static class DripstoneWaterFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public DripstoneWaterFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripstoneFallAndLandParticle(level, x, y, z, Fluids.WATER, ParticleTypes.SPLASH, this.sprite.get(random));
            particle.setColor(0.2F, 0.3F, 1.0F);
            return particle;
        }
    }

    public static class DripstoneWaterHangProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public DripstoneWaterHangProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripHangParticle(
                level, x, y, z, Fluids.WATER, ParticleTypes.FALLING_DRIPSTONE_WATER, this.sprite.get(random)
            );
            particle.setColor(0.2F, 0.3F, 1.0F);
            return particle;
        }
    }

    private static class FallAndLandParticle extends DripParticle.FallingParticle {
        protected final ParticleOptions landParticle;

        public FallAndLandParticle(ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions landParticle, TextureAtlasSprite sprite) {
            super(level, x, y, z, type, sprite);
            this.lifetime = (int)(64.0 / (this.random.nextFloat() * 0.8 + 0.2));
            this.landParticle = landParticle;
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(this.landParticle, this.x, this.y, this.z, 0.0, 0.0, 0.0);
            }
        }
    }

    private static class FallingParticle extends DripParticle {
        public FallingParticle(ClientLevel level, double x, double y, double z, Fluid type, TextureAtlasSprite sprite) {
            super(level, x, y, z, type, sprite);
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
            }
        }
    }

    private static class HoneyFallAndLandParticle extends DripParticle.FallAndLandParticle {
        public HoneyFallAndLandParticle(ClientLevel level, double x, double y, double z, Fluid type, ParticleOptions landParticle, TextureAtlasSprite sprite) {
            super(level, x, y, z, type, landParticle, sprite);
        }

        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(this.landParticle, this.x, this.y, this.z, 0.0, 0.0, 0.0);
                float volume = Mth.randomBetween(this.random, 0.3F, 1.0F);
                this.level.playLocalSound(this.x, this.y, this.z, SoundEvents.BEEHIVE_DRIP, SoundSource.BLOCKS, volume, 1.0F, false);
            }
        }
    }

    public static class HoneyFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public HoneyFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.HoneyFallAndLandParticle(
                level, x, y, z, Fluids.EMPTY, ParticleTypes.LANDING_HONEY, this.sprite.get(random)
            );
            particle.gravity = 0.01F;
            particle.setColor(0.582F, 0.448F, 0.082F);
            return particle;
        }
    }

    public static class HoneyHangProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public HoneyHangProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle.DripHangParticle particle = new DripParticle.DripHangParticle(
                level, x, y, z, Fluids.EMPTY, ParticleTypes.FALLING_HONEY, this.sprite.get(random)
            );
            particle.gravity *= 0.01F;
            particle.lifetime = 100;
            particle.setColor(0.622F, 0.508F, 0.082F);
            return particle;
        }
    }

    public static class HoneyLandProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public HoneyLandProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripLandParticle(level, x, y, z, Fluids.EMPTY, this.sprite.get(random));
            particle.lifetime = (int)(128.0 / (random.nextFloat() * 0.8 + 0.2));
            particle.setColor(0.522F, 0.408F, 0.082F);
            return particle;
        }
    }

    public static class LavaFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public LavaFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.FallAndLandParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.LANDING_LAVA, this.sprite.get(random));
            particle.setColor(1.0F, 0.2857143F, 0.083333336F);
            return particle;
        }
    }

    public static class LavaHangProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public LavaHangProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            return new DripParticle.CoolingDripHangParticle(level, x, y, z, Fluids.LAVA, ParticleTypes.FALLING_LAVA, this.sprite.get(random));
        }
    }

    public static class LavaLandProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public LavaLandProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripLandParticle(level, x, y, z, Fluids.LAVA, this.sprite.get(random));
            particle.setColor(1.0F, 0.2857143F, 0.083333336F);
            return particle;
        }
    }

    public static class NectarFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public NectarFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.FallingParticle(level, x, y, z, Fluids.EMPTY, this.sprite.get(random));
            particle.lifetime = (int)(16.0 / (random.nextFloat() * 0.8 + 0.2));
            particle.gravity = 0.007F;
            particle.setColor(0.92F, 0.782F, 0.72F);
            return particle;
        }
    }

    public static class ObsidianTearFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ObsidianTearFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.FallAndLandParticle(
                level, x, y, z, Fluids.EMPTY, ParticleTypes.LANDING_OBSIDIAN_TEAR, this.sprite.get(random)
            );
            particle.isGlowing = true;
            particle.gravity = 0.01F;
            particle.setColor(0.51171875F, 0.03125F, 0.890625F);
            return particle;
        }
    }

    public static class ObsidianTearHangProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ObsidianTearHangProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle.DripHangParticle particle = new DripParticle.DripHangParticle(
                level, x, y, z, Fluids.EMPTY, ParticleTypes.FALLING_OBSIDIAN_TEAR, this.sprite.get(random)
            );
            particle.isGlowing = true;
            particle.gravity *= 0.01F;
            particle.lifetime = 100;
            particle.setColor(0.51171875F, 0.03125F, 0.890625F);
            return particle;
        }
    }

    public static class ObsidianTearLandProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public ObsidianTearLandProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripLandParticle(level, x, y, z, Fluids.EMPTY, this.sprite.get(random));
            particle.isGlowing = true;
            particle.lifetime = (int)(28.0 / (random.nextFloat() * 0.8 + 0.2));
            particle.setColor(0.51171875F, 0.03125F, 0.890625F);
            return particle;
        }
    }

    public static class SporeBlossomFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public SporeBlossomFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.FallingParticle(level, x, y, z, Fluids.EMPTY, this.sprite.get(random));
            particle.lifetime = (int)(64.0F / Mth.randomBetween(particle.random, 0.1F, 0.9F));
            particle.gravity = 0.005F;
            particle.setColor(0.32F, 0.5F, 0.22F);
            return particle;
        }
    }

    public static class WaterFallProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public WaterFallProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.FallAndLandParticle(level, x, y, z, Fluids.WATER, ParticleTypes.SPLASH, this.sprite.get(random));
            particle.setColor(0.2F, 0.3F, 1.0F);
            return particle;
        }
    }

    public static class WaterHangProvider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public WaterHangProvider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        public Particle createParticle(
            SimpleParticleType options, ClientLevel level, double x, double y, double z, double xAux, double yAux, double zAux, RandomSource random
        ) {
            DripParticle particle = new DripParticle.DripHangParticle(level, x, y, z, Fluids.WATER, ParticleTypes.FALLING_WATER, this.sprite.get(random));
            particle.setColor(0.2F, 0.3F, 1.0F);
            return particle;
        }
    }
}
