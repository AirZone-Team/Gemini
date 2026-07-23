package net.minecraft.world.level.block;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.core.dispenser.DispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

public class DropperBlock extends DispenserBlock {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final MapCodec<DropperBlock> CODEC = simpleCodec(DropperBlock::new);
    private static final DispenseItemBehavior DISPENSE_BEHAVIOUR = new DefaultDispenseItemBehavior();

    @Override
    public MapCodec<DropperBlock> codec() {
        return CODEC;
    }

    public DropperBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected DispenseItemBehavior getDispenseMethod(Level level, ItemStack itemStack) {
        return DISPENSE_BEHAVIOUR;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos worldPosition, BlockState blockState) {
        return new DropperBlockEntity(worldPosition, blockState);
    }

    @Override
    protected void dispenseFrom(ServerLevel level, BlockState state, BlockPos pos) {
        DispenserBlockEntity blockEntity = level.getBlockEntity(pos, BlockEntityTypes.DROPPER).orElse(null);
        if (blockEntity == null) {
            LOGGER.warn("Ignoring dispensing attempt for Dropper without matching block entity at {}", pos);
        } else {
            BlockSource source = new BlockSource(level, pos, state, blockEntity);
            int slot = blockEntity.getRandomSlot(level.getRandom());
            if (slot < 0) {
                level.levelEvent(1001, pos, 0);
            } else {
                ItemStack itemStack = blockEntity.getItem(slot);
                if (!itemStack.isEmpty()) {
                    Direction direction = level.getBlockState(pos).getValue(FACING);
                    var into = HopperBlockEntity.getContainerOrHandlerAt(level, pos.relative(direction), direction.getOpposite());
                    ItemStack remaining;
                    if (into.isEmpty()) {
                        remaining = DISPENSE_BEHAVIOUR.dispense(source, itemStack);
                    } else {
                        if (into.container() != null) {
                            remaining = HopperBlockEntity.addItem(blockEntity, into.container(), itemStack.copyWithCount(1), direction.getOpposite());
                        } else {
                            remaining = net.neoforged.neoforge.transfer.item.ItemUtil.insertItemReturnRemaining(into.itemHandler(), itemStack.copyWithCount(1), false, null);
                        }
                        if (remaining.isEmpty()) {
                            remaining = itemStack.copy();
                            remaining.shrink(1);
                        } else {
                            remaining = itemStack.copy();
                        }
                    }

                    blockEntity.setItem(slot, remaining);
                }
            }
        }
    }
}
