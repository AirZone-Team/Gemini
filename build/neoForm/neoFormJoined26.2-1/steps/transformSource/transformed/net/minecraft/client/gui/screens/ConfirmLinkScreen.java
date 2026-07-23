package net.minecraft.client.gui.screens;

import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.net.URI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ConfirmLinkScreen extends ConfirmScreen {
    private static final Component WARNING_TEXT = Component.translatable("chat.link.warning").withColor(-13108);
    private static final int BUTTON_WIDTH = 100;
    private final String url;
    private final boolean showWarning;

    public ConfirmLinkScreen(BooleanConsumer callback, String url, boolean trusted) {
        this(callback, confirmMessage(trusted), Component.literal(url), url, trusted ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_NO, trusted);
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, String url, boolean trusted) {
        this(callback, title, confirmMessage(trusted, url), url, trusted ? CommonComponents.GUI_CANCEL : CommonComponents.GUI_NO, trusted);
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, URI uri, boolean trusted) {
        this(callback, title, uri.toString(), trusted);
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, Component message, URI uri, Component noButton, boolean trusted) {
        this(callback, title, message, uri.toString(), noButton, trusted);
    }

    public ConfirmLinkScreen(BooleanConsumer callback, Component title, Component message, String url, Component noButtonComponent, boolean trusted) {
        super(callback, title, message);
        this.yesButtonComponent = trusted ? CommonComponents.GUI_OPEN_IN_BROWSER : CommonComponents.GUI_YES;
        this.noButtonComponent = noButtonComponent;
        this.showWarning = !trusted;
        this.url = url;
    }

    protected static MutableComponent confirmMessage(boolean trusted, String url) {
        return confirmMessage(trusted).append(CommonComponents.SPACE).append(Component.literal(url));
    }

    protected static MutableComponent confirmMessage(boolean trusted) {
        return Component.translatable(trusted ? "chat.link.confirmTrusted" : "chat.link.confirm");
    }

    @Override
    protected void addAdditionalText() {
        if (this.showWarning) {
            this.layout.addChild(new StringWidget(WARNING_TEXT, this.font));
        }
    }

    @Override
    protected void addButtons(LinearLayout buttonLayout) {
        this.yesButton = buttonLayout.addChild(Button.builder(this.yesButtonComponent, button -> this.callback.accept(true)).width(100).build());
        buttonLayout.addChild(Button.builder(CommonComponents.GUI_COPY_TO_CLIPBOARD, button -> {
            this.copyToClipboard();
            this.callback.accept(false);
        }).width(100).build());
        this.noButton = buttonLayout.addChild(Button.builder(this.noButtonComponent, button -> this.callback.accept(false)).width(100).build());
    }

    public void copyToClipboard() {
        this.minecraft.keyboardHandler.setClipboard(this.url);
    }

    public static void confirmLinkNow(Screen parentScreen, String uri, boolean trusted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new ConfirmLinkScreen(shouldOpen -> {
            if (shouldOpen) {
                Util.getPlatform().openUri(uri);
            }

            minecraft.gui.setScreen(parentScreen);
        }, uri, trusted));
    }

    public static void confirmLinkNow(@Nullable Screen parentScreen, URI uri, boolean trusted) {
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.gui.setScreen(new ConfirmLinkScreen(shouldOpen -> {
            if (shouldOpen) {
                Util.getPlatform().openUri(uri);
            }

            minecraft.gui.setScreen(parentScreen);
        }, uri.toString(), trusted));
    }

    public static void confirmLinkNow(@Nullable Screen parentScreen, URI uri) {
        confirmLinkNow(parentScreen, uri, true);
    }

    public static void confirmLinkNow(Screen parentScreen, String uri) {
        confirmLinkNow(parentScreen, uri, true);
    }

    public static Button.OnPress confirmLink(Screen parentScreen, String uri, boolean trusted) {
        return button -> confirmLinkNow(parentScreen, uri, trusted);
    }

    public static Button.OnPress confirmLink(Screen parentScreen, URI uri, boolean trusted) {
        return button -> confirmLinkNow(parentScreen, uri, trusted);
    }

    public static Button.OnPress confirmLink(Screen parentScreen, String uri) {
        return confirmLink(parentScreen, uri, true);
    }

    public static Button.OnPress confirmLink(Screen parentScreen, URI uri) {
        return confirmLink(parentScreen, uri, true);
    }
}
