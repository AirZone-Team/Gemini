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
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Stealer extends Module {
    private static final String CHEST_KEY = "container.chest";
    private static final String LARGE_CHEST_KEY = "container.chestDouble";
    private static final String ENDER_CHEST_KEY = "container.enderchest";
    private static final String FALLBACK_CHEST_TITLE = "Chest"; // 备用检查，以防本地化失败

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
     * 检查物品是否值得偷取。
     * @param stack 要检查的物品堆
     * @return 物品是否值得偷取
     */
    private boolean isItemUseful(ItemStack stack) {
        // 基础检查：物品非空
        if (stack.isEmpty()) {
            return false;
        }

        // 获取物品
        var item = stack.getItem();

        // 2. 检查工具和武器
        if (isValuableToolOrWeapon(item)) {
            return true;
        }

        // 3. 检查护甲
        if (isValuableArmor(stack)) {
            return true;
        }

        // 4. 检查其他有价值的物品
        if (isOtherValuableItem(item)) {
            return true;
        }

        return false;
    }

    /**
     * 检查是否为有价值的工具或武器
     */
    private boolean isValuableToolOrWeapon(Item item) {
        return item == Items.DIAMOND_PICKAXE ||
                item == Items.DIAMOND_AXE ||
                item == Items.DIAMOND_SWORD ||
                item == Items.DIAMOND_SHOVEL ||
                item == Items.DIAMOND_HOE ||
                item == Items.NETHERITE_PICKAXE ||
                item == Items.NETHERITE_AXE ||
                item == Items.NETHERITE_SWORD ||
                item == Items.NETHERITE_SHOVEL ||
                item == Items.NETHERITE_HOE ||
                item == Items.TRIDENT ||
                item == Items.BOW ||
                item == Items.CROSSBOW ||
                item == Items.SHIELD ||
                item == Items.FISHING_ROD ||
                item == Items.FLINT_AND_STEEL ||
                item == Items.SHEARS;
    }

    /**
     * 检查是否为有价值的护甲
     */
    private boolean isValuableArmor(ItemStack stack) {
        var item = stack.getItem();

        // 检查基础护甲类型
        boolean isArmor = item == Items.DIAMOND_HELMET ||
                item == Items.DIAMOND_CHESTPLATE ||
                item == Items.DIAMOND_LEGGINGS ||
                item == Items.DIAMOND_BOOTS ||
                item == Items.NETHERITE_HELMET ||
                item == Items.NETHERITE_CHESTPLATE ||
                item == Items.NETHERITE_LEGGINGS ||
                item == Items.NETHERITE_BOOTS ||
                item == Items.IRON_HELMET ||
                item == Items.IRON_CHESTPLATE ||
                item == Items.IRON_LEGGINGS ||
                item == Items.IRON_BOOTS ||
                item == Items.GOLDEN_HELMET ||
                item == Items.GOLDEN_CHESTPLATE ||
                item == Items.GOLDEN_LEGGINGS ||
                item == Items.GOLDEN_BOOTS ||
                item == Items.CHAINMAIL_HELMET ||
                item == Items.CHAINMAIL_CHESTPLATE ||
                item == Items.CHAINMAIL_LEGGINGS ||
                item == Items.CHAINMAIL_BOOTS ||
                item == Items.TURTLE_HELMET ||
                item == Items.ELYTRA;

        if (!isArmor) {
            return false;
        }

        // 检查附魔 - 如果有任何附魔，认为有价值
        if (stack.has(DataComponents.ENCHANTMENTS) && !stack.get(DataComponents.ENCHANTMENTS).isEmpty()) {
            return true;
        }

        // 对于钻石和下界合金护甲，即使没有附魔也认为有价值
        return item == Items.DIAMOND_HELMET ||
                item == Items.DIAMOND_CHESTPLATE ||
                item == Items.DIAMOND_LEGGINGS ||
                item == Items.DIAMOND_BOOTS ||
                item == Items.NETHERITE_HELMET ||
                item == Items.NETHERITE_CHESTPLATE ||
                item == Items.NETHERITE_LEGGINGS ||
                item == Items.NETHERITE_BOOTS ||
                item == Items.ELYTRA;
    }

    /**
     * 检查其他有价值的物品
     */
    private boolean isOtherValuableItem(Item item) {
        return item == Items.ENCHANTED_BOOK ||
                item == Items.EXPERIENCE_BOTTLE ||
                item == Items.GOLDEN_APPLE ||
                item == Items.ENCHANTED_GOLDEN_APPLE ||
                item == Items.ENDER_CHEST ||
                item == Items.BEACON ||
                item == Items.CONDUIT ||
                item == Items.HEART_OF_THE_SEA ||
                item == Items.NETHER_STAR ||
                item == Items.DRAGON_EGG ||
                item == Items.TOTEM_OF_UNDYING ||
                item == Items.MUSIC_DISC_5 ||
                item == Items.MUSIC_DISC_11 ||
                item == Items.MUSIC_DISC_13 ||
                item == Items.MUSIC_DISC_BLOCKS ||
                item == Items.MUSIC_DISC_CAT ||
                item == Items.MUSIC_DISC_CHIRP ||
                item == Items.MUSIC_DISC_FAR ||
                item == Items.MUSIC_DISC_MALL ||
                item == Items.MUSIC_DISC_MELLOHI ||
                item == Items.MUSIC_DISC_STAL ||
                item == Items.MUSIC_DISC_STRAD ||
                item == Items.MUSIC_DISC_WARD ||
                item == Items.MUSIC_DISC_WAIT ||
                item == Items.MUSIC_DISC_OTHERSIDE ||
                item == Items.MUSIC_DISC_PIGSTEP ||
                item == Items.MUSIC_DISC_RELIC;
    }

    /**
     * 检查物品是否在排除列表中（可选功能）
     */
    private boolean isJunkItem(Item item) {
        return item == Items.ROTTEN_FLESH ||
                item == Items.BONE ||
                item == Items.STRING ||
                item == Items.SPIDER_EYE ||
                item == Items.GUNPOWDER ||
                item == Items.WHEAT_SEEDS ||
                item == Items.PUMPKIN_SEEDS ||
                item == Items.MELON_SEEDS ||
                item == Items.BEETROOT_SEEDS;
    }
}