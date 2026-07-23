package net.minecraft.world.level.block.entity;

import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

public class ChestBlockEntity extends RandomizableContainerBlockEntity implements LidBlockEntity {
    private static final int EVENT_SET_OPEN_COUNT = 1;
    private static final Component DEFAULT_NAME = Component.translatable("container.chest");
    private NonNullList<ItemStack> items = NonNullList.withSize(27, ItemStack.EMPTY);
    private final ContainerOpenersCounter openersCounter = new ContainerOpenersCounter() {
        @Override
        protected void onOpen(Level level, BlockPos pos, BlockState blockState) {
            if (blockState.getBlock() instanceof ChestBlock chestBlock) {
                ChestBlockEntity.playSound(level, pos, blockState, chestBlock.getOpenChestSound());
            }
        }

        @Override
        protected void onClose(Level level, BlockPos pos, BlockState blockState) {
            if (blockState.getBlock() instanceof ChestBlock chestBlock) {
                ChestBlockEntity.playSound(level, pos, blockState, chestBlock.getCloseChestSound());
            }
        }

        @Override
        protected void openerCountChanged(Level level, BlockPos pos, BlockState blockState, int previous, int current) {
            ChestBlockEntity.this.signalOpenCount(level, pos, blockState, previous, current);
        }

        @Override
        public boolean isOwnContainer(Player player) {
            if (!(player.containerMenu instanceof ChestMenu)) {
                return false;
            }

            Container container = ((ChestMenu)player.containerMenu).getContainer();
            return container == ChestBlockEntity.this
                || container instanceof CompoundContainer compoundContainer && compoundContainer.contains(ChestBlockEntity.this);
        }
    };
    private final ChestLidController chestLidController = new ChestLidController();

    protected ChestBlockEntity(BlockEntityType<?> type, BlockPos worldPosition, BlockState blockState) {
        super(type, worldPosition, blockState);
    }

    public ChestBlockEntity(BlockPos worldPosition, BlockState blockState) {
        this(BlockEntityTypes.CHEST, worldPosition, blockState);
    }

    @Override
    public int getContainerSize() {
        return 27;
    }

    @Override
    protected Component getDefaultName() {
        return DEFAULT_NAME;
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }

    public static void lidAnimateTick(Level level, BlockPos pos, BlockState state, ChestBlockEntity entity) {
        entity.chestLidController.tickLid();
    }

    private static void playSound(Level level, BlockPos worldPosition, BlockState blockState, SoundEvent event) {
        ChestType type = blockState.getValue(ChestBlock.TYPE);
        if (type != ChestType.LEFT) {
            double x = worldPosition.getX() + 0.5;
            double y = worldPosition.getY() + 0.5;
            double z = worldPosition.getZ() + 0.5;
            if (type == ChestType.RIGHT) {
                Direction direction = ChestBlock.getConnectedDirection(blockState);
                x += direction.getStepX() * 0.5;
                z += direction.getStepZ() * 0.5;
            }

            level.playSound(null, x, y, z, event, SoundSource.BLOCKS, 0.5F, level.getRandom().nextFloat() * 0.1F + 0.9F);
        }
    }

    @Override
    public boolean triggerEvent(int b0, int b1) {
        if (b0 == 1) {
            this.chestLidController.shouldBeOpen(b1 > 0);
            return true;
        } else {
            return super.triggerEvent(b0, b1);
        }
    }

    @Override
    public void startOpen(ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter
                .incrementOpeners(
                    containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState(), containerUser.getContainerInteractionRange()
                );
        }
    }

    @Override
    public void stopOpen(ContainerUser containerUser) {
        if (!this.remove && !containerUser.getLivingEntity().isSpectator()) {
            this.openersCounter.decrementOpeners(containerUser.getLivingEntity(), this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    @Override
    public List<ContainerUser> getEntitiesWithContainerOpen() {
        return this.openersCounter.getEntitiesWithContainerOpen(this.getLevel(), this.getBlockPos());
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public float getOpenNess(float a) {
        return this.chestLidController.getOpenness(a);
    }

    public static int getOpenCount(BlockGetter level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.hasBlockEntity() && level.getBlockEntity(pos) instanceof ChestBlockEntity chestBlockEntity
            ? chestBlockEntity.openersCounter.getOpenerCount()
            : 0;
    }

    public static void swapContents(ChestBlockEntity one, ChestBlockEntity two) {
        NonNullList<ItemStack> items = one.getItems();
        one.setItems(two.getItems());
        two.setItems(items);
    }

    @Override
    protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
        return ChestMenu.threeRows(containerId, inventory, this);
    }

    @Override
    public void setBlockState(BlockState blockState) {
        var oldState = getBlockState();
        super.setBlockState(blockState);
        // Neo: Chest state change might change the chest item handler -> invalidate
        if ((oldState.getValue(ChestBlock.FACING) != blockState.getValue(ChestBlock.FACING))
                || (oldState.getValue(ChestBlock.TYPE) != blockState.getValue(ChestBlock.TYPE))) {
            this.invalidateCapabilities();
        }
    }

    public void recheckOpen() {
        if (!this.remove) {
            this.openersCounter.recheckOpeners(this.getLevel(), this.getBlockPos(), this.getBlockState());
        }
    }

    protected void signalOpenCount(Level level, BlockPos pos, BlockState blockState, int previous, int current) {
        Block block = blockState.getBlock();
        level.blockEvent(pos, block, 1, current);
    }
}
