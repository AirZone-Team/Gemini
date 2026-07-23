package net.minecraft.world.item.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.TooltipFlag;

public final class ItemContainerContents implements TooltipProvider {
    private static final int NO_SLOT = -1;
    private static final int MAX_SIZE = 256;
    public static final ItemContainerContents EMPTY = new ItemContainerContents(List.of());
    public static final Codec<ItemContainerContents> CODEC = ItemContainerContents.Slot.CODEC
        .sizeLimitedListOf(256)
        .xmap(ItemContainerContents::fromSlots, ItemContainerContents::asSlots);
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemContainerContents> STREAM_CODEC = ItemStackTemplate.STREAM_CODEC
        .apply(ByteBufCodecs::optional)
        .<List<Optional<ItemStackTemplate>>>apply(ByteBufCodecs.list(256))
        .map(ItemContainerContents::new, c -> c.items);
    private final List<Optional<ItemStackTemplate>> items;
    private final int hashCode;

    private ItemContainerContents(List<Optional<ItemStackTemplate>> items) {
        if (items.size() > 256) {
            throw new IllegalArgumentException("Got " + items.size() + " items, but maximum is 256");
        }

        this.items = items;
        this.hashCode = items.hashCode();
    }

    private static List<Optional<ItemStackTemplate>> emptyContents(int size) {
        return new ArrayList<>(Collections.nCopies(size, Optional.empty()));
    }

    private static ItemContainerContents fromSlots(List<ItemContainerContents.Slot> slots) {
        OptionalInt maxSlotIndex = slots.stream().mapToInt(ItemContainerContents.Slot::index).max();
        if (maxSlotIndex.isEmpty()) {
            return EMPTY;
        }

        List<Optional<ItemStackTemplate>> items = emptyContents(maxSlotIndex.getAsInt() + 1);

        for (ItemContainerContents.Slot slot : slots) {
            items.set(slot.index(), Optional.of(slot.item()));
        }

        return new ItemContainerContents(items);
    }

    public static ItemContainerContents fromItems(List<ItemStack> itemStacks) {
        int lastNonEmptySlot = findLastNonEmptySlot(itemStacks);
        if (lastNonEmptySlot == -1) {
            return EMPTY;
        }

        List<Optional<ItemStackTemplate>> items = emptyContents(lastNonEmptySlot + 1);

        for (int i = 0; i <= lastNonEmptySlot; i++) {
            ItemStack sourceStack = itemStacks.get(i);
            if (!sourceStack.isEmpty()) {
                items.set(i, Optional.of(ItemStackTemplate.fromNonEmptyStack(sourceStack)));
            }
        }

        return new ItemContainerContents(items);
    }

    private static int findLastNonEmptySlot(List<ItemStack> itemStacks) {
        for (int i = itemStacks.size() - 1; i >= 0; i--) {
            if (!itemStacks.get(i).isEmpty()) {
                return i;
            }
        }

        return -1;
    }

    private List<ItemContainerContents.Slot> asSlots() {
        List<ItemContainerContents.Slot> slots = new ArrayList<>();

        for (int i = 0; i < this.items.size(); i++) {
            Optional<ItemStackTemplate> item = this.items.get(i);
            if (item.isPresent()) {
                slots.add(new ItemContainerContents.Slot(i, item.get()));
            }
        }

        return slots;
    }

    private ItemStack createStackFromSlot(int slot) {
        if (slot < this.items.size()) {
            Optional<ItemStackTemplate> slotContents = this.items.get(slot);
            if (slotContents.isPresent()) {
                return slotContents.get().create();
            }
        }

        return ItemStack.EMPTY;
    }

    public void copyInto(NonNullList<ItemStack> destination) {
        for (int i = 0; i < destination.size(); i++) {
            destination.set(i, this.createStackFromSlot(i));
        }
    }

    public ItemStack copyOne() {
        return this.createStackFromSlot(0);
    }

    public Stream<ItemStack> allItemsCopyStream() {
        return this.items.stream().map(i -> i.map(ItemStackTemplate::create).orElse(ItemStack.EMPTY));
    }

    private Stream<ItemStackTemplate> nonEmptyItemsStream() {
        return this.items.stream().flatMap(Optional::stream);
    }

    public Stream<ItemStack> nonEmptyItemCopyStream() {
        return this.nonEmptyItemsStream().map(ItemStackTemplate::create);
    }

    public Iterable<ItemStackTemplate> nonEmptyItems() {
        return () -> this.nonEmptyItemsStream().iterator();
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ? true : obj instanceof ItemContainerContents contents && this.items.equals(contents.items);
    }

    @Override
    public int hashCode() {
        return this.hashCode;
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> consumer, TooltipFlag flag, DataComponentGetter components) {
        int lineCount = 0;
        int itemCount = 0;

        for (Optional<ItemStackTemplate> item : this.items) {
            if (!item.isEmpty()) {
                itemCount++;
                if (lineCount <= 4) {
                    lineCount++;
                    ItemStack itemStack = item.get().create();
                    consumer.accept(Component.translatable("item.container.item_count", itemStack.getHoverName(), itemStack.getCount()));
                }
            }
        }

        if (itemCount - lineCount > 0) {
            consumer.accept(Component.translatable("item.container.more_items", itemCount - lineCount).withStyle(ChatFormatting.ITALIC));
        }
    }

    /**
     * Neo:
     * {@return the number of slots in this container}
     */
    public int getSlots() {
        return this.items.size();
    }

    /**
     * Neo: Gets a copy of the stack at a particular slot.
     *
     * @param slot The slot to check. Must be within [0, {@link #getSlots()}]
     * @return A copy of the stack in that slot
     * @throws UnsupportedOperationException if the provided slot index is out-of-bounds.
     */
    public ItemStack getStackInSlot(int slot) {
        validateSlotIndex(slot);
        // TODO 26.1: Migrate to ItemStackTemplate or ItemInstance
        return this.items.get(slot).map(ItemStackTemplate::create).orElse(ItemStack.EMPTY);
    }

    /**
     * Neo: Throws {@link UnsupportedOperationException} if the provided slot index is invalid.
     */
    private void validateSlotIndex(int slot) {
        if (slot < 0 || slot >= getSlots()) {
            throw new UnsupportedOperationException("Slot " + slot + " not in valid range - [0," + getSlots() + ")");
        }
    }

    private record Slot(int index, ItemStackTemplate item) {
        public static final Codec<ItemContainerContents.Slot> CODEC = RecordCodecBuilder.create(
            i -> i.group(
                    Codec.intRange(0, 255).fieldOf("slot").forGetter(ItemContainerContents.Slot::index),
                    ItemStackTemplate.CODEC.fieldOf("item").forGetter(ItemContainerContents.Slot::item)
                )
                .apply(i, ItemContainerContents.Slot::new)
        );
    }
}
