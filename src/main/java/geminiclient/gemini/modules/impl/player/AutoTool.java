package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ListValue;
import geminiclient.gemini.values.impl.IntValue;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.core.BlockPos;

/**
 * 自动工具模块：根据目标（方块或实体）自动切换至热键栏中的最佳工具或武器。
 */
public final class AutoTool extends Module {

    // 模式常量
    private static final String SMART_MODE = "SMART";
    private static final String FAST_MODE = "FAST"; // 新增，与SIMPLE区分
    private static final String SIMPLE_MODE = "SIMPLE";
    private static final String[] MODES = new String[] { SMART_MODE, FAST_MODE, SIMPLE_MODE };

    // 值定义
    private final IntValue switchDelay = new IntValue("SwitchDelay", 5, 0, 500);
    private final ListValue switchMode = new ListValue("SwitchMode", SMART_MODE, MODES);

    private final BoolValue switchForCombat = new BoolValue("CombatSwitch", true);
    private final BoolValue requireSneak = new BoolValue("RequireSneak", false);

    private final BoolValue protectTool = new BoolValue("ProtectTool", true);

    private final IntValue minThreshold = new IntValue("MinThreshold", 10, 1, 100, () -> protectTool.enabled);

    private final TimerUtils switchTimer = new TimerUtils();
    private ActionState currentState = ActionState.IDLE;
    private int previousSlot = -1;

    public AutoTool() {
        super("AutoTool", ModuleEnum.Player);
        this.addValue(switchDelay, switchMode, switchForCombat, requireSneak, protectTool, minThreshold);
    }

    private void setQuickbarSlot(int slot) {
        if (mc.player == null || slot < 0 || slot > 8)
            return;
        mc.player.getInventory().setSelectedSlot(slot);

        // 通常需要发送数据包同步到服务器，这里假设客户端API已处理或省略
        // mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    private int getCurrentSlot() {
        return mc.player != null ? mc.player.getInventory().getSelectedSlot() : -1;
    }

    /**
     * 尝试切换到新的槽位，并处理前一个槽位的存储。
     */
    private void trySwitchToSlot(int newSlot) {
        if (newSlot == -1 || newSlot == getCurrentSlot()) {
            return;
        }

        if (!switchTimer.hasTimeElapsed(switchDelay.getValue(), false)) {
            return;
        }

        // 只有在空闲状态且非SIMPLE模式下才存储 previousSlot
        if (currentState == ActionState.IDLE && !switchMode.is(SIMPLE_MODE)) {
            previousSlot = getCurrentSlot();
        }

        setQuickbarSlot(newSlot);
        switchTimer.reset();
    }

    /**
     * 尝试切回 previousSlot，并重置状态。
     */
    private void tryRollback() {
        if (currentState != ActionState.IDLE && !switchMode.is(SIMPLE_MODE) && previousSlot != -1) {
            setQuickbarSlot(previousSlot);
            previousSlot = -1;
        }
        currentState = ActionState.IDLE;
    }

    @Override
    public void onDisabled() {
        tryRollback();
        super.onDisabled();
    }

    @SuppressWarnings("unused")
    @EventTarget
    private void onMotionEvent(MotionEvent event) {
        if (mc.player == null || mc.level == null)
            return;

        if (requireSneak.enabled && !mc.player.isCrouching()) {
            tryRollback();
            return;
        }

        if (!mc.options.keyAttack.isDown()) {
            tryRollback();
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (hitResult == null)
            return;

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            handleMining((BlockHitResult) hitResult);
        } else if (hitResult.getType() == HitResult.Type.ENTITY && switchForCombat.enabled) {
            handleCombat((EntityHitResult) hitResult);
        } else {
            tryRollback();
        }
    }

    private void handleMining(BlockHitResult blockHit) {
        if (mc.player == null || mc.level == null)
            return;

        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = mc.level.getBlockState(blockPos);

        // 不可破坏的方块
        if (blockState.getDestroySpeed(mc.level, blockPos) < 0) {
            tryRollback();
            return;
        }

        int bestToolSlot = findBestTool(blockState);
        if (bestToolSlot == -1 || bestToolSlot == getCurrentSlot()) {
            // 已经是最好的工具或找不到更好的，维持当前状态
            currentState = ActionState.MINING;
            return;
        }

        // SMART 模式下的检查
        if (switchMode.is(SMART_MODE)) {
            ItemStack currentTool = mc.player.getMainHandItem();
            if (!currentTool.isEmpty()) {
                ItemStack newTool = mc.player.getInventory().getItem(bestToolSlot);
                float currentScore = calculateToolScore(currentTool, blockState);
                float newScore = calculateToolScore(newTool, blockState);

                // 只有新工具的评分显著高于当前工具时才切换 (15% 提升)
                if (newScore <= currentScore * 1.15f) {
                    currentState = ActionState.MINING;
                    return;
                }
            }
        }

        trySwitchToSlot(bestToolSlot);
        currentState = ActionState.MINING;
    }

