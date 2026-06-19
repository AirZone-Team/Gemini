package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.customRenderer.glsl.modules.TargetDisplayRenderer;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.AttackEvent;
import geminiclient.gemini.event.events.impl.Render2DEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

public class TargetDisplay extends Module {

    private LivingEntity target;
    private String displayName = "";
    private static final long HOLD_DURATION_MS = 3000;
    private long lastAttackTime = 0;

    // ── 动画控制变量 ──────────────────────────────────────────────
    private float animatedHealth = 0f;
    private float fadeAlpha = 0f; // 0.0f 到 1.0f 之间的透明度

    public TargetDisplay() {
        super("TargetDisplay", ModuleEnum.Visual);
        hudX = 6;
        hudY = 100;
    }

    // ── 事件处理 ──────────────────────────────────────────────────

    @EventTarget
    public void onAttack(AttackEvent event) {
        if (event.entity() instanceof LivingEntity living) {
            // 如果切换了目标，重置动画血量以防止血量条乱跳
            if (target != living) {
                animatedHealth = living.getHealth();
            }
            target = living;
            displayName = buildDisplayName(living);
            lastAttackTime = System.currentTimeMillis();
        }
    }

    @EventTarget
    public void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.level == null) {
            fadeAlpha = 0f;
            target = null;
            return;
        }

        // 判断是否应该继续显示（包含实体存活和时间判定）
        long elapsed = System.currentTimeMillis() - lastAttackTime;
        boolean shouldRender = target != null && !target.isDeadOrDying() && elapsed <= HOLD_DURATION_MS;

        // 处理淡入淡出动画 (Fade Animation)
        if (shouldRender) {
            fadeAlpha = Math.min(1f, fadeAlpha + 0.05f); // 渐显速度
        } else {
            fadeAlpha = Math.max(0f, fadeAlpha - 0.05f); // 渐隐速度
        }

        // 如果完全透明且不需要渲染，清理目标并退出
        if (fadeAlpha <= 0f) {
            target = null;
            return;
        }

        GuiGraphicsExtractor g = event.guiGraphics();
        int x = hudX;
        int y = hudY;

        float actualHealth = target.getHealth();
        float maxHealth = target.getMaxHealth();

        // 处理平滑血量动画 (Smooth Health Animation)
        // 缓动公式：当前值 += (目标值 - 当前值) * 缓动系数
        animatedHealth += (actualHealth - animatedHealth) * 0.15f;

        if (target instanceof Player player) {
            TargetDisplayRenderer.drawTargetDisplay(g, x, y, player, displayName, animatedHealth, maxHealth, fadeAlpha);
        } else {
            TargetDisplayRenderer.drawTargetDisplayFallback(g, x, y, displayName, animatedHealth, maxHealth, fadeAlpha);
        }

        Gemini.hudDragManager.registerDragRegion(this, x, y,
                TargetDisplayRenderer.DISPLAY_WIDTH, TargetDisplayRenderer.DISPLAY_HEIGHT);
    }

    // ── 编辑器占位 ────────────────────────────────────────────────

    @Override
    public void renderEditorPlaceholder(GuiGraphicsExtractor g) {
        int x = hudX;
        int y = hudY;

        if (!enabled) {
            // 占位符也应具有一致的视觉圆角和背景
            TargetDisplayRenderer.drawBackground(g, x, y,
                    TargetDisplayRenderer.DISPLAY_WIDTH,
                    TargetDisplayRenderer.DISPLAY_HEIGHT,
                    TargetDisplayRenderer.CORNER_RADIUS, 0.35f);
        }

        Gemini.hudDragManager.registerDragRegion(this, x, y,
                TargetDisplayRenderer.DISPLAY_WIDTH, TargetDisplayRenderer.DISPLAY_HEIGHT);
    }

    // ── 辅助方法 ──────────────────────────────────────────────────

    private static String buildDisplayName(LivingEntity entity) {
        Component customName = entity.getCustomName();
        if (customName != null) return customName.getString();

        if (entity instanceof Player player) {
            return player.getGameProfile().name();
        }

        return entity.getType().getDescription().getString();
    }
}