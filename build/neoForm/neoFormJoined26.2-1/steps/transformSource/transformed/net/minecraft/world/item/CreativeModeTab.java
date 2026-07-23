package net.minecraft.world.item;

import java.util.Collection;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.HolderLookup;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.level.ItemLike;
import org.jspecify.annotations.Nullable;

public class CreativeModeTab {
    private static final net.minecraft.resources.Identifier SCROLLER_SPRITE = net.minecraft.resources.Identifier.withDefaultNamespace("container/creative_inventory/scroller");
    private static final net.minecraft.resources.Identifier SCROLLER_DISABLED_SPRITE = net.minecraft.resources.Identifier.withDefaultNamespace("container/creative_inventory/scroller_disabled");
    private static final Identifier DEFAULT_BACKGROUND = createTextureLocation("items");
    private final Component displayName;
    private Identifier backgroundTexture = DEFAULT_BACKGROUND;
    private boolean canScroll = true;
    private boolean showTitle = true;
    private boolean alignedRight = false;
    private final CreativeModeTab.Row row;
    private final int column;
    private final CreativeModeTab.Type type;
    private @Nullable ItemStack iconItemStack;
    private Collection<ItemStack> displayItems = ItemStackLinkedSet.createTypeAndComponentsSet();
    private Set<ItemStack> displayItemsSearchTab = ItemStackLinkedSet.createTypeAndComponentsSet();
    private final Supplier<ItemStack> iconGenerator;
    private final CreativeModeTab.DisplayItemsGenerator displayItemsGenerator;
    private final net.minecraft.resources.@Nullable Identifier scrollerSpriteLocation;
    private final boolean hasSearchBar;
    private final int searchBarWidth;
    private final net.minecraft.resources.Identifier tabsImage;
    private final int labelColor;
    public final java.util.List<net.minecraft.resources.Identifier> tabsBefore;
    public final java.util.List<net.minecraft.resources.Identifier> tabsAfter;

    private CreativeModeTab(
        CreativeModeTab.Row row,
        int column,
        CreativeModeTab.Type type,
        Component displayName,
        Supplier<ItemStack> iconGenerator,
        CreativeModeTab.DisplayItemsGenerator displayItemsGenerator,
        net.minecraft.resources.Identifier scrollerSpriteLocation,
        boolean hasSearchBar,
        int searchBarWidth,
        net.minecraft.resources.Identifier tabsImage,
        int labelColor,
        java.util.List<net.minecraft.resources.Identifier> tabsBefore,
        java.util.List<net.minecraft.resources.Identifier> tabsAfter
    ) {
        this.row = row;
        this.column = column;
        this.displayName = displayName;
        this.iconGenerator = iconGenerator;
        this.displayItemsGenerator = displayItemsGenerator;
        this.type = type;
        this.scrollerSpriteLocation = scrollerSpriteLocation;
        this.hasSearchBar = hasSearchBar;
        this.searchBarWidth = searchBarWidth;
        this.tabsImage = tabsImage;
        this.labelColor = labelColor;
        this.tabsBefore = java.util.List.copyOf(tabsBefore);
        this.tabsAfter = java.util.List.copyOf(tabsAfter);
    }

    protected CreativeModeTab(CreativeModeTab.Builder builder) {
        this(builder.row, builder.column, builder.type, builder.displayName, builder.iconGenerator, builder.displayItemsGenerator, builder.spriteScrollerLocation, builder.hasSearchBar, builder.searchBarWidth, builder.tabsImage, builder.labelColor, builder.tabsBefore, builder.tabsAfter);
    }

    public static CreativeModeTab.Builder builder() {
        return new CreativeModeTab.Builder(Row.TOP, 0);
    }

    public static Identifier createTextureLocation(String name) {
        return Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tab_" + name + ".png");
    }

    /** @deprecated Forge: use {@link #builder()} **/ @Deprecated
    public static CreativeModeTab.Builder builder(CreativeModeTab.Row row, int column) {
        return new CreativeModeTab.Builder(row, column);
    }

    public Component getDisplayName() {
        return this.displayName;
    }

    public ItemStack getIconItem() {
        if (this.iconItemStack == null) {
            this.iconItemStack = this.iconGenerator.get();
        }

        return this.iconItemStack;
    }

    public Identifier getBackgroundTexture() {
        return this.backgroundTexture;
    }

    public boolean showTitle() {
        return this.showTitle;
    }

