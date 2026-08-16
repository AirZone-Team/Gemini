package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * BlockESP（方块透视）— 在指定的方块位置渲染与其真实形状一致的 3D 矩形，
 * 实现透视/高亮效果。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>以玩家为中心按 {@code Radius} 半径扫描已加载区块中的目标方块</li>
 *   <li>通过 {@link BlockState#getShape} 取该方块的实际碰撞形状
 *       （例如箱子、矿车轨道、按钮等非整格的形状也会被精确匹配）</li>
 *   <li>在 {@link Render3DEvent} 中逐帧绘制填充/描边矩形</li>
 * </ol>
 *
 * <h3>性能</h3>
 * <p>扫描结果按 0.5s 缓存，渲染阶段只绘制缓存到的 AABB 列表，
 * 不会在每一帧重新遍历区块。</p>
 */
public class BlockESP extends Module {

    /** 可复选的目标方块（注册表 id）。 */
    private static final String[] BLOCK_IDS = {
            "minecraft:diamond_ore",
            "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore",
            "minecraft:deepslate_emerald_ore",
            "minecraft:gold_ore",
            "minecraft:deepslate_gold_ore",
            "minecraft:iron_ore",
            "minecraft:deepslate_iron_ore",
            "minecraft:coal_ore",
            "minecraft:deepslate_coal_ore",
            "minecraft:redstone_ore",
            "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore",
            "minecraft:deepslate_lapis_ore",
            "minecraft:copper_ore",
            "minecraft:deepslate_copper_ore",
            "minecraft:ancient_debris",
            "minecraft:nether_quartz_ore",
            "minecraft:nether_gold_ore",
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:ender_chest",
            "minecraft:spawner",
            "minecraft:furnace",
            "minecraft:barrel",
            "minecraft:tnt",
            "minecraft:bedrock"
    };

    /** 绘制模式：填充 / 描边 / 两者。 */
    private final ListValue mode = new ListValue("Mode", "Both", new String[]{
            "Both", "Fill", "Outline"
    });

    /** 每个方块一个开关，默认只勾选钻石矿石。 */
    private final BoolValue[] blockToggles = createBlockToggles();
    private final CheckboxValue blocks = new CheckboxValue("Blocks", blockToggles);

    /** 扫描半径（方块）。 */
    private final FloatValue radius = new FloatValue("Radius", 64f, 8f, 128f);

    /** 填充颜色。 */
    private final ColorValue fillColor = new ColorValue("Fill Color", 0x55FF5555);

    /** 描边颜色。 */
    private final ColorValue outlineColor = new ColorValue("Outline Color", 0xFFFF5555);

    /** 是否只高亮被遮挡（不在视野内）的方块。 */
    private final BoolValue onlyHidden = new BoolValue("Only Hidden", false);

    // ── 缓存状态 ──────────────────────────────────────────────────

    private final List<AABB> boxes = new ArrayList<>();
    private long lastScan = 0L;

    public BlockESP() {
        super("BlockESP", ModuleEnum.Visual);
        addValue(mode, blocks, radius, fillColor, outlineColor, onlyHidden);
    }

    /** 为每个方块 id 生成一个显示名友好的开关，默认勾选钻石矿石。 */
    private static BoolValue[] createBlockToggles() {
        BoolValue[] toggles = new BoolValue[BLOCK_IDS.length];
        for (int i = 0; i < BLOCK_IDS.length; i++) {
            String id = BLOCK_IDS[i];
            String name = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
            name = name.replace('_', ' ');
            toggles[i] = new BoolValue(name, i == 0);
        }
        return toggles;
    }

    @Override
    public void onDisabled() {
        boxes.clear();
        lastScan = 0L;
    }

    // ── 定时扫描 ──────────────────────────────────────────────────

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScan < 500) return; // 0.5s 缓存，避免每帧扫描
        lastScan = now;

        // 收集所有勾选的方块
        Set<Block> targets = new HashSet<>();
        for (int i = 0; i < blockToggles.length; i++) {
            if (!blockToggles[i].enabled) continue;
            Block target = BuiltInRegistries.BLOCK.getValue(Identifier.parse(BLOCK_IDS[i]));
            if (target != null) targets.add(target);
        }
        if (targets.isEmpty()) {
            boxes.clear();
            return;
        }

        boxes.clear();

        double px = mc.player.getX();
        double pz = mc.player.getZ();
        int r = (int) Math.ceil(radius.getValue());

        int minCX = (int) Math.floor((px - r) / 16.0);
        int maxCX = (int) Math.floor((px + r) / 16.0);
        int minCZ = (int) Math.floor((pz - r) / 16.0);
        int maxCZ = (int) Math.floor((pz + r) / 16.0);

        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                if (!mc.level.hasChunk(cx, cz)) continue;
                LevelChunk chunk = mc.level.getChunk(cx, cz);
                scanChunk(chunk, targets);
            }
        }
    }

    /** 扫描单个区块：跳过纯空气 section，命中目标方块时记录其形状 AABB。 */
    private void scanChunk(LevelChunk chunk, Set<Block> targets) {
        LevelChunkSection[] sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir()) continue;

            int baseY = chunk.getSectionYFromSectionIndex(i) << 4;
            int baseX = chunk.getPos().x() << 4;
            int baseZ = chunk.getPos().z() << 4;

            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < 16; y++) {
                    for (int z = 0; z < 16; z++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (!targets.contains(state.getBlock())) continue;

                        BlockPos pos = new BlockPos(baseX + x, baseY + y, baseZ + z);
                        if (onlyHidden.enabled && !isHidden(pos)) continue;

                        VoxelShape shape = state.getShape(mc.level, pos);
                        if (shape.isEmpty()) continue;

                        for (AABB bb : shape.toAabbs()) {
                            boxes.add(bb.move(pos.getX(), pos.getY(), pos.getZ()));
                        }
                    }
                }
            }
        }
    }

    /** 视线判定：从玩家眼睛向方块中心发射射线，若被其他实心方块阻挡则视为 hidden。 */
    private boolean isHidden(BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 target = Vec3.atCenterOf(pos);
        // 若目标方块中心被自身碰撞盒遮挡，先稍微偏移再判断
        ClipContext ctx = new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player);
        BlockHitResult hit = mc.level.clip(ctx);
        return hit.getType() != HitResult.Type.MISS && !hit.getBlockPos().equals(pos);
    }

    // ── 渲染 ──────────────────────────────────────────────────────

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || boxes.isEmpty()) return;

        int fill = fillColor.getColor();
        int outline = outlineColor.getColor();

        for (AABB box : boxes) {
            if (mode.is("Fill")) {
                RenderUtils.drawFilledBox(box, fill);
            } else if (mode.is("Outline")) {
                RenderUtils.drawOutlineBox(box, outline);
            } else {
                RenderUtils.drawFilledBox(box, fill);
                RenderUtils.drawOutlineBox(box, outline);
            }
        }
    }
}
