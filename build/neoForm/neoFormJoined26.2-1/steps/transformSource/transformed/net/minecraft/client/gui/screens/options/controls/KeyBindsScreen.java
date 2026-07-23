package net.minecraft.client.gui.screens.options.controls;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class KeyBindsScreen extends OptionsSubScreen {
    private static final Component TITLE = Component.translatable("controls.keybinds.title");
    public @Nullable KeyMapping selectedKey;
    public long lastKeySelection;
    private KeyBindsList keyBindsList;
    private Button resetButton;
    // Neo: These are to hold the last key and modifier pressed so they can be checked in keyReleased
    private InputConstants.Key lastPressedKey = InputConstants.UNKNOWN;
    private InputConstants.Key lastPressedModifier = InputConstants.UNKNOWN;
    private boolean isLastKeyHeldDown = false;
    private boolean isLastModifierHeldDown = false;

    public KeyBindsScreen(Screen lastScreen, Options options) {
        super(lastScreen, options, TITLE);
    }

    @Override
    protected void addContents() {
        this.keyBindsList = this.layout.addToContents(new KeyBindsList(this, this.minecraft));
    }

    @Override
    protected void addOptions() {
    }

    @Override
    protected void addFooter() {
        this.resetButton = Button.builder(Component.translatable("controls.resetAll"), button -> {
            for (KeyMapping key : this.options.keyMappings) {
                key.setToDefault();
            }

            this.keyBindsList.resetMappingAndUpdateButtons();
        }).build();
        LinearLayout bottomButtons = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        bottomButtons.addChild(this.resetButton);
        bottomButtons.addChild(Button.builder(CommonComponents.GUI_DONE, button -> this.onClose()).build());
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
        this.keyBindsList.updateSize(this.width, this.layout);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.selectedKey != null) {
            this.selectedKey.setKey(InputConstants.Type.MOUSE.getOrCreate(event.button()));
            this.selectedKey = null;
            this.keyBindsList.resetMappingAndUpdateButtons();
            return true;
        } else {
            return super.mouseClicked(event, doubleClick);
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.selectedKey != null) {
            var key = InputConstants.getKey(event);
            if (this.lastPressedModifier == InputConstants.UNKNOWN && net.neoforged.neoforge.client.settings.KeyModifier.isKeyCodeModifier(key)) {
                this.lastPressedModifier = key;
                this.isLastModifierHeldDown = true;
            } else {
                this.lastPressedKey = key;
                this.isLastKeyHeldDown = true;
            }
            return true;
        } else {
            return super.keyPressed(event);
        }
    }

    @Override
    public boolean keyReleased(KeyEvent event) {
        // We ignore events from keys with the scan code 63 as they're emitted
        // (only as RELEASE, not PRESS) by Mac systems to indicate that "Fn" is being pressed
        // See https://github.com/neoforged/NeoForge/issues/1683
        if (this.selectedKey != null && (!net.minecraft.client.input.InputQuirks.ON_OSX || event.scancode() != 63)) {
            if (event.isEscape()) {
                this.selectedKey.setKeyModifierAndCode(net.neoforged.neoforge.client.settings.KeyModifier.NONE, InputConstants.UNKNOWN);
                this.selectedKey.setKey(InputConstants.UNKNOWN);
                this.lastPressedKey = InputConstants.UNKNOWN;
                this.lastPressedModifier = InputConstants.UNKNOWN;
                this.isLastKeyHeldDown = false;
                this.isLastModifierHeldDown = false;
            } else {
                var key = InputConstants.getKey(event);
                if (this.lastPressedKey.equals(key)) {
                    this.isLastKeyHeldDown = false;
                } else if (this.lastPressedModifier.equals(key)) {
                    this.isLastModifierHeldDown = false;
                }

                if (!this.isLastKeyHeldDown && !this.isLastModifierHeldDown) {
                    if (!this.lastPressedKey.equals(InputConstants.UNKNOWN)) {
                        this.selectedKey.setKeyModifierAndCode(
                                net.neoforged.neoforge.client.settings.KeyModifier.getKeyModifier(this.lastPressedModifier),
                                this.lastPressedKey
                        );
                        this.selectedKey.setKey(this.lastPressedKey);
                    } else {
                        this.selectedKey.setKeyModifierAndCode(
                                net.neoforged.neoforge.client.settings.KeyModifier.NONE,
                                this.lastPressedModifier
                        );
                        this.selectedKey.setKey(this.lastPressedModifier);
                    }
                    this.lastPressedKey = InputConstants.UNKNOWN;
                    this.lastPressedModifier = InputConstants.UNKNOWN;
                } else {
                    return true;
                }
            }
            this.selectedKey = null;
            this.lastKeySelection = Util.getMillis();
            this.keyBindsList.resetMappingAndUpdateButtons();
            return true;
        } else {
            return super.keyReleased(event);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        super.extractRenderState(graphics, mouseX, mouseY, a);
        boolean canReset = false;

        for (KeyMapping key : this.options.keyMappings) {
            if (!key.isDefault()) {
                canReset = true;
                break;
            }
        }

        this.resetButton.active = canReset;
    }
}
