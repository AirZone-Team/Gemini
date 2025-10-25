package geminiclient.gemini.modules.impl.movement;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.SlowDownEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.PotionItem;

public class NoSlow extends Module {

    // 使用物品时的最大速度倍率
    public final FloatValue factor = new FloatValue("Factor", 0.98f, 0.2f, 0.98f);

    public final ListValue foodMode = new ListValue("FoodMode", "Vanilla", new String[] { "Vanilla", "None" });
    public final ListValue bowMode = new ListValue("BowMode", "Vanilla", new String[] { "Vanilla", "None" });
    public final ListValue otherMode = new ListValue("OtherMode", "Vanilla", new String[] { "Vanilla", "None" });

    public NoSlow() {
        super("NoSlow", ModuleEnum.Movement);

        addValue(factor);
        addValue(foodMode);
        addValue(bowMode);
        addValue(otherMode);
    }

    @SuppressWarnings("unused")
    @EventTarget
    public void onSlowDown(SlowDownEvent event) {
        if (mc.player == null)
            return;

        // 使用物品处理
        if (!mc.player.isUsingItem())
            return;
        Item item = mc.player.getUseItem().getItem();

        // 食物 / 药水
        if (isFoodOrPotion(item)) {
            if (foodMode.is("Vanilla")) {
                event.setFactor(factor.getValue());
            }
            return; // 明确返回，防止继续处理其他模式
        }

        // 弓 / 弩
        if (item == Items.BOW || item == Items.CROSSBOW) {
            if (bowMode.is("Vanilla")) {
                event.setFactor(factor.getValue());
            }
            return; // 明确返回，防止继续处理其他模式
        }

        // 其他可使用物品（盾牌等）
        if (otherMode.is("Vanilla")) {
            event.setFactor(factor.getValue());
        }
    }

    /**
     * 判断是否为食物或药水（兼容 1.21.9）
     */
    private boolean isFoodOrPotion(Item item) {
        if (item instanceof PotionItem || item == Items.MILK_BUCKET)
            return true;

        String name = item.toString().toLowerCase();
        return name.contains("food") || name.contains("meat") || name.contains("apple") || name.contains("bread");
    }
}
