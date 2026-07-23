package net.minecraft.world.level.block.entity;

import com.google.common.annotations.VisibleForTesting;
import net.minecraft.Optionull;
import net.minecraft.advancements.triggers.CriteriaTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SculkCatalystBlock;
import net.minecraft.world.level.block.SculkSpreader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

public class SculkCatalystBlockEntity extends BlockEntity implements GameEventListener.Provider<SculkCatalystBlockEntity.CatalystListener> {
    private final SculkCatalystBlockEntity.CatalystListener catalystListener;

    public SculkCatalystBlockEntity(BlockPos worldPosition, BlockState blockState) {
        super(BlockEntityTypes.SCULK_CATALYST, worldPosition, blockState);
        this.catalystListener = new SculkCatalystBlockEntity.CatalystListener(blockState, new BlockPositionSource(worldPosition));
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, SculkCatalystBlockEntity entity) {
        entity.catalystListener.getSculkSpreader().updateCursors(level, pos, level.getRandom(), true);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.catalystListener.sculkSpreader.load(input);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        this.catalystListener.sculkSpreader.save(output);
        super.saveAdditional(output);
    }

    public SculkCatalystBlockEntity.CatalystListener getListener() {
        return this.catalystListener;
    }

    public static class CatalystListener implements GameEventListener {
        public static final int PULSE_TICKS = 8;
        private final SculkSpreader sculkSpreader;
        private final BlockState blockState;
        private final PositionSource positionSource;

        public CatalystListener(BlockState blockState, PositionSource positionSource) {
            this.blockState = blockState;
            this.positionSource = positionSource;
            this.sculkSpreader = SculkSpreader.createLevelSpreader();
        }

        @Override
        public PositionSource getListenerSource() {
            return this.positionSource;
        }

        @Override
        public int getListenerRadius() {
            return 8;
        }

        @Override
        public GameEventListener.DeliveryMode getDeliveryMode() {
            return GameEventListener.DeliveryMode.BY_DISTANCE;
        }

        @Override
        public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context, Vec3 sourcePosition) {
            if (event.is(GameEvent.ENTITY_DIE) && context.sourceEntity() instanceof LivingEntity mob) {
                if (!mob.wasExperienceConsumed()) {
                    DamageSource lastDamageSource = mob.getLastDamageSource();
                    int experienceWouldDrop = mob.getExperienceReward(level, Optionull.map(lastDamageSource, DamageSource::getEntity));
                    if (mob.shouldDropExperience() && experienceWouldDrop > 0) {
                        this.sculkSpreader.addCursors(BlockPos.containing(sourcePosition.relative(Direction.UP, 0.5)), experienceWouldDrop);
                        this.tryAwardItSpreadsAdvancement(level, mob);
                    }

                    mob.skipDropExperience();
                    this.positionSource.getPosition(level).ifPresent(vec3 -> this.bloom(level, BlockPos.containing(vec3), this.blockState, level.getRandom()));
                }

                return true;
            } else {
                return false;
            }
        }

        @VisibleForTesting
        public SculkSpreader getSculkSpreader() {
            return this.sculkSpreader;
        }

        private void bloom(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
            level.setBlock(pos, state.setValue(SculkCatalystBlock.PULSE, true), 3);
            level.scheduleTick(pos, state.getBlock(), 8);
            level.sendParticles(ParticleTypes.SCULK_SOUL, pos.getX() + 0.5, pos.getY() + 1.15, pos.getZ() + 0.5, 2, 0.2, 0.0, 0.2, 0.0);
            level.playSound(null, pos, SoundEvents.SCULK_CATALYST_BLOOM, SoundSource.BLOCKS, 2.0F, 0.6F + random.nextFloat() * 0.4F);
        }

        private void tryAwardItSpreadsAdvancement(Level level, LivingEntity mob) {
            if (mob.getLastHurtByMob() instanceof ServerPlayer player) {
                DamageSource damageSource = mob.getLastDamageSource() == null ? level.damageSources().playerAttack(player) : mob.getLastDamageSource();
                CriteriaTriggers.KILL_MOB_NEAR_SCULK_CATALYST.trigger(player, mob, damageSource);
            }
        }
    }
}
