package net.minecraft.world.inventory;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

public class ResultSlot extends Slot {
    private final CraftingContainer craftSlots;
    private final Player player;
    private int removeCount;

    public ResultSlot(Player player, CraftingContainer craftSlots, Container container, int id, int x, int y) {
        super(container, id, x, y);
        this.player = player;
        this.craftSlots = craftSlots;
    }

    @Override
    public boolean mayPlace(ItemStack itemStack) {
        return false;
    }

    @Override
    public ItemStack remove(int amount) {
        if (this.hasItem()) {
            this.removeCount = this.removeCount + Math.min(amount, this.getItem().getCount());
        }

        return super.remove(amount);
    }

    @Override
    protected void onQuickCraft(ItemStack picked, int count) {
        this.removeCount += count;
        this.checkTakeAchievements(picked);
    }

    @Override
    protected void onSwapCraft(int count) {
        this.removeCount += count;
    }

    @Override
    public ItemStack safeClone(Player player) {
        ItemStack result = super.safeClone(player);
        result.getItem().onCraftedBy(result, player);
        return result;
    }

    @Override
    protected void checkTakeAchievements(ItemStack carried) {
        if (this.removeCount > 0) {
            carried.onCraftedBy(this.player, this.removeCount);
            net.neoforged.neoforge.event.EventHooks.firePlayerCraftingEvent(this.player, carried, this.craftSlots);
        }

        if (this.container instanceof RecipeCraftingHolder recipeCraftingHolder) {
            recipeCraftingHolder.awardUsedRecipes(this.player, this.craftSlots.getItems());
        }

        this.removeCount = 0;
    }

    private static NonNullList<ItemStack> copyAllInputItems(CraftingInput input) {
        NonNullList<ItemStack> result = NonNullList.withSize(input.size(), ItemStack.EMPTY);

        for (int slot = 0; slot < result.size(); slot++) {
            result.set(slot, input.getItem(slot));
        }

        return result;
    }

    private NonNullList<ItemStack> getRemainingItems(CraftingInput input, Level level) {
        return level instanceof ServerLevel serverLevel
            ? serverLevel.recipeAccess()
                .getRecipeFor(RecipeType.CRAFTING, input, serverLevel)
                .map(recipe -> recipe.value().getRemainingItems(input))
                .orElseGet(() -> copyAllInputItems(input))
            : CraftingRecipe.defaultCraftingReminder(input);
    }

    @Override
    public void onTake(Player player, ItemStack carried) {
        this.checkTakeAchievements(carried);
        CraftingInput.Positioned positionedRecipe = this.craftSlots.asPositionedCraftInput();
        CraftingInput input = positionedRecipe.input();
        int recipeLeft = positionedRecipe.left();
        int recipeTop = positionedRecipe.top();
        net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(player);
        NonNullList<ItemStack> remaining = this.getRemainingItems(input, player.level());
        net.neoforged.neoforge.common.CommonHooks.setCraftingPlayer(null);

        for (int y = 0; y < input.height(); y++) {
            for (int x = 0; x < input.width(); x++) {
                int slot = x + recipeLeft + (y + recipeTop) * this.craftSlots.getWidth();
                ItemStack itemStack = this.craftSlots.getItem(slot);
                ItemStack replacement = remaining.get(x + y * input.width());
                if (!itemStack.isEmpty()) {
                    this.craftSlots.removeItem(slot, 1);
                    itemStack = this.craftSlots.getItem(slot);
                }

                if (!replacement.isEmpty()) {
                    if (itemStack.isEmpty()) {
                        this.craftSlots.setItem(slot, replacement);
                    } else if (ItemStack.isSameItemSameComponents(itemStack, replacement)) {
                        replacement.grow(itemStack.getCount());
                        this.craftSlots.setItem(slot, replacement);
                    } else if (!this.player.getInventory().add(replacement)) {
                        this.player.drop(replacement, false);
                    }
                }
            }
        }
    }

    @Override
    public boolean isFake() {
        return true;
    }
}
