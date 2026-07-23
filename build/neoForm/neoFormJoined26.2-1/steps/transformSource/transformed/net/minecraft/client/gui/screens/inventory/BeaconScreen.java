package net.minecraft.client.gui.screens.inventory;

import com.google.common.collect.Lists;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.inventory.ContainerListener;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class BeaconScreen extends AbstractContainerScreen<BeaconMenu> {
    private static final Identifier BEACON_LOCATION = Identifier.withDefaultNamespace("textures/gui/container/beacon.png");
    private static final Identifier BUTTON_DISABLED_SPRITE = Identifier.withDefaultNamespace("container/beacon/button_disabled");
    private static final Identifier BUTTON_SELECTED_SPRITE = Identifier.withDefaultNamespace("container/beacon/button_selected");
    private static final Identifier BUTTON_HIGHLIGHTED_SPRITE = Identifier.withDefaultNamespace("container/beacon/button_highlighted");
    private static final Identifier BUTTON_SPRITE = Identifier.withDefaultNamespace("container/beacon/button");
    private static final Identifier CONFIRM_SPRITE = Identifier.withDefaultNamespace("container/beacon/confirm");
    private static final Identifier CANCEL_SPRITE = Identifier.withDefaultNamespace("container/beacon/cancel");
    private static final Component PRIMARY_EFFECT_LABEL = Component.translatable("block.minecraft.beacon.primary");
    private static final Component SECONDARY_EFFECT_LABEL = Component.translatable("block.minecraft.beacon.secondary");
    private final List<BeaconScreen.BeaconButton> beaconButtons = Lists.newArrayList();
    private @Nullable Holder<MobEffect> primary;
    private @Nullable Holder<MobEffect> secondary;

    public BeaconScreen(BeaconMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 230, 219);
        menu.addSlotListener(new ContainerListener() {
            @Override
            public void slotChanged(AbstractContainerMenu container, int slotIndex, ItemStack itemStack) {
            }

            @Override
            public void dataChanged(AbstractContainerMenu container, int id, int value) {
                BeaconScreen.this.primary = menu.getPrimaryEffect();
                BeaconScreen.this.secondary = menu.getSecondaryEffect();
            }
        });
    }

    private <T extends AbstractWidget & BeaconScreen.BeaconButton> void addBeaconButton(T beaconButton) {
        this.addRenderableWidget(beaconButton);
        this.beaconButtons.add(beaconButton);
    }

    @Override
    protected void init() {
        super.init();
        this.beaconButtons.clear();

        for (int tier = 0; tier <= 2; tier++) {
            int count = BeaconBlockEntity.BEACON_EFFECTS.get(tier).size();
            int totalWidth = count * 22 + (count - 1) * 2;

            for (int c = 0; c < count; c++) {
                Holder<MobEffect> effect = BeaconBlockEntity.BEACON_EFFECTS.get(tier).get(c);
                BeaconScreen.BeaconPowerButton beaconPowerButton = new BeaconScreen.BeaconPowerButton(
                    this.leftPos + 76 + c * 24 - totalWidth / 2, this.topPos + 22 + tier * 25, effect, true, tier
                );
                beaconPowerButton.active = false;
                this.addBeaconButton(beaconPowerButton);
            }
        }

        int tier = 3;
        int count = BeaconBlockEntity.BEACON_EFFECTS.get(3).size() + 1;
        int totalWidth = count * 22 + (count - 1) * 2;

        for (int c = 0; c < count - 1; c++) {
            Holder<MobEffect> effect = BeaconBlockEntity.BEACON_EFFECTS.get(3).get(c);
            BeaconScreen.BeaconPowerButton beaconPowerButton = new BeaconScreen.BeaconPowerButton(
                this.leftPos + 167 + c * 24 - totalWidth / 2, this.topPos + 47, effect, false, 3
            );
            beaconPowerButton.active = false;
            this.addBeaconButton(beaconPowerButton);
        }

        Holder<MobEffect> dummyEffect = BeaconBlockEntity.BEACON_EFFECTS.get(0).get(0);
        BeaconScreen.BeaconPowerButton beaconPowerButton = new BeaconScreen.BeaconUpgradePowerButton(
            this.leftPos + 167 + (count - 1) * 24 - totalWidth / 2, this.topPos + 47, dummyEffect
        );
        beaconPowerButton.visible = false;
        this.addBeaconButton(beaconPowerButton);
        this.addBeaconButton(new BeaconScreen.BeaconConfirmButton(this.leftPos + 164, this.topPos + 107));
        this.addBeaconButton(new BeaconScreen.BeaconCancelButton(this.leftPos + 190, this.topPos + 107));
    }

    @Override
    public void containerTick() {
        super.containerTick();
        this.updateButtons();
    }

    private void updateButtons() {
        int levels = this.menu.getLevels();
        this.beaconButtons.forEach(b -> b.updateStatus(levels));
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int xm, int ym) {
        graphics.centeredText(this.font, PRIMARY_EFFECT_LABEL, 62, 10, -2039584);
        graphics.centeredText(this.font, SECONDARY_EFFECT_LABEL, 169, 10, -2039584);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractBackground(graphics, mouseX, mouseY, a);
        int xo = (this.width - this.imageWidth) / 2;
        int yo = (this.height - this.imageHeight) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, BEACON_LOCATION, xo, yo, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
        graphics.item(new ItemStack(Items.NETHERITE_INGOT), xo + 20, yo + 109);
        graphics.item(new ItemStack(Items.EMERALD), xo + 41, yo + 109);
        graphics.item(new ItemStack(Items.DIAMOND), xo + 41 + 22, yo + 109);
        graphics.item(new ItemStack(Items.GOLD_INGOT), xo + 42 + 44, yo + 109);
        graphics.item(new ItemStack(Items.IRON_INGOT), xo + 42 + 66, yo + 109);
    }

    private interface BeaconButton {
        void updateStatus(final int levels);
    }

    private class BeaconCancelButton extends BeaconScreen.BeaconSpriteScreenButton {
        public BeaconCancelButton(int x, int y) {
            super(x, y, BeaconScreen.CANCEL_SPRITE, CommonComponents.GUI_CANCEL);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            BeaconScreen.this.minecraft.player.closeContainer();
        }

        @Override
        public void updateStatus(int levels) {
        }
    }

    private class BeaconConfirmButton extends BeaconScreen.BeaconSpriteScreenButton {
        public BeaconConfirmButton(int x, int y) {
            super(x, y, BeaconScreen.CONFIRM_SPRITE, CommonComponents.GUI_DONE);
        }

        @Override
        public void onPress(InputWithModifiers input) {
            BeaconScreen.this.minecraft
                .getConnection()
                .send(new ServerboundSetBeaconPacket(Optional.ofNullable(BeaconScreen.this.primary), Optional.ofNullable(BeaconScreen.this.secondary)));
            BeaconScreen.this.minecraft.player.closeContainer();
        }

        @Override
        public void updateStatus(int levels) {
            this.active = BeaconScreen.this.menu.hasPayment() && BeaconScreen.this.primary != null;
        }
    }

    private class BeaconPowerButton extends BeaconScreen.BeaconScreenButton {
        private final boolean isPrimary;
        protected final int tier;
        private Holder<MobEffect> effect;
        private Identifier sprite;

        public BeaconPowerButton(int x, int y, Holder<MobEffect> effect, boolean isPrimary, int tier) {
            super(x, y);
            this.isPrimary = isPrimary;
            this.tier = tier;
            this.setEffect(effect);
        }

        protected void setEffect(Holder<MobEffect> effect) {
            this.effect = effect;
            this.sprite = Hud.getMobEffectSprite(effect);
            this.setTooltip(Tooltip.create(this.createEffectDescription(effect), null));
        }

        protected MutableComponent createEffectDescription(Holder<MobEffect> effect) {
            return Component.translatable(effect.value().getDescriptionId());
        }

        @Override
        public void onPress(InputWithModifiers input) {
            if (!this.isSelected()) {
                if (this.isPrimary) {
                    BeaconScreen.this.primary = this.effect;
                    if (!Objects.equals(BeaconScreen.this.secondary, this.effect)) {
                        BeaconScreen.this.secondary = null;
                    }
                } else {
                    BeaconScreen.this.secondary = this.effect;
                }

                BeaconScreen.this.updateButtons();
            }
        }

        @Override
        protected void extractIcon(GuiGraphicsExtractor graphics) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, this.getX() + 2, this.getY() + 2, 18, 18);
        }

        @Override
        public void updateStatus(int levels) {
            this.active = this.tier < levels;
            this.setSelected(this.effect.equals(this.isPrimary ? BeaconScreen.this.primary : BeaconScreen.this.secondary));
        }

        @Override
        protected MutableComponent createNarrationMessage() {
            return this.createEffectDescription(this.effect);
        }
    }

    private abstract static class BeaconScreenButton extends AbstractButton implements BeaconScreen.BeaconButton {
        private boolean selected;

        protected BeaconScreenButton(int x, int y) {
            super(x, y, 22, 22, CommonComponents.EMPTY);
        }

        protected BeaconScreenButton(int x, int y, Component component) {
            super(x, y, 22, 22, component);
        }

        @Override
        public void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
            Identifier sprite;
            if (!this.active) {
                sprite = BeaconScreen.BUTTON_DISABLED_SPRITE;
            } else if (this.selected) {
                sprite = BeaconScreen.BUTTON_SELECTED_SPRITE;
            } else if (this.isHoveredOrFocused()) {
                sprite = BeaconScreen.BUTTON_HIGHLIGHTED_SPRITE;
            } else {
                sprite = BeaconScreen.BUTTON_SPRITE;
            }

            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, this.getX(), this.getY(), this.width, this.height);
            this.extractIcon(graphics);
        }

        protected abstract void extractIcon(final GuiGraphicsExtractor graphics);

        public boolean isSelected() {
            return this.selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public void updateWidgetNarration(NarrationElementOutput output) {
            this.defaultButtonNarrationText(output);
        }
    }

    private abstract static class BeaconSpriteScreenButton extends BeaconScreen.BeaconScreenButton {
        private final Identifier sprite;

        protected BeaconSpriteScreenButton(int x, int y, Identifier sprite, Component label) {
            super(x, y, label);
            this.setTooltip(Tooltip.create(label));
            this.sprite = sprite;
        }

        @Override
        protected void extractIcon(GuiGraphicsExtractor graphics) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, this.sprite, this.getX() + 2, this.getY() + 2, 18, 18);
        }
    }

    private class BeaconUpgradePowerButton extends BeaconScreen.BeaconPowerButton {
        public BeaconUpgradePowerButton(int x, int y, Holder<MobEffect> effect) {
            super(x, y, effect, false, 3);
        }

        @Override
        protected MutableComponent createEffectDescription(Holder<MobEffect> effect) {
            return Component.translatable(effect.value().getDescriptionId()).append(" II");
        }

        @Override
        public void updateStatus(int levels) {
            if (BeaconScreen.this.primary != null) {
                this.visible = true;
                this.setEffect(BeaconScreen.this.primary);
                super.updateStatus(levels);
            } else {
                this.visible = false;
            }
        }
    }
}
