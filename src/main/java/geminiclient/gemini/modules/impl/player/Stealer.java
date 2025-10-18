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
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import it.unimi.dsi.fastutil.objects.Object2IntMap;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Stealer extends Module {
    private static final String CHEST_KEY = "container.chest";
    private static final String LARGE_CHEST_KEY = "container.chestDouble";
    private static final String FALLBACK_CHEST_TITLE = "Chest";

    // 有价值的物品列表
    private static final Set<String> VALUABLE_ITEMS = Set.of(
            "diamond", "netherite", "emerald", "gold_ingot", "iron_ingot",
            "ender_pearl", "obsidian", "enchanted_book", "experience_bottle",
            "beacon", "ender_eye", "blaze_rod", "ghast_tear", "shulker_shell");

    // 有价值的工具和武器
    private static final Set<String> VALUABLE_TOOLS = Set.of(
            "sword", "pickaxe", "axe", "shovel", "hoe", "bow", "crossbow", "trident", "fishing_rod");

    private final IntRangeValue delay = new IntRangeValue("Delay", 100, 200, 0, 500);
    private final TimerUtils timer = new TimerUtils();
    private final Random random = new Random();
    private Screen lastTickScreen;

    public Stealer() {
        super("Stealer", ModuleEnum.Player);
        this.addValue(delay);
    }

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

        if (isTargetChest(container)) {
            ChestMenu menu = container.getMenu();
            int currentDelay = random.nextInt(delay.getMinValue(), delay.getMaxValue());

            if (isChestEmpty(menu)) {
                if (timer.hasTimeElapsed(currentDelay, false)) {
                    mc.player.closeContainer();
                }
            } else {
                if (timer.hasTimeElapsed(currentDelay, true)) {
                    attemptSteal(menu);
                }
            }
        }
    }

    /**
     * 检查当前打开的容器是否为目标箱子
     */
    private boolean isTargetChest(ContainerScreen container) {
        String chestTitle = container.getTitle().getString().toLowerCase();

        String chest = Component.translatable(CHEST_KEY).getString().toLowerCase();
        String largeChest = Component.translatable(LARGE_CHEST_KEY).getString().toLowerCase();

        return chestTitle.contains(chest)
                || chestTitle.contains(largeChest)
                || chestTitle.contains(FALLBACK_CHEST_TITLE.toLowerCase());
    }

    /**
     * 尝试偷取有价值的物品
     */
    private void attemptSteal(ChestMenu menu) {
        List<Integer> valuableSlots = findValuableSlots(menu);

        if (!valuableSlots.isEmpty()) {
            int slotId = valuableSlots.get(random.nextInt(valuableSlots.size()));
            mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0, ClickType.QUICK_MOVE, mc.player);
        } else {
            List<Integer> allSlots = IntStream.range(0, menu.getRowCount() * 9)
                    .boxed()
                    .collect(Collectors.toList());
            Collections.shuffle(allSlots, random);

            for (Integer slotId : allSlots) {
                ItemStack stack = menu.getSlot(slotId).getItem();
                if (!stack.isEmpty()) {
                    mc.gameMode.handleInventoryMouseClick(menu.containerId, slotId, 0, ClickType.QUICK_MOVE, mc.player);
                    break;
                }
            }
        }
    }

    /**
     * 查找箱子中有价值的槽位
     */
    private List<Integer> findValuableSlots(ChestMenu menu) {
        List<Integer> valuableSlots = new ArrayList<>();

        for (int i = 0; i < menu.getRowCount() * 9; i++) {
            ItemStack stack = menu.getSlot(i).getItem();
            if (!stack.isEmpty() && getItemValue(stack) > 0) {
                valuableSlots.add(i);
            }
        }

        valuableSlots.sort((slot1, slot2) -> {
            ItemStack stack1 = menu.getSlot(slot1).getItem();
            ItemStack stack2 = menu.getSlot(slot2).getItem();
            return Integer.compare(getItemValue(stack2), getItemValue(stack1));
        });

        return valuableSlots;
    }

    /**
     * 计算物品的价值分数
     */
    private int getItemValue(ItemStack stack) {
        if (stack.isEmpty())
            return 0;

        int value = 0;
        String itemName = stack.getItem().toString().toLowerCase();

        if (itemName.contains("netherite"))
            value += 100;
        else if (itemName.contains("diamond"))
            value += 80;
        else if (itemName.contains("emerald"))
            value += 60;
        else if (itemName.contains("gold"))
            value += 40;
        else if (itemName.contains("iron"))
            value += 30;

        for (String tool : VALUABLE_TOOLS) {
            if (itemName.contains(tool)) {
                value += 50;
                break;
            }
        }

        if (itemName.contains("helmet") || itemName.contains("chestplate") ||
                itemName.contains("leggings") || itemName.contains("boots")) {
            value += 40;
        }

        for (String valuableItem : VALUABLE_ITEMS) {
            if (itemName.contains(valuableItem)) {
                value += 70;
                break;
            }
        }

        value += calculateEnchantmentValue(stack);

        if (stack.getCount() > 1) {
            value += stack.getCount() / 2;
        }

        return value;
    }

    /**
     * 计算附魔价值 - 简化版，只考虑等级
     */
    private int calculateEnchantmentValue(ItemStack stack) {
        int enchantValue = 0;

        ItemEnchantments enchantments = stack.get(DataComponents.ENCHANTMENTS);
        if (enchantments != null) {
            for (Object2IntMap.Entry<Holder<Enchantment>> entry : enchantments.entrySet()) {
                int level = entry.getIntValue();
                // 每个附魔等级基础价值为5
                enchantValue += level * 5;
            }
        }

        return enchantValue;
    }

    /**
     * 检查箱子是否为空
     */
    private boolean isChestEmpty(ChestMenu menu) {
        return IntStream.range(0, menu.getRowCount() * 9)
                .mapToObj(i -> menu.getSlot(i).getItem())
                .allMatch(ItemStack::isEmpty);
    }
}