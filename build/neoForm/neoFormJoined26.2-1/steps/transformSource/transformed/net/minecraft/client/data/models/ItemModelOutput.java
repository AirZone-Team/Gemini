package net.minecraft.client.data.models;

import net.minecraft.client.renderer.item.ClientItem;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.world.item.Item;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public interface ItemModelOutput {
    default void accept(Item item, ItemModel.Unbaked generator) {
        this.accept(item, generator, ClientItem.Properties.DEFAULT);
    }

    void accept(Item item, ItemModel.Unbaked generator, ClientItem.Properties properties);

    void copy(Item donor, Item acceptor);

    /**
     * Neo: Pulled up from {@link ModelProvider.ItemInfoCollector} to give modders full control over the {@link net.minecraft.client.renderer.item.ClientItem} instead of just the {@link ItemModel.Unbaked} in {@link #accept(Item, ItemModel.Unbaked)
     */
    default void register(Item item, net.minecraft.client.renderer.item.ClientItem clientItem) {
    }

    /**
     * Neo: Allow generating arbitrarily named client item files
     */
    default void register(net.minecraft.resources.Identifier identifier, net.minecraft.client.renderer.item.ClientItem clientItem) {
    }
}
