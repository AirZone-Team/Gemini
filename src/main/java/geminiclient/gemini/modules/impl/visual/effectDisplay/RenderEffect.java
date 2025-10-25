package geminiclient.gemini.modules.impl.visual.effectDisplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.locale.Language;

import java.awt.Color;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

// --- 渲染效果主类 ---
public class RenderEffect {

    // --- 颜色和尺寸常量 ---
    private static final int HEADER_COLOR_RGB = 0xFF962D2D; // 标题文本颜色 (R:150, G:45, B:45)
    private static final int BODY_COLOR_ARGB = 0x78000000;  // 背景颜色 (A:120, R:0, G:0, B:0)
    private static final int DURATION_BAR_COLOR_ARGB = 0x96323232; // 持续时间条颜色
    private static final int DURATION_TEXT_COLOR_RGB = 0xFFFFFFFF; // 持续时间文本颜色

    private static final float PADDING_X = 10.0F;    // 距离屏幕左侧的间距
    private static final float ELEMENT_HEIGHT = 30.0F; // 效果卡片的高度
    private static final float SPACING_Y = 34.0F;    // 效果之间的垂直间隔

    private final Map<Holder<MobEffect>, MobEffectInfo> infos = new ConcurrentHashMap<>();

    private static final RenderEffect INSTANCE = new RenderEffect();

    public static RenderEffect getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. 数据更新和动画目标计算
        // 遍历所有当前活跃的效果，更新或创建 MobEffectInfo
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            Holder<MobEffect> effectHolder = effect.getEffect();
            MobEffectInfo info = this.infos.computeIfAbsent(effectHolder, k -> new MobEffectInfo());

            info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
            info.duration = effect.getDuration();
            info.amplifier = effect.getAmplifier();
            info.shouldDisappear = false;

            // 估算卡片宽度：文本宽度 (0.75x scale) + 边距 (30.0F)
            String text = getDisplayName(effectHolder.value(), info).getString();
            info.width = mc.font.width(text) * 0.75F + 30.0F;

