package net.minecraft.client.gui.components.toasts;

import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.gui.components.WidgetSprites;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class FriendToast implements Toast {
    private static final WidgetSprites BACKGROUND_SPRITE = new WidgetSprites(Identifier.withDefaultNamespace("friends/toast_background"));
    private static final int FACE_SIZE = 20;
    private static final int TEXT_LEFT_WITH_FACE = 30;
    private static final int TEXT_LEFT_NO_FACE = 7;
    private static final int PADDING_TOP = 7;
    private static final int PADDING_BOTTOM = 3;
    private static final int LINE_SPACING = 11;
    private static final long DEFAULT_DISPLAY_TIME_MS = 5000L;
    private final @Nullable ResolvableProfile skinProfile;
    private final List<FormattedCharSequence> messageLines;
    private final long displayTimeMs;
    private Toast.Visibility visibility = Toast.Visibility.SHOW;

    public FriendToast(Font font, @Nullable ResolvableProfile skinProfile, Component message) {
        this(font, skinProfile, message, 5000L);
    }

    public FriendToast(Font font, @Nullable ResolvableProfile skinProfile, Component message, long displayTimeMs) {
        this.skinProfile = skinProfile;
        int textLeft = skinProfile != null ? 30 : 7;
        this.messageLines = font.split(message, 160 - textLeft - 4);
        this.displayTimeMs = displayTimeMs;
    }

    @Override
    public Toast.Visibility getWantedVisibility() {
        return this.visibility;
    }

    @Override
    public void update(ToastManager manager, long fullyVisibleForMs) {
        if (fullyVisibleForMs >= this.displayTimeMs * manager.getNotificationDisplayTimeMultiplier()) {
            this.visibility = Toast.Visibility.HIDE;
        }
    }

    @Override
    public int height() {
        return 7 + this.contentHeight() + 3;
    }

    private int contentHeight() {
        return Math.max(this.messageLines.size(), 2) * 11;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, Font font, long fullyVisibleForMs) {
        int height = this.height();
        graphics.blitSprite(RenderPipelines.GUI_TEXTURED, BACKGROUND_SPRITE.get(true, false), 0, 0, this.width(), height);
        int textLeft;
        if (this.skinProfile != null) {
            PlayerFaceExtractor.extractRenderState(graphics, this.skinProfile, 6, 6, 20);
            textLeft = 30;
        } else {
            textLeft = 7;
        }

        int totalTextHeight = this.messageLines.size() * 11;
        int textTop = 7 + (this.contentHeight() - totalTextHeight) / 2;

        for (int i = 0; i < this.messageLines.size(); i++) {
            graphics.text(font, this.messageLines.get(i), textLeft, textTop + i * 11, -1, false);
        }
    }

    public void hide() {
        this.visibility = Toast.Visibility.HIDE;
    }

    public static void add(ToastManager toastManager, Font font, @Nullable ResolvableProfile skinProfile, Component message) {
        toastManager.addToast(new FriendToast(font, skinProfile, message));
    }

    private static void add(Minecraft minecraft, @Nullable ResolvableProfile skinProfile, Component message) {
        add(minecraft.gui.toastManager(), minecraft.font, skinProfile, message);
    }

    private static void addWithSkin(Minecraft minecraft, UUID playerId, Component message) {
        ResolvableProfile skinProfile = ResolvableProfile.createUnresolved(playerId);
        add(minecraft, skinProfile, message);
    }

    public static void showFriendRequestSent(Minecraft minecraft, String nickname) {
        add(minecraft, null, Component.translatable("gui.friends.toast.request_sent.message", nickname));
    }

    public static void showFriendRequestReceived(Minecraft minecraft, String nickname, UUID playerId) {
        addWithSkin(minecraft, playerId, Component.translatable("gui.friends.toast.request_received.message", nickname));
    }

    public static void showFriendRequestAccepted(Minecraft minecraft, String nickname, UUID playerId) {
        addWithSkin(minecraft, playerId, Component.translatable("gui.friends.toast.request_accepted.message", nickname));
    }

    public static void showFriendAdded(Minecraft minecraft, String nickname, UUID playerId) {
        addWithSkin(minecraft, playerId, Component.translatable("gui.friends.toast.friend_added.message", nickname));
    }

    @FunctionalInterface
    public interface SkinToastEmitter {
        void emit(Minecraft minecraft, String playerName, UUID playerId);
    }
}
