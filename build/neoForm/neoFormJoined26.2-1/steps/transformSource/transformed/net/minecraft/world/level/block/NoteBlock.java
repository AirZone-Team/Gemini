package net.minecraft.world.level.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.stats.Stats;
import net.minecraft.tags.ItemTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.entity.SkullBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.redstone.Orientation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

public class NoteBlock extends Block {
    public static final MapCodec<NoteBlock> CODEC = simpleCodec(NoteBlock::new);
    public static final EnumProperty<NoteBlockInstrument> INSTRUMENT = BlockStateProperties.NOTEBLOCK_INSTRUMENT;
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;
    public static final IntegerProperty NOTE = BlockStateProperties.NOTE;
    public static final int NOTE_VOLUME = 3;

    @Override
    public MapCodec<NoteBlock> codec() {
        return CODEC;
    }

    public NoteBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(INSTRUMENT, NoteBlockInstrument.HARP).setValue(NOTE, 0).setValue(POWERED, false));
    }

    private BlockState setInstrument(LevelReader level, BlockPos position, BlockState state) {
        NoteBlockInstrument instrumentAbove = level.getBlockState(position.above()).instrument();
        if (instrumentAbove.worksAboveNoteBlock()) {
            return state.setValue(INSTRUMENT, instrumentAbove);
        }

        NoteBlockInstrument instrumentBelow = level.getBlockState(position.below()).instrument();
        NoteBlockInstrument newBelow = instrumentBelow.worksAboveNoteBlock() ? NoteBlockInstrument.HARP : instrumentBelow;
        return state.setValue(INSTRUMENT, newBelow);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.setInstrument(context.getLevel(), context.getClickedPos(), this.defaultBlockState());
    }

    @Override
    protected BlockState updateShape(
        BlockState state,
        LevelReader level,
        ScheduledTickAccess ticks,
        BlockPos pos,
        Direction directionToNeighbour,
        BlockPos neighbourPos,
        BlockState neighbourState,
        RandomSource random
    ) {
        boolean neighborDirectionSetsInstrument = directionToNeighbour.getAxis() == Direction.Axis.Y;
        return neighborDirectionSetsInstrument
            ? this.setInstrument(level, pos, state)
            : super.updateShape(state, level, ticks, pos, directionToNeighbour, neighbourPos, neighbourState, random);
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, @Nullable Orientation orientation, boolean movedByPiston) {
        boolean signal = level.hasNeighborSignal(pos);
        if (signal != state.getValue(POWERED)) {
            if (signal) {
                this.playNote(null, state, level, pos);
            }

            level.setBlock(pos, state.setValue(POWERED, signal), 3);
        }
    }

    private void playNote(@Nullable Entity source, BlockState state, Level level, BlockPos pos) {
        if (state.getValue(INSTRUMENT).worksAboveNoteBlock() || level.getBlockState(pos.above()).isAir()) {
            level.blockEvent(pos, this, 0, 0);
            level.gameEvent(source, GameEvent.NOTE_BLOCK_PLAY, pos);
        }
    }

    @Override
    protected InteractionResult useItemOn(
        ItemStack itemStack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        return itemStack.is(ItemTags.NOTE_BLOCK_TOP_INSTRUMENTS) && hitResult.getDirection() == Direction.UP
            ? InteractionResult.PASS
            : super.useItemOn(itemStack, state, level, pos, player, hand, hitResult);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide()) {
            int _new = net.neoforged.neoforge.common.CommonHooks.onNoteChange(level, pos, state, state.getValue(NOTE), state.cycle(NOTE).getValue(NOTE));
            if (_new == -1) return InteractionResult.FAIL;
            state = state.setValue(NOTE, _new);
            level.setBlock(pos, state, 3);
            this.playNote(player, state, level, pos);
            player.awardStat(Stats.TUNE_NOTEBLOCK);
        }

        return InteractionResult.SUCCESS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            this.playNote(player, state, level, pos);
            player.awardStat(Stats.PLAY_NOTEBLOCK);
        }
    }

    public static float getPitchFromNote(int twoOctaveRangeNote) {
        return (float)Math.pow(2.0, (twoOctaveRangeNote - 12) / 12.0);
    }

    @Override
    protected boolean triggerEvent(BlockState state, Level level, BlockPos pos, int b0, int b1) {
        net.neoforged.neoforge.event.level.NoteBlockEvent.Play e = new net.neoforged.neoforge.event.level.NoteBlockEvent.Play(level, pos, state, state.getValue(NOTE), state.getValue(INSTRUMENT));
        if (net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(e).isCanceled()) return false;
        state = state.setValue(NOTE, e.getVanillaNoteId()).setValue(INSTRUMENT, e.getInstrument());
        NoteBlockInstrument instrument = state.getValue(INSTRUMENT);
        float pitch;
        if (instrument.isTunable()) {
            int note = state.getValue(NOTE);
            pitch = getPitchFromNote(note);
            level.addParticle(ParticleTypes.NOTE, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, note / 24.0, 0.0, 0.0);
        } else {
            pitch = 1.0F;
        }

        Holder<SoundEvent> soundEvent;
        if (instrument.hasCustomSound()) {
            Identifier soundId = this.getCustomSoundId(level, pos);
            if (soundId == null) {
                return false;
            }

            soundEvent = Holder.direct(SoundEvent.createVariableRangeEvent(soundId));
        } else {
            soundEvent = instrument.getSoundEvent();
        }

        level.playSeededSound(
            null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, soundEvent, SoundSource.RECORDS, 3.0F, pitch, level.getRandom().nextLong()
        );
        return true;
    }

    private @Nullable Identifier getCustomSoundId(Level level, BlockPos pos) {
        return level.getBlockEntity(pos.above()) instanceof SkullBlockEntity head ? head.getNoteBlockSound() : null;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(INSTRUMENT, POWERED, NOTE);
    }
}
