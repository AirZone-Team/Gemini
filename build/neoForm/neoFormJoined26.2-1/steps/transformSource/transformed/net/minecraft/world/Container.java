package net.minecraft.world;

import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ContainerUser;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.SlotProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jspecify.annotations.Nullable;

import net.neoforged.neoforge.common.extensions.ContainerExtension;

public interface Container extends Clearable, Iterable<ItemStack>, SlotProvider, ContainerExtension {
    float DEFAULT_DISTANCE_BUFFER = 4.0F;

    int getContainerSize();

    boolean isEmpty();

    ItemStack getItem(int slot);

    ItemStack removeItem(int slot, int count);

    ItemStack removeItemNoUpdate(int slot);

    void setItem(int slot, ItemStack itemStack);

    default int getMaxStackSize() {
        return 99;
    }

    default int getMaxStackSize(ItemStack itemStack) {
        return Math.min(this.getMaxStackSize(), itemStack.getMaxStackSize());
    }

    void setChanged();

    boolean stillValid(Player player);

    default void startOpen(ContainerUser containerUser) {
    }

    default void stopOpen(ContainerUser containerUser) {
    }

    default List<ContainerUser> getEntitiesWithContainerOpen() {
        return List.of();
    }

    default boolean canPlaceItem(int slot, ItemStack itemStack) {
        return true;
    }

    default boolean canTakeItem(Container into, int slot, ItemStack itemStack) {
        return true;
    }

    default int countItem(Item item) {
        int count = 0;

        for (ItemStack slotItem : this) {
            if (slotItem.getItem().equals(item)) {
                count += slotItem.getCount();
            }
        }

        return count;
    }

    default boolean hasAnyOf(Set<Item> item) {
        return this.hasAnyMatching(stack -> !stack.isEmpty() && item.contains(stack.getItem()));
    }

    default boolean hasAnyMatching(Predicate<ItemStack> predicate) {
        for (ItemStack slotItem : this) {
            if (predicate.test(slotItem)) {
                return true;
            }
        }

        return false;
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player) {
        return stillValidBlockEntity(blockEntity, player, 4.0F);
    }

    static boolean stillValidBlockEntity(BlockEntity blockEntity, Player player, float distanceBuffer) {
        Level level = blockEntity.getLevel();
        BlockPos worldPosition = blockEntity.getBlockPos();
        if (level == null) {
            return false;
        } else {
            return level.getBlockEntity(worldPosition) != blockEntity ? false : player.isWithinBlockInteractionRange(worldPosition, distanceBuffer);
        }
    }

    @Override
    default @Nullable SlotAccess getSlot(int slot) {
        return slot >= 0 && slot < this.getContainerSize() ? new SlotAccess() {
            @Override
            public ItemStack get() {
                return Container.this.getItem(slot);
            }

            @Override
            public boolean set(ItemStack itemStack) {
                Container.this.setItem(slot, itemStack);
                return true;
            }
        } : null;
    }

    @Override
    default Iterator<ItemStack> iterator() {
        return new Container.ContainerIterator(this);
    }

    class ContainerIterator implements Iterator<ItemStack> {
        private final Container container;
        private int index;
        private final int size;

        public ContainerIterator(Container container) {
            this.container = container;
            this.size = container.getContainerSize();
        }

        @Override
        public boolean hasNext() {
            return this.index < this.size;
        }

        public ItemStack next() {
            if (!this.hasNext()) {
                throw new NoSuchElementException();
            } else {
                return this.container.getItem(this.index++);
            }
        }
    }
}