    private void handleCombat(EntityHitResult entityHit) {
        Entity entity = entityHit.getEntity();
        if (!(entity instanceof LivingEntity) || entity == mc.player) {
            tryRollback();
            return;
        }

        int bestWeaponSlot = findBestWeapon();
        if (bestWeaponSlot == -1 || bestWeaponSlot == getCurrentSlot()) {
            currentState = ActionState.ATTACKING;
            return;
        }

        trySwitchToSlot(bestWeaponSlot);
        currentState = ActionState.ATTACKING;
    }

    /**
     * 查找热键栏中最佳的武器（剑 > 斧 > 其他）。
     */
    private int findBestWeapon() {
        if (mc.player == null)
            return -1;

        // 暂时的最佳槽位，优先考虑伤害/速度
        int bestSlot = -1;
        float bestWeaponDamage = 0.0f;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty())
                continue;

            // 保护工具检查
            if (protectTool.enabled && hasLowDurability(stack))
                continue;

            // 简化：仅检查剑或斧
            Item item = stack.getItem();
            String itemName = item.toString().toLowerCase();

            // 确定是否为潜在武器
            boolean isWeapon = itemName.contains("sword") || item instanceof AxeItem;

            if (isWeapon) {
                // 简化的武器评分：基于攻击伤害（Minecraft客户端可能需要Access Transformer访问伤害属性）
                // 默认使用 getDestroySpeed 作为基础分，此处简化为：剑的优先级高于斧
                float currentWeaponDamage = itemName.contains("sword") ? 2.0f : (item instanceof AxeItem ? 1.0f : 0.0f);

                // 进一步优化：添加材质加成
                currentWeaponDamage += getToolMaterialBonus(stack) / 2.0f; // 材质加成作为权重

                if (currentWeaponDamage > bestWeaponDamage) {
                    bestWeaponDamage = currentWeaponDamage;
                    bestSlot = i;
                }
            }
        }

        // 避免找不到任何武器的情况
        return bestSlot;
    }

    private int findBestTool(BlockState blockState) {
        if (mc.player == null)
            return -1;
        int bestSlot = -1;
        float bestScore = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty())
                continue;

            if (protectTool.enabled && hasLowDurability(stack))
                continue;

            if (isToolEffective(stack, blockState)) {
                float score = calculateToolScore(stack, blockState);
                if (score > bestScore) {
                    bestScore = score;
                    bestSlot = i;
                }
            }
        }
        return bestSlot;
    }

    private boolean isToolEffective(ItemStack tool, BlockState blockState) {
        // 1.0f 阈值可以防止切换到对当前方块无效的工具（例如，用镐子挖泥土）
        return tool.isCorrectToolForDrops(blockState) || tool.getDestroySpeed(blockState) > 1.0f;
    }

    /**
     * 计算工具得分，考虑挖掘速度、耐久度和材质。
     */
    private float calculateToolScore(ItemStack tool, BlockState blockState) {
        // 基础分：挖掘速度
        float baseScore = blockState != null ? tool.getDestroySpeed(blockState) : 1.0f;

        // 耐久度乘数：耐久度越高，乘数越接近 1.0f。
        float durabilityMultiplier = 1.0f;
        if (tool.getMaxDamage() > 0) {
            float durabilityRatio = (float) (tool.getMaxDamage() - tool.getDamageValue()) / tool.getMaxDamage();
            // 将耐久度比例（0到1）映射到 (0.8f, 1.0f) 范围，鼓励使用耐久度更高的工具。
            durabilityMultiplier = Mth.clamp(0.8f + (durabilityRatio * 0.2f), 0.8f, 1.0f);
        }

        // 材质加成：额外的权重，确保优先选择高材质工具。
        float materialBonus = getToolMaterialBonus(tool);

        return baseScore * durabilityMultiplier * materialBonus;
    }

    /**
     * 计算工具材质加成权重 (浮点数)
     */
    private float getToolMaterialBonus(ItemStack tool) {
        String itemName = tool.getItem().toString().toLowerCase();

        // 使用更平滑的权重，使高材质工具的优势更明显
        if (itemName.contains("netherite"))
            return 1.5f;
        if (itemName.contains("diamond"))
            return 1.3f;
        if (itemName.contains("iron"))
            return 1.1f;

        // 黄金、石制、木制工具的权重可以设置为接近 1.0f 或更低，因为它们通常不如铁制工具。
        if (itemName.contains("golden"))
            return 0.9f;
        if (itemName.contains("stone"))
            return 0.8f;
        if (itemName.contains("wooden"))
            return 0.7f;

        return 1.0f; // 非特定材质的工具（如剪刀、水桶等）
    }

    private boolean hasLowDurability(ItemStack stack) {
        if (!stack.isDamageableItem())
            return false;

        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        int threshold = minThreshold.getValue();

        return remaining <= threshold;
    }

    private enum ActionState {
        IDLE,
        MINING,
        ATTACKING
    }
}