package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.base.MinecraftInstance;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ListValue;
import geminiclient.gemini.values.impl.IntValue; 

// Minecraft 导入路径
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

import java.util.concurrent.atomic.AtomicLong; 

// 计时器辅助类 (TimerUtil)
class TimerUtil implements MinecraftInstance {
    private final AtomicLong lastTime = new AtomicLong(System.currentTimeMillis());

    public boolean hasElapsedTime(long ms) {
        return System.currentTimeMillis() - lastTime.get() >= ms;
    }

    public void reset() {
        lastTime.set(System.currentTimeMillis());
    }
}


public final class AutoTool extends Module {
    
    // 【ListValue所需】定义模式的字符串数组
    private static final String[] MODES = new String[]{"SMART", "FAST", "SIMPLE"};
    private static final String SMART_MODE = "SMART";
    private static final String SIMPLE_MODE = "SIMPLE";
    
    private static final IntValue switchDelay = new IntValue("Switch Delay", 5, 0, 500); 
    private static final ListValue switchMode = new ListValue("Switch Mode", SMART_MODE, MODES);
    
    private static final BoolValue switchForCombat = new BoolValue("Combat Switch", true);
    private static final BoolValue requireSneak = new BoolValue("Require Sneak", false); 
    
    // 【修改和联动】Protect Durability -> Protect Tool
    private static final BoolValue protectTool = new BoolValue("Protect Tool", true); 
    
    // 【修改和联动】Durability Threshold -> Min Threshold
    // 只有当 protectTool.enabled 为 true 时才显示
    private static final IntValue minThreshold = new IntValue("Min Threshold", 10, 1, 100, () -> protectTool.enabled); 

    private final TimerUtil switchTimer = new TimerUtil();
    private ActionState currentState = ActionState.IDLE;
    private int previousSlot = -1;

    public AutoTool() {
        super("Auto Tool", ModuleEnum.Player);
        // 更新 addValue 列表，使用新名称
        this.addValue(switchDelay, switchMode, switchForCombat, requireSneak, protectTool, minThreshold);
    }
    
    /**
     * 【Access Transformer访问】设置热键栏槽位。
     */
    private void setQuickbarSlot(int slot) {
        mc.player.getInventory().selected = slot; 
        
        // 发送数据包同步到服务器 (重要!)
        // mc.player.connection.send(new ServerboundSetCarriedItemPacket(slot));
    }

    /**
     * 【Access Transformer访问】读取当前热键栏槽位。
     */
    private int getCurrentSlot() {
        return mc.player.getInventory().selected; 
    }


    @Override
    public void onDisabled() {
        currentState = ActionState.IDLE;
        if (!switchMode.is(SIMPLE_MODE) && previousSlot != -1 && mc.player != null) {
            setQuickbarSlot(previousSlot); 
        }
        previousSlot = -1;
        super.onDisabled();
    }

    @EventTarget
    private void onMotionEvent(MotionEvent event) {
        if (mc.player == null || mc.level == null) return; 

        if (requireSneak.enabled && !mc.player.isCrouching()) { 
            return;
        }

        if (!mc.options.keyAttack.isDown()) {
            if (currentState != ActionState.IDLE && !switchMode.is(SIMPLE_MODE) && previousSlot != -1) {
                setQuickbarSlot(previousSlot);
                previousSlot = -1;
            }
            currentState = ActionState.IDLE;
            return;
        }

        HitResult hitResult = mc.hitResult; 
        if (hitResult == null) return;

        if (hitResult.getType() == HitResult.Type.BLOCK) {
            handleMining((BlockHitResult) hitResult);
        } else if (hitResult.getType() == HitResult.Type.ENTITY && switchForCombat.enabled) {
            handleCombat((EntityHitResult) hitResult);
        }
    }
    