    public boolean canScroll() {
        return this.canScroll;
    }

    public int column() {
        return this.column;
    }

    public CreativeModeTab.Row row() {
        return this.row;
    }

    public boolean hasAnyItems() {
        return !this.displayItems.isEmpty();
    }

    public boolean shouldDisplay() {
        return this.type != CreativeModeTab.Type.CATEGORY || this.hasAnyItems();
    }

    public boolean isAlignedRight() {
        return this.alignedRight;
    }

    public CreativeModeTab.Type getType() {
        return this.type;
    }

    public void buildContents(CreativeModeTab.ItemDisplayParameters parameters) {
        CreativeModeTab.ItemDisplayBuilder displayList = new CreativeModeTab.ItemDisplayBuilder(this, parameters.enabledFeatures);
        net.neoforged.neoforge.event.EventHooks.onCreativeModeTabBuildContents(this, this.displayItemsGenerator, parameters, displayList);
        this.displayItems = displayList.tabContents;
        this.displayItemsSearchTab = displayList.searchTabContents;
    }

    public Collection<ItemStack> getDisplayItems() {
        return this.displayItems;
    }

    public Collection<ItemStack> getSearchTabDisplayItems() {
        return this.displayItemsSearchTab;
    }

    public boolean contains(ItemStack stack) {
        return this.displayItemsSearchTab.contains(stack);
    }

    public boolean hasSearchBar() {
        return this.hasSearchBar;
    }

    public int getSearchBarWidth() {
        return searchBarWidth;
    }

    public net.minecraft.resources.Identifier getTabsImage() {
        return tabsImage;
    }

    public int getLabelColor() {
        return labelColor;
    }

    public net.minecraft.resources.Identifier getScrollerSprite() {
        if (scrollerSpriteLocation == null)
            return this.canScroll() ? SCROLLER_SPRITE : SCROLLER_DISABLED_SPRITE;
        return scrollerSpriteLocation;
    }

    public static class Builder {
        private static final CreativeModeTab.DisplayItemsGenerator EMPTY_GENERATOR = (parameters, output) -> {};
        private static final net.minecraft.resources.Identifier CREATIVE_INVENTORY_TABS_IMAGE = net.minecraft.resources.Identifier.withDefaultNamespace("textures/gui/container/creative_inventory/tabs.png");
        private static final net.minecraft.resources.Identifier CREATIVE_ITEM_SEARCH_BACKGROUND = createTextureLocation("item_search");
        private final CreativeModeTab.Row row;
        private final int column;
        private Component displayName = Component.empty();
        private Supplier<ItemStack> iconGenerator = () -> ItemStack.EMPTY;
        private CreativeModeTab.DisplayItemsGenerator displayItemsGenerator = EMPTY_GENERATOR;
        private boolean canScroll = true;
        private boolean showTitle = true;
        private boolean alignedRight = false;
        private CreativeModeTab.Type type = CreativeModeTab.Type.CATEGORY;
        private Identifier backgroundTexture = CreativeModeTab.DEFAULT_BACKGROUND;
        private net.minecraft.resources.@Nullable Identifier spriteScrollerLocation;
        private boolean hasSearchBar = false;
        private int searchBarWidth = 89;
        private net.minecraft.resources.Identifier tabsImage = CREATIVE_INVENTORY_TABS_IMAGE;
        private int labelColor = -12566464;
        private java.util.function.Function<CreativeModeTab.Builder, CreativeModeTab> tabFactory = CreativeModeTab::new;
        private final java.util.List<net.minecraft.resources.Identifier> tabsBefore = new java.util.ArrayList<>();
        private final java.util.List<net.minecraft.resources.Identifier> tabsAfter = new java.util.ArrayList<>();

        public Builder(CreativeModeTab.Row row, int column) {
            this.row = row;
            this.column = column;
        }

        public CreativeModeTab.Builder title(Component displayName) {
            this.displayName = displayName;
            return this;
        }

        public CreativeModeTab.Builder icon(Supplier<ItemStack> iconGenerator) {
            this.iconGenerator = iconGenerator;
            return this;
        }

        public CreativeModeTab.Builder displayItems(CreativeModeTab.DisplayItemsGenerator displayItemsGenerator) {
            this.displayItemsGenerator = displayItemsGenerator;
            return this;
        }

        public CreativeModeTab.Builder alignedRight() {
            this.alignedRight = true;
            return this;
        }

