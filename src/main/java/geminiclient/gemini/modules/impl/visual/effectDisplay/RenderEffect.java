package geminiclient.gemini.modules.impl.visual.effectDisplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RenderEffect {

    // --- 颜色和尺寸常量 ---
    private static final int HEADER_COLOR_RGB = 0xFFFF5555;
    private static final int BODY_COLOR_ARGB = 0x99000000;
    private static final int DURATION_BAR_COLOR_ARGB = 0xBBFF5555;
    private static final int DURATION_TEXT_COLOR_RGB = 0xFFAAAAAA;

    private static final float PADDING_X = 12.0F;
    private static final float ELEMENT_HEIGHT = 32.0F;
    private static final float SPACING_Y = 38.0F;
    private static final float ICON_SIZE = 18.0F;

    private final Map<Holder<MobEffect>, MobEffectInfo> infos = new ConcurrentHashMap<>();
    private static final RenderEffect INSTANCE = new RenderEffect();

    public static RenderEffect getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. 数据更新和动画目标计算
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Holder<MobEffect> effectHolder = effect.getEffect();
            MobEffectInfo info = this.infos.computeIfAbsent(effectHolder, k -> new MobEffectInfo());

            info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
            info.duration = effect.getDuration();
            info.amplifier = effect.getAmplifier();
            info.shouldDisappear = false;

            String text = getDisplayName(effectHolder.value(), info).getString();
            info.width = mc.font.width(text) * 0.85F + ICON_SIZE + 40.0F;

            info.durationTimer.target = (float) info.duration / (float) info.maxDuration * info.width;
            info.xTimer.target = PADDING_X;
        }

        final float initialY = mc.getWindow().getGuiScaledHeight() / 2.0F - this.infos.size() * (SPACING_Y / 2.0F);
        final float[] currentY = { initialY };
        TextureManager textureManager = mc.getTextureManager();

        // 2. 动画更新、渲染和移除判断
        this.infos.entrySet().removeIf(entry -> {
            Holder<MobEffect> effect = entry.getKey();
            MobEffectInfo info = entry.getValue();

            if (!info.shouldDisappear) {
                info.yTimer.target = currentY[0];
            } else {
                info.xTimer.target = -info.width - 20.0F;
            }

            info.durationTimer.update(true);
            info.xTimer.update(true);
            info.yTimer.update(true);

            if (!info.shouldDisappear && !mc.player.hasEffect(effect)) {
                info.shouldDisappear = true;
                info.xTimer.target = -info.width - 20.0F;
            }

            float x = info.xTimer.value;
            float y = info.yTimer.value;

            if (x < mc.getWindow().getGuiScaledWidth()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y); // Matrix3x2fStack 只需要 x 和 y

                // --- 渲染背景与进度条 ---
                graphics.fill(0, 0, (int) info.width, (int) ELEMENT_HEIGHT, BODY_COLOR_ARGB);
                if (info.durationTimer.value > 0) {
                    int barWidth = (int) Math.min(info.durationTimer.value, info.width);
                    graphics.fill(0, (int) ELEMENT_HEIGHT - 2, barWidth, (int) ELEMENT_HEIGHT, DURATION_BAR_COLOR_ARGB);
                }

                // 左侧点缀竖条
                graphics.fill(0, 0, 2, (int) ELEMENT_HEIGHT, HEADER_COLOR_RGB);

                // --- 渲染药水图标 (适配最新管线 API) ---
//                AbstractTexture sprite = textureManager.getTexture(effect.getKey().identifier());
                if (effect.getKey() != null) {
                    graphics.blitSprite(
                            RenderPipelines.GUI_TEXTURED,
                            Gui.getMobEffectSprite(effect),
                            6,
                            (int) (ELEMENT_HEIGHT / 2 - ICON_SIZE / 2),
                            (int) ICON_SIZE,
                            (int) ICON_SIZE
                    );
                }

                // --- 渲染文本 (适配最新文本 API) ---
                graphics.pose().pushMatrix();
                float textScale = 0.85F;
                graphics.pose().scale(textScale, textScale); // Matrix3x2fStack 缩放仅支持 XY 平面

                Component name = getDisplayName(effect.value(), info);
                float textX = (6 + ICON_SIZE + 6) / textScale;

                // 绘制名称
                graphics.text(mc.font, name, (int) textX, (int) (6.0F / textScale), HEADER_COLOR_RGB, true);

                // 绘制持续时间
                String duration = StringUtil.formatTickDuration(info.duration, 20);
                graphics.text(mc.font, duration, (int) textX, (int) (18.0F / textScale), DURATION_TEXT_COLOR_RGB, true);

                graphics.pose().popMatrix();
                graphics.pose().popMatrix();

                currentY[0] += SPACING_Y;
            }

            if (info.shouldDisappear) {
                return info.xTimer.value <= -info.width - 15.0F;
            }
            return false;
        });
    }

    private Component getDisplayName(MobEffect effect, MobEffectInfo info) {
        MutableComponent name = effect.getDisplayName().copy();
        if (info.amplifier >= 1) {
            name.append(" ").append(Component.translatable("enchantment.level." + (info.amplifier + 1)));
        }
        return name;
    }
}

class MobEffectInfo {
    public SmoothAnimationTimer xTimer = new SmoothAnimationTimer(0.0F, 0.15F);
    public SmoothAnimationTimer yTimer = new SmoothAnimationTimer(0.0F, 0.15F);
    public SmoothAnimationTimer durationTimer = new SmoothAnimationTimer(0.0F, 0.15F);

    public int duration = 0;
    public int maxDuration = 1;
    public int amplifier = 0;
    public float width = 0.0F;
    public boolean shouldDisappear = false;
}