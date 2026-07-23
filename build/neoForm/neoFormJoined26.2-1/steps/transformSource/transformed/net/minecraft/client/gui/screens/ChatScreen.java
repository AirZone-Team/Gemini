package net.minecraft.client.gui.screens;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.multiplayer.RestrictionsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.ChatAbilities;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.StringUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ChatScreen extends Screen {
    public static final double MOUSE_SCROLL_SPEED = 7.0;
    private static final Component USAGE_TEXT = Component.translatable("chat_screen.usage");
    private static final Component RESTRICTED_NARRATION_TEXT = Component.translatable("chat_screen.restricted.narration");
    public static final int USAGE_BACKGROUND_COLOR = -805306368;
    private final boolean closeOnSubmit;
    private String historyBuffer = "";
    private int historyPos = -1;
    protected EditBox input;
    protected String initial;
    protected boolean isDraft;
    private ChatComponent.DisplayMode displayMode = ChatComponent.DisplayMode.FOREGROUND;
    protected ChatScreen.ExitReason exitReason = ChatScreen.ExitReason.INTERRUPTED;
    private CommandSuggestions commandSuggestions;

    public ChatScreen(String initial, boolean isDraft) {
        this(initial, isDraft, true);
    }

    public ChatScreen(String initial, boolean isDraft, boolean closeOnSubmit) {
        super(Component.translatable("chat_screen.title"));
        this.closeOnSubmit = closeOnSubmit;
        this.initial = initial;
        this.isDraft = isDraft;
    }

    @Override
    protected void init() {
        this.historyPos = this.minecraft.gui.hud.getChat().getRecentChat().size();
        this.input = new EditBox(this.minecraft.fontFilterFishy, 4, this.height - 12, this.width - 4, 12, Component.translatable("chat.editBox")) {
            @Override
            protected MutableComponent createNarrationMessage() {
                return super.createNarrationMessage().append(ChatScreen.this.commandSuggestions.getNarrationMessage());
            }
        };
        this.input.setMaxLength(256);
        this.input.setBordered(false);
        this.input.setValue(this.initial);
        this.input.setResponder(this::onEdited);
        this.input.addFormatter(this::formatChat);
        this.input.setCanLoseFocus(false);
        this.addRenderableWidget(this.input);
        this.commandSuggestions = new CommandSuggestions(this.minecraft, this, this.input, this.font, false, false, 1, 10, true, -805306368);
        this.commandSuggestions.setAllowHiding(false);
        this.commandSuggestions.setAllowSuggestions(false);
        ChatAbilities chatAbilities = this.minecraft.player.chatAbilities();
        this.displayMode = chatAbilities.hasAnyRestrictions() ? ChatComponent.DisplayMode.FOREGROUND_RESTRICTED : ChatComponent.DisplayMode.FOREGROUND;
        this.commandSuggestions.setRestrictions(chatAbilities.canSendMessages(), chatAbilities.canSendCommands());
        this.commandSuggestions.updateCommandInfo();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.input);
    }

    @Override
    public void resize(int width, int height) {
        this.initial = this.input.getValue();
        this.init(width, height);
    }

    @Override
    public void onClose() {
        this.exitReason = ChatScreen.ExitReason.INTENTIONAL;
        super.onClose();
    }

    @Override
    public void removed() {
        this.minecraft.gui.hud.getChat().resetChatScroll();
        this.initial = this.input.getValue();
        if (this.shouldDiscardDraft() || StringUtils.isBlank(this.initial)) {
            this.minecraft.gui.hud.getChat().discardDraft();
        } else if (!this.isDraft) {
            this.minecraft.gui.hud.getChat().saveAsDraft(this.initial);
        }
    }

    protected boolean shouldDiscardDraft() {
        return this.exitReason != ChatScreen.ExitReason.INTERRUPTED
            && (this.exitReason != ChatScreen.ExitReason.INTENTIONAL || !this.minecraft.options.saveChatDrafts().get());
    }

    private void onEdited(String value) {
        this.commandSuggestions.setAllowSuggestions(true);
        this.commandSuggestions.updateCommandInfo();
        this.isDraft = false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.commandSuggestions.keyPressed(event)) {
            return true;
        }

        if (this.isDraft && event.key() == 259) {
            this.input.setValue("");
            this.isDraft = false;
            return true;
        }

        if (super.keyPressed(event)) {
            return true;
        }

        if (event.isConfirmation()) {
            if (!this.commandSuggestions.hasAllowedInput()) {
                return true;
            }

            this.handleChatInput(this.input.getValue(), true);
            if (this.closeOnSubmit) {
                this.exitReason = ChatScreen.ExitReason.DONE;
                // Neo: Prevent closing the screen if another screen has been opened.
                if (this.minecraft.gui.screen() == this)
                this.minecraft.gui.setScreen(null);
            } else {
                this.input.setValue("");
                this.minecraft.gui.hud.getChat().resetChatScroll();
            }

            return true;
        } else {
            switch (event.key()) {
                case 264:
                    this.moveInHistory(1);
                    break;
                case 265:
                    this.moveInHistory(-1);
                    break;
                case 266:
                    this.minecraft.gui.hud.getChat().scrollChat(this.minecraft.gui.hud.getChat().getLinesPerPage() - 1);
                    break;
                case 267:
                    this.minecraft.gui.hud.getChat().scrollChat(-this.minecraft.gui.hud.getChat().getLinesPerPage() + 1);
                    break;
                default:
                    return false;
            }

            return true;
        }
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        scrollY = Mth.clamp(scrollY, -1.0, 1.0);
        if (this.commandSuggestions.mouseScrolled(scrollY)) {
            return true;
        }

        if (!this.minecraft.hasShiftDown()) {
            scrollY *= 7.0;
        }

        this.minecraft.gui.hud.getChat().scrollChat((int)scrollY);
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (this.commandSuggestions.mouseClicked(event)) {
            return true;
        }

        if (event.button() == 0) {
            int screenHeight = this.minecraft.getWindow().getGuiScaledHeight();
            ActiveTextCollector.ClickableStyleFinder finder = new ActiveTextCollector.ClickableStyleFinder(this.getFont(), (int)event.x(), (int)event.y())
                .includeInsertions(this.insertionClickMode());
            this.minecraft.gui.hud.getChat().captureClickableText(finder, screenHeight, this.minecraft.gui.hud.getGuiTicks(), this.displayMode);
            Style clicked = finder.result();
            if (clicked != null && this.handleComponentClicked(clicked, this.insertionClickMode())) {
                this.initial = this.input.getValue();
                return true;
            }
        }

        return super.mouseClicked(event, doubleClick);
    }

    private boolean insertionClickMode() {
        return this.minecraft.hasShiftDown();
    }

    private boolean handleComponentClicked(Style clicked, boolean allowInsertions) {
        ClickEvent event = clicked.getClickEvent();
        if (allowInsertions) {
            if (clicked.getInsertion() != null) {
                this.insertText(clicked.getInsertion(), false);
            }
        } else if (event != null) {
            switch (event) {
                case ClickEvent.Custom customEvent when customEvent.id().equals(ChatComponent.QUEUE_EXPAND_ID):
                    ChatListener chatListener = this.minecraft.gui.chatListener();
                    if (chatListener.queueSize() != 0L) {
                        chatListener.acceptNextDelayedMessage();
                    }
                    break;
                case ClickEvent.Custom customEvent when customEvent.id().equals(ChatComponent.GO_TO_RESTRICTIONS_SCREEN):
                    this.minecraft.gui.setScreen(new RestrictionsScreen(this, this.minecraft.player.chatAbilities()));
                    break;
                default:
                    defaultHandleGameClickEvent(event, this.minecraft, this);
            }

            return true;
        }

        return false;
    }

    @Override
    public void insertText(String text, boolean replace) {
        if (replace) {
            this.input.setValue(text);
        } else {
            this.input.insertText(text);
        }
    }

    public void moveInHistory(int dir) {
        int newPos = this.historyPos + dir;
        int max = this.minecraft.gui.hud.getChat().getRecentChat().size();
        newPos = Mth.clamp(newPos, 0, max);
        if (newPos != this.historyPos) {
            if (newPos == max) {
                this.historyPos = max;
                this.input.setValue(this.historyBuffer);
            } else {
                if (this.historyPos == max) {
                    this.historyBuffer = this.input.getValue();
                }

                this.input.setValue(this.minecraft.gui.hud.getChat().getRecentChat().get(newPos));
                this.commandSuggestions.setAllowSuggestions(false);
                this.historyPos = newPos;
            }
        }
    }

    private @Nullable FormattedCharSequence formatChat(String text, int offset) {
        return this.isDraft ? FormattedCharSequence.forward(text, Style.EMPTY.withColor(ChatFormatting.GRAY).withItalic(true)) : null;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fill(2, this.height - 14, this.width - 2, this.height - 2, this.minecraft.options.getBackgroundColor(Integer.MIN_VALUE));
        this.minecraft
            .gui
            .hud
            .getChat()
            .extractRenderState(graphics, this.font, this.minecraft.gui.hud.getGuiTicks(), mouseX, mouseY, this.displayMode, this.insertionClickMode());
        super.extractRenderState(graphics, mouseX, mouseY, a);
        this.commandSuggestions.extractRenderState(graphics, mouseX, mouseY);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public boolean isAllowedInPortal() {
        return true;
    }

    @Override
    protected void updateNarrationState(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, this.getTitle());
        if (this.displayMode.showRestrictedPrompt) {
            output.add(NarratedElementType.USAGE, CommonComponents.joinForNarration(USAGE_TEXT, RESTRICTED_NARRATION_TEXT));
        } else {
            output.add(NarratedElementType.USAGE, USAGE_TEXT);
        }

        String value = this.input.getValue();
        if (!value.isEmpty()) {
            output.nest().add(NarratedElementType.TITLE, Component.translatable("chat_screen.message", value));
        }
    }

    public void handleChatInput(String msg, boolean addToRecent) {
        msg = this.normalizeChatMessage(msg);
        if (!msg.isEmpty()) {
            if (addToRecent) {
                this.minecraft.gui.hud.getChat().addRecentChat(msg);
            }

            if (msg.startsWith("/")) {
                this.minecraft.player.connection.sendCommand(msg.substring(1));
            } else {
                this.minecraft.player.connection.sendChat(msg);
            }
        }
    }

    public String normalizeChatMessage(String message) {
        return StringUtil.trimChatMessage(StringUtils.normalizeSpace(message.trim()));
    }

    @FunctionalInterface
    public interface ChatConstructor<T extends ChatScreen> {
        T create(String initial, boolean isDraft);
    }

    protected enum ExitReason {
        INTENTIONAL,
        INTERRUPTED,
        DONE;
    }
}