        public CreativeModeTab.Builder hideTitle() {
            this.showTitle = false;
            return this;
        }

        public CreativeModeTab.Builder noScrollBar() {
            this.canScroll = false;
            return this;
        }

        protected CreativeModeTab.Builder type(CreativeModeTab.Type type) {
            this.type = type;
            if (type == Type.SEARCH)
                return this.withSearchBar();
            return this;
        }

        public CreativeModeTab.Builder backgroundTexture(Identifier backgroundTexture) {
            this.backgroundTexture = backgroundTexture;
            return this;
        }

        /**
         * Gives this tab a search bar.
         * <p>Note that, if using a custom {@link #backgroundTexture(net.minecraft.resources.Identifier) background image}, you will need to make sure that your image contains the input box and the scroll bar.</p>
         */
        public CreativeModeTab.Builder withSearchBar() {
            this.hasSearchBar = true;
            if (this.backgroundTexture == CreativeModeTab.DEFAULT_BACKGROUND)
                return this.backgroundTexture(CREATIVE_ITEM_SEARCH_BACKGROUND);
            return this;
        }

        /**
         * Gives this tab a search bar, with a specific width.
         * @param searchBarWidth the width of the search bar
         */
        public CreativeModeTab.Builder withSearchBar(int searchBarWidth) {
            this.searchBarWidth = searchBarWidth;
            return withSearchBar();
        }

        /**
         * Sets the location of the scroll bar background.
         */
        public CreativeModeTab.Builder withScrollBarSpriteLocation(net.minecraft.resources.Identifier scrollBarSpriteLocation) {
            this.spriteScrollerLocation = scrollBarSpriteLocation;
            return this;
        }

        /**
         * Sets the image of the tab to a custom resource location, instead of an item's texture.
         */
        public CreativeModeTab.Builder withTabsImage(net.minecraft.resources.Identifier tabsImage) {
            this.tabsImage = tabsImage;
            return this;
        }

        /**
         * Sets the color of the tab label.
         */
        public CreativeModeTab.Builder withLabelColor(int labelColor) {
            this.labelColor = labelColor;
            return this;
        }

        public CreativeModeTab.Builder withTabFactory(java.util.function.Function<CreativeModeTab.Builder, CreativeModeTab> tabFactory) {
            this.tabFactory = tabFactory;
            return this;
        }

        /** Define tabs that should come <i>before</i> this tab. This tab will be placed <strong>after</strong> the {@code tabs}. **/
        public CreativeModeTab.Builder withTabsBefore(net.minecraft.resources.Identifier... tabs) {
            this.tabsBefore.addAll(java.util.List.of(tabs));
            return this;
        }

        /** Define tabs that should come <i>after</i> this tab. This tab will be placed <strong>before</strong> the {@code tabs}.**/
        public CreativeModeTab.Builder withTabsAfter(net.minecraft.resources.Identifier... tabs) {
            this.tabsAfter.addAll(java.util.List.of(tabs));
            return this;
        }

        /** Define tabs that should come <i>before</i> this tab. This tab will be placed <strong>after</strong> the {@code tabs}. **/
        @SafeVarargs
        public final CreativeModeTab.Builder withTabsBefore(net.minecraft.resources.ResourceKey<CreativeModeTab>... tabs) {
            java.util.stream.Stream.of(tabs).map(net.minecraft.resources.ResourceKey::identifier).forEach(this.tabsBefore::add);
            return this;
        }

        /** Define tabs that should come <i>after</i> this tab. This tab will be placed <strong>before</strong> the {@code tabs}.**/
        @SafeVarargs
        public final CreativeModeTab.Builder withTabsAfter(net.minecraft.resources.ResourceKey<CreativeModeTab>... tabs) {
            java.util.stream.Stream.of(tabs).map(net.minecraft.resources.ResourceKey::identifier).forEach(this.tabsAfter::add);
            return this;
        }

