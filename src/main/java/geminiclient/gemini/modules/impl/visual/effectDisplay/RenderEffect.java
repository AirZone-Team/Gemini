package geminiclient.gemini.modules.impl.visual.effectDisplay;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

import java.awt.*;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RenderEffect {
    private final Map<Holder<MobEffect>, MobEffectInfo> infos = new ConcurrentHashMap<>();
    private final Color headerColor = new Color(150, 45, 45, 255);
    private final Color bodyColor = new Color(0, 0, 0, 120); // 更高的透明度

    private static final float WIDTH_BASE = 25.0F; // 图标宽度
    private static final float PADDING_X = 10.0F; // 距离屏幕左侧的间距
    private static final float HEIGHT = 30.0F;
    private static final float SPACING_Y = 34.0F; // 效果之间的间隔

    private static final RenderEffect INSTANCE = new RenderEffect();
    public static RenderEffect getInstance() {
        return INSTANCE;
    }

    public void render(GuiGraphics graphics) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        // 1. 更新数据并处理移除/添加
        for (MobEffectInstance effect : mc.player.getActiveEffects()) {
            MobEffectInfo info = this.infos.computeIfAbsent(effect.getEffect(), k -> new MobEffectInfo());

            info.maxDuration = Math.max(info.maxDuration, effect.getDuration());
            info.duration = effect.getDuration();
            info.amplifier = effect.getAmplifier();
            info.shouldDisappear = false;
        }

        // 2. 计算起始Y坐标
        final int[] startY = {mc.getWindow().getGuiScaledHeight() / 2 - this.infos.size() * (int) (SPACING_Y / 2)};

        // 使用 for-each 遍历，并利用迭代器的 remove 方法安全移除消失的效果
        this.infos.entrySet().removeIf(entry -> {
            Holder<MobEffect> effect = entry.getKey();
            MobEffectInfo info = entry.getValue();

            // 如果效果仍然存在，更新动画目标
            if (!info.shouldDisappear) {
                // 假设默认字体宽度是 5px * scale
                String text = getDisplayName(effect.value(), info).getString();
                // 估算宽度：文本宽度(text.length() * 5 * 1.5) + 边距(20)
                info.width = mc.font.width(text) * 0.75F + 30.0F; // 调整宽度计算，移除图标宽度

                info.durationTimer.target = (float)info.duration / (float)info.maxDuration * info.width;
                if (info.durationTimer.value <= 0.0F) {
                    info.durationTimer.value = info.durationTimer.target;
                }

                info.xTimer.target = PADDING_X;
                info.yTimer.target = (float) startY[0];

                info.durationTimer.update(true);
                info.xTimer.update(true);
                info.yTimer.update(true);

                // 检查效果是否被移除（游戏机制）
                if (!mc.player.hasEffect(effect)) {
                    info.shouldDisappear = true;
                    info.xTimer.target = -info.width - 20.0F; // 设置消失目标
                }
            } else {
                // 效果已消失，更新动画
                info.xTimer.update(true);
                // 动画完成，移除信息
                if (info.xTimer.value <= -info.width - 19.0F) {
                    return true;
                }
            }

            // 3. 渲染
            float x = info.xTimer.value;
            float y = info.yTimer.value;

            graphics.pose().pushMatrix();
            graphics.pose().translate(x, y);

            // 渲染圆角背景
            // 警告：GuiGraphics.fill 是矩形。要实现圆角，你需要自定义着色器/渲染工具。
            // 这里我们使用一个简单的矩形作为占位符。
            graphics.fill(2, 2, (int)info.width - 2, (int)HEIGHT - 2, bodyColor.getRGB());

            // 渲染持续时间条
            if (info.durationTimer.value > 0) {
                graphics.fill(2, 2, (int)info.durationTimer.value, (int)HEIGHT - 2, new Color(50, 50, 50, 150).getRGB());
            }

            // 渲染装饰条（移到左边的竖条）
            graphics.fill((int)info.width - 10, 7, (int)info.width - 5, 25, headerColor.getRGB());

            // 渲染文本：名称（向左对齐）
            graphics.pose().pushMatrix();
            graphics.pose().scale(0.75F, 0.75F);
            String name = getDisplayName(effect.value(), info).getString();
            // 向左对齐，从装饰条右侧开始绘制
            graphics.drawString(mc.font, name, (int)(14 / 0.75F), (int)(7.0F / 0.75F), headerColor.getRGB(), false);
            graphics.pose().popMatrix();

            // 渲染文本：持续时间（向左对齐）
            graphics.pose().pushMatrix();
            graphics.pose().scale(0.6F, 0.6F);
            String duration = StringUtil.formatTickDuration(info.duration,60);
            // 向左对齐，从装饰条右侧开始绘制
            graphics.drawString(mc.font,duration, (int)(14 / 0.6F), (int)(17.0F / 0.6F), Color.WHITE.getRGB(), false);
            graphics.pose().popMatrix();

            // 图标渲染已删除

            graphics.pose().popMatrix();

            startY[0] += (int) SPACING_Y;
            return false; // 不移除
        });
    }

    private Component getDisplayName(MobEffect effect, MobEffectInfo info) {
        Component name = effect.getDisplayName();
        String amplifierName;
        // 模仿原代码的放大器显示逻辑
        if (info.amplifier >= 1 && info.amplifier <= 3) {
            amplifierName = " " + net.minecraft.locale.Language.getInstance().getOrDefault("enchantment.level." + (info.amplifier + 1));
        } else if (info.amplifier > 3) {
            amplifierName = " " + (info.amplifier + 1);
        } else {
            amplifierName = "";
        }

        // 简化的 Component 合并，实际应用中可能需要更复杂的 TextComponent 构建
        return Component.literal(name.getString() + amplifierName);
    }
}