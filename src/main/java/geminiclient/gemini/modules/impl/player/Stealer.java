package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.MotionEvent;
import geminiclient.gemini.event.events.impl.enums.TimeEnum;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.TimerUtils;
import geminiclient.gemini.values.impl.IntRangeValue;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ArmorStandItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.equipment.ArmorMaterials;
import net.minecraft.world.item.equipment.ArmorType;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Stealer extends Module {
    private static final String CHEST_KEY = "container.chest";
    private static final String LARGE_CHEST_KEY = "container.chestDouble";
    private static final String ENDER_CHEST_KEY = "container.enderchest";
    private static final String FALLBACK_CHEST_TITLE = "Chest"; // 备用检查，以防本地化失败
    private static final String[] leather_armor = new String[] {
            Component.translatable("item.minecraft.leather_boots").getString(),
            Component.translatable("item.minecraft.leather_helmet").getString(),
            Component.translatable("item.minecraft.leather_chestplate").getString(),
            Component.translatable("item.minecraft.leather_leggings").getString()
    };
    private static final String[] iron_armor = new String[] {
            Component.translatable("item.minecraft.iron_leggings").getString(),
            Component.translatable("item.minecraft.iron_boots").getString(),
            Component.translatable("item.minecraft.iron_chestplate").getString(),
            Component.translatable("item.minecraft.iron_helmet").getString(),
    };
    private static final String[] copper_armor = new String[] {
            Component.translatable("item.minecraft.copper_helmet").getString(),
            Component.translatable("item.minecraft.copper_boots").getString(),
            Component.translatable("item.minecraft.copper_leggings").getString(),
            Component.translatable("item.minecraft.copper_chestplate").getString(),
    };
    private static final String[] golden_armor = new String[] {
            Component.translatable("item.minecraft.golden_helmet").getString(),
            Component.translatable("item.minecraft.golden_leggings").getString(),
            Component.translatable("item.minecraft.golden_chestplate").getString(),
            Component.translatable("item.minecraft.golden_boots").getString()
    };
    private static final String[] diamond_armor = new String[] {
            Component.translatable("item.minecraft.diamond_helmet").getString(),
            Component.translatable("item.minecraft.diamond_leggings").getString(),
            Component.translatable("item.minecraft.diamond_boots").getString(),
            Component.translatable("item.minecraft.diamond_chestplate").getString(),
    };
    private static final String[] netherite_armor = new String[] {
            Component.translatable("item.minecraft.netherite_helmet").getString(),
            Component.translatable("item.minecraft.netherite_leggings").getString(),
            Component.translatable("item.minecraft.netherite_boots").getString(),
            Component.translatable("item.minecraft.netherite_chestplate").getString(),
    };
    private static final String[] chainmain_armor = new String[]{
            Component.translatable("item.minecraft.chainmail_helmet").getString(),
            Component.translatable("item.minecraft.chainmail_boots").getString(),
            Component.translatable("item.minecraft.chainmail_leggings").getString(),
            Component.translatable("item.minecraft.chainmail_chestplate").getString(),
    };
    private static final String[] other_armor = new String[] {
            Component.translatable("item.minecraft.turtle_helmet").getString()
    };
    private static final String[] armor = new String[] {//傻逼MoJang删你妈的ArmorItem
            Component.translatable("item.minecraft.leather_boots").getString(),
            Component.translatable("item.minecraft.leather_helmet").getString(),
            Component.translatable("item.minecraft.leather_chestplate").getString(),
            Component.translatable("item.minecraft.leather_leggings").getString(),

            Component.translatable("item.minecraft.iron_leggings").getString(),
            Component.translatable("item.minecraft.iron_boots").getString(),
            Component.translatable("item.minecraft.iron_chestplate").getString(),
            Component.translatable("item.minecraft.iron_helmet").getString(),

            Component.translatable("item.minecraft.copper_helmet").getString(),
            Component.translatable("item.minecraft.copper_boots").getString(),
            Component.translatable("item.minecraft.copper_leggings").getString(),
            Component.translatable("item.minecraft.copper_chestplate").getString(),

            Component.translatable("item.minecraft.golden_helmet").getString(),
            Component.translatable("item.minecraft.golden_leggings").getString(),
            Component.translatable("item.minecraft.golden_chestplate").getString(),
            Component.translatable("item.minecraft.golden_boots").getString(),

            Component.translatable("item.minecraft.diamond_helmet").getString(),
            Component.translatable("item.minecraft.diamond_leggings").getString(),
            Component.translatable("item.minecraft.diamond_boots").getString(),
            Component.translatable("item.minecraft.diamond_chestplate").getString(),

            Component.translatable("item.minecraft.netherite_helmet").getString(),
            Component.translatable("item.minecraft.netherite_leggings").getString(),
            Component.translatable("item.minecraft.netherite_boots").getString(),
            Component.translatable("item.minecraft.netherite_chestplate").getString(),

            Component.translatable("item.minecraft.chainmail_helmet").getString(),
            Component.translatable("item.minecraft.chainmail_boots").getString(),
            Component.translatable("item.minecraft.chainmail_leggings").getString(),
            Component.translatable("item.minecraft.chainmail_chestplate").getString(),

            Component.translatable("item.minecraft.turtle_helmet").getString()

    };

    private final IntRangeValue delay = new IntRangeValue("Delay", 100, 200, 0, 500);

    private final TimerUtils timer = new TimerUtils();
    private final Random random = new Random();
    private Screen lastTickScreen;

    public Stealer() {
        super("Stealer", ModuleEnum.Player);
        this.addValue(delay);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onMotion(MotionEvent event) {
        if (event.getTimeEnum() != TimeEnum.Pre || mc.player == null || mc.gameMode == null) {
            return;
        }

        Screen currentScreen = mc.screen;
        if (!(currentScreen instanceof ContainerScreen container)) {
            this.lastTickScreen = currentScreen;
            return;
        }

        if (currentScreen != this.lastTickScreen) {
            timer.reset();
        }

        this.lastTickScreen = currentScreen;

        // 3. 核心逻辑处理
        if (isTargetChest(container)) {
            ChestMenu menu = container.getMenu();
            int currentDelay = random.nextInt(delay.getMinValue(), delay.getMaxValue());

            if (isChestEmpty(menu)) {
                // 箱子为空，并且计时器已到，则关闭箱子
                if (timer.hasTimeElapsed(currentDelay, false)) {
                    mc.player.closeContainer();
                }
            } else {
                // 箱子不为空，尝试偷取物品
                if (timer.hasTimeElapsed(currentDelay, true)) {
                    attemptSteal(menu);
                }
            }
        }
    }

    /**
     * 检查当前打开的容器是否为目标箱子（普通箱子、大箱子）。
     * @param container 当前的 ContainerScreen
     * @return 是否为目标箱子
     */
    private boolean isTargetChest(ContainerScreen container) {
        String chestTitle = container.getTitle().getString();

        // 获取本地化标题
        String chest = Component.translatable(CHEST_KEY).getString();
        String largeChest = Component.translatable(LARGE_CHEST_KEY).getString();

        // 注意: Stealer通常不偷取末影箱 (Ender Chest)
        return chestTitle.equals(chest)
                || chestTitle.equals(largeChest)
                || chestTitle.equals(FALLBACK_CHEST_TITLE);
    }

    /**
     * 尝试将有用的物品从箱子移动到玩家背包。
     * @param menu 当前的 ChestMenu
     */
    private void attemptSteal(ChestMenu menu) {
        // 创建并打乱箱子的所有槽位索引（0 到 menu.getRowCount() * 9 - 1）
        List<Integer> slots = IntStream.range(0, menu.getRowCount() * 9)
                .boxed()
                .collect(Collectors.toList());
        Collections.shuffle(slots, random); // 使用类的 Random 实例打乱

        for (Integer slotId : slots) {
            ItemStack stack = menu.getSlot(slotId).getItem();

            // 检查物品是否有用（即非空），**注意：这需要更复杂的实现来检查物品价值**
            if (isItemUseful(stack)) {
                // 使用 QUICK_MOVE (Shift-Click) 将物品移动到背包
                mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0, ClickType.QUICK_MOVE, mc.player);
                break; // 每次只移动一个物品以尊重延迟
            }
        }
    }

    /**
     * 检查箱子（ChestMenu 的前 rows*9 个槽位）是否为空。
     * @param menu 容器菜单
     * @return 如果箱子中没有非空物品，则返回 true
     */
    private boolean isChestEmpty(ChestMenu menu) {
        // 只检查箱子部分的槽位 (0 到 menu.getRowCount() * 9 - 1)
        return IntStream.range(0, menu.getRowCount() * 9)
                .mapToObj(i -> menu.getSlot(i).getItem())
                .allMatch(ItemStack::isEmpty);
    }

    /**
     * **TODO: 完整的物品判断逻辑**
     * 检查物品是否值得偷取。
     * 目前仅检查物品是否非空。你需要在这里添加实际的过滤逻辑 (如：不偷工具、只偷钻石等)。
     * @param stack 要检查的物品堆
     * @return 物品是否值得偷取
     */
    private boolean isItemUseful(ItemStack stack) {
        // 基础检查：物品非空
        if (stack.isEmpty() || stack.getEquipmentSlot() == null) {
            return false;
        }

        if (stack.getEquipmentSlot().isArmor()) {
            float value = 0;
            if (stack.has(DataComponents.EQUIPPABLE)) {
                value += stack.get(DataComponents.DAMAGE);
            }
        }

        // TODO: 在这里添加更复杂的逻辑：
        // 1. 检查物品ID/名称
        // 2. 检查附魔、耐久
        // 3. 检查玩家背包中是否已有同类/更好的物品 (用于 isBestItemInChest 的逻辑)

        return true;
    }
}