            // 设置动画目标
            info.durationTimer.target = (float) info.duration / (float) info.maxDuration * info.width;
            info.xTimer.target = PADDING_X;
        }

        // 用于在 removeIf 循环中追踪当前绘制的 Y 坐标（需要数组/AtomicXXX 来在 Lambda 中修改外部变量）
        final float initialY = mc.getWindow().getGuiScaledHeight() / 2.0F - this.infos.size() * (SPACING_Y / 2.0F);
        final float[] currentY = {initialY};

        // 2. 动画更新、渲染和移除判断
        this.infos.entrySet().removeIf(entry -> {
            Holder<MobEffect> effect = entry.getKey();
            MobEffectInfo info = entry.getValue();

            // --- 阶段 A: 动画更新与消失逻辑 ---
            if (!info.shouldDisappear) {
                // 设置 Y 坐标目标并更新
                info.yTimer.target = currentY[0];
            } else {
                // 如果已标记消失，设置 X 轴目标移出屏幕
                info.xTimer.target = -info.width - 20.0F;
            }

            // 更新所有计时器
            info.durationTimer.update(true);
            info.xTimer.update(true);
            info.yTimer.update(true);

            // 检查效果是否被移除（游戏机制）
            if (!info.shouldDisappear && !mc.player.hasEffect(effect)) {
                info.shouldDisappear = true;
                // 立即设置消失目标，动画将在下一帧开始
                info.xTimer.target = -info.width - 20.0F;
            }

            // --- 阶段 B: 渲染绘制 ---
            float x = info.xTimer.value;
            float y = info.yTimer.value;

            // 仅当卡片在屏幕内或正在移出时才渲染
            if (x < mc.getWindow().getGuiScaledWidth()) {
                graphics.pose().pushMatrix();
                graphics.pose().translate(x, y);

                // 渲染圆角背景 - (使用简单的矩形占位)
                graphics.fill(2, 2, (int) info.width - 2, (int) ELEMENT_HEIGHT - 2, BODY_COLOR_ARGB);

                // 渲染持续时间条
                if (info.durationTimer.value > 0) {
                    // 确保持续时间条不会超出卡片宽度
                    int barWidth = (int) Math.min(info.durationTimer.value, info.width - 4);
                    graphics.fill(2, 2, barWidth, (int) ELEMENT_HEIGHT - 2, DURATION_BAR_COLOR_ARGB);
                }

                // 渲染装饰条（右侧竖条）
                graphics.fill((int) info.width - 10, 7, (int) info.width - 5, 25, HEADER_COLOR_RGB);

                // 渲染文本：名称和等级
                graphics.pose().pushMatrix();
                graphics.pose().scale(0.75F, 0.75F);
                String name = getDisplayName(effect.value(), info).getString();
                // 绘制位置：X=14，Y=7
                graphics.drawString(mc.font, name, (int) (14 / 0.75F), (int) (7.0F / 0.75F), HEADER_COLOR_RGB, false);
                graphics.pose().popMatrix();

                // 渲染文本：持续时间
                graphics.pose().pushMatrix();
                graphics.pose().scale(0.6F, 0.6F);
                String duration = StringUtil.formatTickDuration(info.duration,20);
                // 绘制位置：X=14，Y=17
                graphics.drawString(mc.font, duration, (int) (14 / 0.6F), (int) (17.0F / 0.6F), DURATION_TEXT_COLOR_RGB, false);
                graphics.pose().popMatrix();

                graphics.pose().popMatrix();

                // 只有成功渲染（未移除）的效果才占用 Y 空间
                currentY[0] += SPACING_Y;
            }


            // --- 阶段 C: 移除判断 ---
            if (info.shouldDisappear) {
                // 动画完成，移除信息 (检查 X 坐标是否达到或超过目标)
                return info.xTimer.value <= -info.width - 19.0F;
            }

            return false; // 不移除
        });
    }

    /**
     * 构建效果的显示名称 (名称 + 等级)
     */
    private Component getDisplayName(MobEffect effect, MobEffectInfo info) {
        Component name = effect.getDisplayName();
        String amplifierName;

        if (info.amplifier >= 1) {
            // 使用 Minecraft 的标准等级本地化键
            String levelKey = "enchantment.level." + (info.amplifier + 1);
            amplifierName = " " + Language.getInstance().getOrDefault(levelKey);
        } else {
            amplifierName = "";
        }

        // 合并 Component (使用 literal 创建，避免过度依赖旧版 TextComponent)
        return Component.literal(name.getString() + amplifierName);
    }
}

/**
 * 辅助类：用于平滑过渡的计时器
 */
class SimpleTimer {
    public float value;
    public float target;

    public SimpleTimer() {
        this.value = 0.0F;
        this.target = 0.0F;
    }

    /**
     * 更新计时器的值。
     * @param smooth 是否使用平滑过渡（线性插值）
     */
    public void update(boolean smooth) {
        if (smooth) {
            // 简单的线性插值 (Lerp) 实现平滑过渡
            float difference = this.target - this.value;
            this.value += difference * 0.15F; // 0.15F 为过渡速度

            // 避免无限接近，当差值很小时直接设置到目标值
            if (Math.abs(difference) < 0.1F) {
                this.value = this.target;
            }
        } else {
            this.value = this.target;
        }
    }
}

/**
 * 辅助类：存储 MobEffect 的状态和动画计时器
 */
class MobEffectInfo {
    public SimpleTimer xTimer = new SimpleTimer();
    public SimpleTimer yTimer = new SimpleTimer();
    public SimpleTimer durationTimer = new SimpleTimer();

    public int duration = 0;
    public int maxDuration = 1;
    public int amplifier = 0;
    public float width = 0.0F; // 卡片的动态宽度
    public boolean shouldDisappear = false; // 是否应该开始消失动画
}