    private void handleMining(BlockHitResult blockHit) {
        BlockPos blockPos = blockHit.getBlockPos();
        BlockState blockState = mc.level.getBlockState(blockPos);

        if (blockState.getDestroySpeed(mc.level, blockPos) < 0) return; 

        int bestToolSlot = findBestTool(blockState);
        if (bestToolSlot != -1 && bestToolSlot != getCurrentSlot()) { 
            if (!switchTimer.hasElapsedTime(switchDelay.getValue())) return;

            if (switchMode.is(SMART_MODE)) {
                ItemStack currentTool = mc.player.getMainHandItem();
                if (!currentTool.isEmpty()) {
                    ItemStack newTool = mc.player.getInventory().getItem(bestToolSlot);
                    float currentScore = calculateToolScore(currentTool, blockState);
                    float newScore = calculateToolScore(newTool, blockState);
                    
                    if (newScore <= currentScore * 1.15f) { 
                        return;
                    }
                }
            }

            if (currentState == ActionState.IDLE && !switchMode.is(SIMPLE_MODE)) {
                previousSlot = getCurrentSlot(); 
            }
            setQuickbarSlot(bestToolSlot); 
            currentState = ActionState.MINING;
            switchTimer.reset();
        }
    }

    private void handleCombat(EntityHitResult entityHit) {
        Entity entity = entityHit.getEntity();
        if (!(entity instanceof LivingEntity) || entity == mc.player) return;

        int bestWeaponSlot = findBestWeapon();
        if (bestWeaponSlot != -1 && bestWeaponSlot != getCurrentSlot()) { 
            if (!switchTimer.hasElapsedTime(switchDelay.getValue())) return;

            setQuickbarSlot(bestWeaponSlot); 
            currentState = ActionState.ATTACKING;
            switchTimer.reset();
        }
    }
    
    private int findBestWeapon() {
        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                
                String itemName = item.toString().toLowerCase();
                // 【逻辑更新】使用 protectTool.enabled 检查
                if (itemName.contains("sword")) { 
                    if (!protectTool.enabled || !hasLowDurability(stack)) {
                        return i;
                    }
                }
            }
        }

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (!stack.isEmpty()) {
                Item item = stack.getItem();
                
                // 【逻辑更新】使用 protectTool.enabled 检查
                if (item instanceof AxeItem) { 
                    if (!protectTool.enabled || !hasLowDurability(stack)) {
                        return i;
                    }
                }
            }
        }

        return -1;
    }
    
    private int findBestTool(BlockState blockState) {
        int bestSlot = -1;
        float bestScore = 0;

        for (int i = 0; i < 9; i++) {
            ItemStack stack = mc.player.getInventory().getItem(i);
            if (stack.isEmpty()) continue;

            // 【逻辑更新】使用 protectTool.enabled 检查
            if (protectTool.enabled && hasLowDurability(stack)) continue;

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
        return tool.isCorrectToolForDrops(blockState) || tool.getDestroySpeed(blockState) > 1.0f; 
    }

    private float calculateToolScore(ItemStack tool, BlockState blockState) {
        float baseScore = blockState != null ? tool.getDestroySpeed(blockState) : 1.0f;

        float durabilityMultiplier = 1.0f;
        if (tool.getMaxDamage() > 0) {
            float durabilityRatio = (float) (tool.getMaxDamage() - tool.getDamageValue()) / tool.getMaxDamage();
            durabilityMultiplier = Mth.clamp(0.8f + (durabilityRatio * 0.2f), 0.8f, 1.0f); 
        }

        float materialBonus = getToolMaterialBonus(tool);

        return baseScore * durabilityMultiplier * materialBonus;
    }

    private float getToolMaterialBonus(ItemStack tool) {
        String itemName = tool.getItem().toString().toLowerCase();

        if (itemName.contains("netherite")) return 6.0f;
        if (itemName.contains("diamond")) return 5.0f;
        if (itemName.contains("iron")) return 4.0f;
        if (itemName.contains("golden")) return 3.5f;
        if (itemName.contains("stone")) return 2.0f;
        if (itemName.contains("wooden")) return 1.5f;

        return 1.0f;
    }

    private boolean hasLowDurability(ItemStack stack) {
        if (!stack.isDamageableItem()) return false;

        int remaining = stack.getMaxDamage() - stack.getDamageValue();
        // 【逻辑更新】使用 minThreshold 代替 durabilityThreshold
        int threshold = minThreshold.getValue(); 

        return remaining <= threshold;
    }

    private enum ActionState {
        IDLE,
        MINING,
        ATTACKING
    }
}