        /**
         * Helper to set this tabs contents to everything in the supplied Collection of Holders.
         * Intended for use with {@link net.neoforged.neoforge.registries.DeferredRegister#getEntries()}, for Item or Block DeferredRegisters.
         * Entries added through this method are filtered out if {@link ItemLike#asItem()} returns {@link Items#AIR} or if disabled via {@link Item#isEnabled(FeatureFlagSet)}.
         * Note that like the vanilla method this overrides any other calls for this tab to {@link #displayItems}.
         */
        public CreativeModeTab.Builder displayItems(Collection<? extends net.minecraft.core.Holder<? extends ItemLike>> collection) {
            return this.displayItems((p, o) -> collection.stream()
                    .map(net.minecraft.core.Holder::value)
                    .map(ItemLike::asItem)
                    .filter(i -> i != Items.AIR)
                    .filter(i -> i.isEnabled(p.enabledFeatures()))
                    .forEach(o::accept));
        }

        public CreativeModeTab build() {
            if ((this.type == CreativeModeTab.Type.HOTBAR || this.type == CreativeModeTab.Type.INVENTORY) && this.displayItemsGenerator != EMPTY_GENERATOR) {
                throw new IllegalStateException("Special tabs can't have display items");
            }

            CreativeModeTab tab = tabFactory.apply(this);
            tab.alignedRight = this.alignedRight;
            tab.showTitle = this.showTitle;
            tab.canScroll = this.canScroll;
            tab.backgroundTexture = this.backgroundTexture;
            return tab;
        }
    }

    @FunctionalInterface
    public interface DisplayItemsGenerator {
        void accept(CreativeModeTab.ItemDisplayParameters parameters, CreativeModeTab.Output output);
    }

    private static class ItemDisplayBuilder implements CreativeModeTab.Output {
        public final Collection<ItemStack> tabContents = ItemStackLinkedSet.createTypeAndComponentsSet();
        public final Set<ItemStack> searchTabContents = ItemStackLinkedSet.createTypeAndComponentsSet();
        private final CreativeModeTab tab;
        private final FeatureFlagSet featureFlagSet;

        public ItemDisplayBuilder(CreativeModeTab tab, FeatureFlagSet featureFlagSet) {
            this.tab = tab;
            this.featureFlagSet = featureFlagSet;
        }

        @Override
        public void accept(ItemStack stack, CreativeModeTab.TabVisibility tabVisibility) {
            if (stack.getCount() != 1) {
                throw new IllegalArgumentException("Stack size must be exactly 1");
            }

            boolean foundDuplicateStack = this.tabContents.contains(stack) && tabVisibility != CreativeModeTab.TabVisibility.SEARCH_TAB_ONLY;
            if (foundDuplicateStack) {
                throw new IllegalStateException(
                    "Accidentally adding the same item stack twice "
                        + stack.getDisplayName().getString()
                        + " to a Creative Mode Tab: "
                        + this.tab.getDisplayName().getString()
                );
            }

            if (stack.getItem().isEnabled(this.featureFlagSet)) {
                switch (tabVisibility) {
                    case PARENT_AND_SEARCH_TABS:
                        this.tabContents.add(stack);
                        this.searchTabContents.add(stack);
                        break;
                    case PARENT_TAB_ONLY:
                        this.tabContents.add(stack);
                        break;
                    case SEARCH_TAB_ONLY:
                        this.searchTabContents.add(stack);
                }
            }
        }
    }

    public record ItemDisplayParameters(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders) {
        public boolean needsUpdate(FeatureFlagSet enabledFeatures, boolean hasPermissions, HolderLookup.Provider holders) {
            return !this.enabledFeatures.equals(enabledFeatures) || this.hasPermissions != hasPermissions || this.holders != holders;
        }
    }

    public interface Output {
        void accept(final ItemStack stack, final CreativeModeTab.TabVisibility tabVisibility);

        default void accept(ItemStack stack) {
            this.accept(stack, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void accept(ItemLike item, CreativeModeTab.TabVisibility tabVisibility) {
            this.accept(new ItemStack(item), tabVisibility);
        }

        default void accept(ItemLike item) {
            this.accept(new ItemStack(item), CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }

        default void acceptAll(Collection<ItemStack> stacks, CreativeModeTab.TabVisibility tabVisibility) {
            stacks.forEach(stack -> this.accept(stack, tabVisibility));
        }

        default void acceptAll(Collection<ItemStack> stacks) {
            this.acceptAll(stacks, CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    public enum Row {
        TOP,
        BOTTOM;
    }

    public enum TabVisibility {
        PARENT_AND_SEARCH_TABS,
        PARENT_TAB_ONLY,
        SEARCH_TAB_ONLY;
    }

    public enum Type {
        CATEGORY,
        INVENTORY,
        HOTBAR,
        SEARCH;
    }
}
