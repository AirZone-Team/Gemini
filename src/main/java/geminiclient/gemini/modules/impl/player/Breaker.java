package geminiclient.gemini.modules.impl.player;

import geminiclient.gemini.Gemini;
import geminiclient.gemini.base.RotationManager;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.utils.RenderUtils;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.CheckboxValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Breaker（隔墙破坏）— 在限定范围内破坏勾选的指定方块。
 * <p>
 * 不做视线检测，因此可以隔着墙破坏目标方块（服务端只校验距离，不校验视线）。
 * 两种模式：
 * <ul>
 *   <li>{@code Single}：通过 {@code MultiPlayerGameMode} 的原生挖掘流程逐个破坏最近的目标，
 *       与手动挖掘完全一致，兼容性最好；</li>
 *   <li>{@code Multi}：向范围内所有目标发送原版挖掘开始封包（经典 Nuker 方式），
 *       每 tick 轮询一次以保证服务端持续累积挖掘进度。</li>
 * </ul>
 */
public class Breaker extends Module {

    private static final String MODE_SINGLE = "Single";
    private static final String MODE_MULTI = "Multi";

    /**
     * 可复选的目标方块（注册表 id）。
     * <p>通配规则：{@code "minecraft:xxx*"} 匹配路径包含 xxx 的所有方块（如所有潜影盒）；
     * {@code "minecraft:*xxx"} 匹配路径以 xxx 结尾的所有方块（如所有床）。
     */
    private static final String[] BLOCK_IDS = {
            // 矿石
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
            // 战利品 / 机械
            "minecraft:chest",
            "minecraft:trapped_chest",
            "minecraft:ender_chest",
            "minecraft:spawner",
            "minecraft:hopper",
            "minecraft:furnace",
            "minecraft:blast_furnace",
            "minecraft:smoker",
            "minecraft:barrel",
            "minecraft:dispenser",
            "minecraft:dropper",
            // 家族通配
            "minecraft:shulker_box*", // 所有潜影盒（含染色变体）
            "minecraft:*_bed"         // 所有床
    };

    private final ListValue mode = new ListValue("Mode", MODE_SINGLE, new String[]{MODE_SINGLE, MODE_MULTI});
    private final FloatValue range = new FloatValue("Range", 4.5f, 1f, 6f);
    private final BoolValue rotate = new BoolValue("Rotate", true);
    private final BoolValue render = new BoolValue("Render", true);
    private final BoolValue progressBar = new BoolValue("Progress", true);
    private final IntValue maxTargets = new IntValue("Max Targets", 8, 1, 64, () -> mode.is(MODE_MULTI));

    private final BoolValue[] blockToggles = createBlockToggles();
    private final CheckboxValue blocks = new CheckboxValue("Blocks", blockToggles);

    /** 已解析的方块集合缓存（注册表在运行期固定，可按 id 缓存）。 */
    private static final Map<String, Set<Block>> ID_CACHE = new HashMap<>();

    // ── 运行状态 ──────────────────────────────────────────────────

    /** Single 模式：正在挖掘的目标。 */
    private BlockPos digging;

    /** Multi 模式：正在跟踪的挖掘目标（方块 → 挖掘时的原始方块）。 */
    private final Map<BlockPos, Block> multiTargets = new LinkedHashMap<>();

    /** 当前模式下需要渲染描边的方块。 */
    private final List<BlockPos> renderBoxes = new ArrayList<>();

    /** 各目标的客户端模拟挖掘进度（开始游戏刻 + 预计总游戏刻）。 */
    private final Map<BlockPos, DigState> activeProgress = new HashMap<>();

    /** 单个挖掘目标的进度状态。 */
    private record DigState(int startTick, int totalTicks) {
    }

    public Breaker() {
        super("Breaker", ModuleEnum.Player);
        addValue(mode, range, rotate, render, progressBar, maxTargets, blocks);
    }

    /** 为每个方块 id 生成一个开关，默认全部勾选。 */
    private static BoolValue[] createBlockToggles() {
        BoolValue[] toggles = new BoolValue[BLOCK_IDS.length];
        for (int i = 0; i < BLOCK_IDS.length; i++) {
            String id = BLOCK_IDS[i];
            String name = switch (id) {
                case "minecraft:shulker_box*" -> "Shulker Boxes";
                case "minecraft:*_bed" -> "Beds";
                default -> id.substring(id.indexOf(':') + 1).replace('_', ' ');
            };
            toggles[i] = new BoolValue(name, true);
        }
        return toggles;
    }

    /** 解析单个 id（含通配）为具体的方块集合。 */
    private static Set<Block> resolveBlocks(String id) {
        Set<Block> cached = ID_CACHE.get(id);
        if (cached != null) return cached;

        Set<Block> result = new HashSet<>();
        int colon = id.indexOf(':');
        String path = id.substring(colon + 1);

        if (id.endsWith("*")) {
            String prefix = path.substring(0, path.length() - 1);
            for (Block block : BuiltInRegistries.BLOCK) {
                if (BuiltInRegistries.BLOCK.getKey(block).getPath().contains(prefix)) {
                    result.add(block);
                }
            }
        } else if (path.startsWith("*")) {
            String suffix = path.substring(1);
            for (Block block : BuiltInRegistries.BLOCK) {
                if (BuiltInRegistries.BLOCK.getKey(block).getPath().endsWith(suffix)) {
                    result.add(block);
                }
            }
        } else {
            Block block = BuiltInRegistries.BLOCK.getValue(Identifier.parse(id));
            if (block != null) result.add(block);
        }

        ID_CACHE.put(id, result);
        return result;
    }

    private Set<Block> collectTargetBlocks() {
        Set<Block> targets = new HashSet<>();
        for (int i = 0; i < blockToggles.length; i++) {
            if (!blockToggles[i].enabled) continue;
            targets.addAll(resolveBlocks(BLOCK_IDS[i]));
        }
        return targets;
    }

    // ── 目标搜索 ──────────────────────────────────────────────────

    /** 搜索范围内勾选的方块，按与玩家的距离升序排列（与服务端距离校验一致）。 */
    private List<BlockPos> findTargets(Set<Block> targets, int limit) {
        if (mc.player == null || mc.level == null || targets.isEmpty()) return List.of();

        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();
        double reachSqr = range.getValue() * range.getValue();
        int ri = (int) Math.ceil(range.getValue());

        List<BlockPos> found = new ArrayList<>();
        BlockPos base = mc.player.blockPosition();
        for (int dx = -ri; dx <= ri; dx++) {
            for (int dy = -ri; dy <= ri; dy++) {
                for (int dz = -ri; dz <= ri; dz++) {
                    BlockPos pos = new BlockPos(base.getX() + dx, base.getY() + dy, base.getZ() + dz);
                    if (pos.distToCenterSqr(px, py, pz) > reachSqr) continue;

                    BlockState state = mc.level.getBlockState(pos);
                    if (state.isAir()) continue;
                    if (state.getDestroySpeed(mc.level, pos) < 0) continue; // 不可破坏（如基岩）
                    if (!targets.contains(state.getBlock())) continue;

                    found.add(pos);
                }
            }
        }

        found.sort(Comparator.comparingDouble(pos -> pos.distToCenterSqr(px, py, pz)));
        return found.size() > limit ? found.subList(0, limit) : found;
    }

    // ── 事件处理 ──────────────────────────────────────────────────

    @SuppressWarnings("unused")
    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null || mc.gameMode == null) return;

        Set<Block> targets = collectTargetBlocks();
        if (targets.isEmpty()) {
            stopAll();
            return;
        }

        if (mode.is(MODE_MULTI)) {
            updateMulti(targets);
        } else {
            updateSingle(targets);
        }
    }

    private void updateSingle(Set<Block> targets) {
        List<BlockPos> candidates = findTargets(targets, 1);
        BlockPos target = candidates.isEmpty() ? null : candidates.get(0);

        // 正在挖的方块已消失 / 超出范围 / 切换到别的目标 → 停止并发出 STOP 封包
        if (digging != null && !digging.equals(target)) {
            stopDigging();
        }

        if (target == null) {
            Gemini.rotationManager.releaseRotation(this);
            renderBoxes.clear();
            return;
        }

        Direction dir;
        if (rotate.enabled) {
            // 点击面由“将请求的旋转视线”决定：先判定朝向玩家一面的方向，再瞄准该面中心
            dir = rotationDirection(target);
            float[] angles = aimAngles(target, dir);
            Gemini.rotationManager.requestRotation(this, angles[0], angles[1],
                    RotationManager.PRIORITY_BREAKER, true);
        } else {
            // 不转头时按玩家当前实际视线判定命中的面，与原版 hitResult 语义一致
            dir = hitDirection(target, Vec3.directionFromRotation(mc.player.getXRot(), mc.player.getYRot()));
        }

        if (digging == null || !digging.equals(target)) {
            mc.gameMode.startDestroyBlock(target, dir);
            digging = target;
            activeProgress.put(target, progressState(target));
        } else {
            mc.gameMode.continueDestroyBlock(target, dir); // 裂纹动画
        }
        mc.player.swing(InteractionHand.MAIN_HAND);

        renderBoxes.clear();
        renderBoxes.add(target);
    }

    private void updateMulti(Set<Block> targets) {
        List<BlockPos> candidates = findTargets(targets, maxTargets.getValue());
        Set<BlockPos> candidateSet = new HashSet<>(candidates);

        double reachSqr = range.getValue() * range.getValue();
        double px = mc.player.getX();
        double py = mc.player.getY();
        double pz = mc.player.getZ();

        // 已破坏或超出范围的目标 → 发出 STOP 并移除跟踪
        Iterator<Map.Entry<BlockPos, Block>> it = multiTargets.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Block> entry = it.next();
            BlockPos pos = entry.getKey();
            BlockState state = mc.level.getBlockState(pos);
            boolean broken = state.isAir() || state.getBlock() != entry.getValue();
            boolean outOfRange = pos.distToCenterSqr(px, py, pz) > reachSqr;
            if (broken || outOfRange) {
                sendAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos);
                it.remove();
                activeProgress.remove(pos);
            }
        }

        // 新目标 → 发送开始挖掘
        for (BlockPos pos : candidates) {
            if (multiTargets.containsKey(pos)) continue;
            sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos);
            multiTargets.put(pos, mc.level.getBlockState(pos).getBlock());
            activeProgress.put(pos, progressState(pos));
        }

        // 轮询已跟踪目标：保证服务端 destroyBlockPos 持续落在各目标上累积挖掘进度
        for (BlockPos pos : multiTargets.keySet()) {
            if (candidateSet.contains(pos)) {
                sendAction(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos);
            }
        }

        if (rotate.enabled && !multiTargets.isEmpty()) {
            BlockPos nearest = multiTargets.keySet().stream()
                    .min(Comparator.comparingDouble(pos -> pos.distToCenterSqr(px, py, pz)))
                    .orElse(null);
            if (nearest != null) {
                Direction dir = rotationDirection(nearest);
                float[] angles = aimAngles(nearest, dir);
                Gemini.rotationManager.requestRotation(this, angles[0], angles[1],
                        RotationManager.PRIORITY_BREAKER, false);
            }
        } else {
            Gemini.rotationManager.releaseRotation(this);
        }

        if (!multiTargets.isEmpty()) {
            mc.player.swing(InteractionHand.MAIN_HAND);
        }

        renderBoxes.clear();
        renderBoxes.addAll(multiTargets.keySet());
    }

    /** 停止 Single 模式的挖掘并发出 STOP 封包。 */
    private void stopDigging() {
        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
        }
        if (digging != null) {
            activeProgress.remove(digging);
        }
        digging = null;
    }

    /** 停止一切破坏动作并释放旋转。 */
    private void stopAll() {
        if (mc.gameMode != null && mc.gameMode.isDestroying()) {
            mc.gameMode.stopDestroyBlock();
        }
        digging = null;
        for (BlockPos pos : new ArrayList<>(multiTargets.keySet())) {
            sendAction(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, pos);
        }
        multiTargets.clear();
        activeProgress.clear();
        renderBoxes.clear();
        Gemini.rotationManager.releaseRotation(this);
    }

    @Override
    public void onDisabled() {
        stopAll();
    }

    // ── 工具方法 ──────────────────────────────────────────────────

    /** 直接发送挖掘动作封包（Multi 模式绕过 gameMode 的单目标限制）。 */
    private void sendAction(ServerboundPlayerActionPacket.Action action, BlockPos pos) {
        if (mc.player == null || mc.level == null || mc.player.connection == null) return;
        mc.player.connection.getConnection()
                .send(new ServerboundPlayerActionPacket(action, pos, rotationDirection(pos)));
    }

    /** 结合旋转视线计算被点击的面：从眼睛向方块看去，判定目光会命中的面。 */
    private Direction rotationDirection(BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 look = Vec3.atCenterOf(pos).subtract(eye).normalize();
        Direction dir = hitDirection(pos, look);
        // 对准面中心后复算一次，保证发包的面与转头角度完全自洽
        Vec3 refined = faceCenter(pos, dir).subtract(eye).normalize();
        return hitDirection(pos, refined);
    }

    /** 沿 lookVec 视线求目标方块实际形状上的命中面；视线未命中时按几何主轴向兜底。 */
    private Direction hitDirection(BlockPos pos, Vec3 lookVec) {
        BlockState state = mc.level.getBlockState(pos);
        VoxelShape shape = state.getShape(mc.level, pos);
        AABB box = shape.isEmpty() ? new AABB(pos) : shape.bounds().move(pos);

        Vec3 eye = mc.player.getEyePosition();
        Optional<Vec3> hit = box.clip(eye, eye.add(lookVec.scale(6.0)));
        return hit.map(point -> faceOf(box, point)).orElseGet(() -> fallbackDirection(pos));
    }

    /** 命中的面：取交点距离最近的面（交点必在盒面上，不存在容差问题）。 */
    private static Direction faceOf(AABB box, Vec3 point) {
        double[] dists = {
                Math.abs(point.x - box.minX), Math.abs(point.x - box.maxX),
                Math.abs(point.y - box.minY), Math.abs(point.y - box.maxY),
                Math.abs(point.z - box.minZ), Math.abs(point.z - box.maxZ)};
        Direction[] dirs = {
                Direction.WEST, Direction.EAST,
                Direction.DOWN, Direction.UP,
                Direction.NORTH, Direction.SOUTH};
        int best = 0;
        for (int i = 1; i < dists.length; i++) {
            if (dists[i] < dists[best]) best = i;
        }
        return dirs[best];
    }

    /** 兜底：眼睛与方块中心的连线的几何主轴向。 */
    private Direction fallbackDirection(BlockPos pos) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        double dx = center.x - eye.x;
        double dy = center.y - eye.y;
        double dz = center.z - eye.z;
        double ax = Math.abs(dx);
        double ay = Math.abs(dy);
        double az = Math.abs(dz);
        if (ax >= ay && ax >= az) return dx >= 0 ? Direction.EAST : Direction.WEST;
        if (ay >= az) return dy >= 0 ? Direction.UP : Direction.DOWN;
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    /** 看向目标方块被点击面中心所需的 {yaw, pitch}。 */
    private float[] aimAngles(BlockPos pos, Direction dir) {
        Vec3 eye = mc.player.getEyePosition();
        Vec3 aim = faceCenter(pos, dir);
        Vec3 delta = aim.subtract(eye);
        float yaw = (float) (Math.atan2(delta.z, delta.x) * 180 / Math.PI) - 90;
        float pitch = (float) (-Math.atan2(delta.y, Math.sqrt(delta.x * delta.x + delta.z * delta.z)) * 180 / Math.PI);
        return new float[]{yaw, pitch};
    }

    /** 方块被点击面的中心点。 */
    private static Vec3 faceCenter(BlockPos pos, Direction dir) {
        return Vec3.atCenterOf(pos)
                .add(dir.getStepX() * 0.5, dir.getStepY() * 0.5, dir.getStepZ() * 0.5);
    }

    // ── 进度模拟 ──────────────────────────────────────────────────

    /** 根据方块的每 tick 挖掘速度估算总挖掘游戏刻数（≥1）。 */
    private DigState progressState(BlockPos pos) {
        float perTick = mc.level.getBlockState(pos).getDestroyProgress(mc.player, mc.level, pos);
        int totalTicks = perTick <= 0f ? 1 : Math.max(1, (int) Math.ceil(1.0 / perTick));
        return new DigState(mc.player.tickCount, totalTicks);
    }

    /** 当前进度 0~1。 */
    private float progressOf(BlockPos pos, DigState state) {
        int elapsed = mc.player.tickCount - state.startTick;
        return elapsed <= 0 ? 0f : Math.min(1f, elapsed / (float) state.totalTicks);
    }

    /** 进度色：绿 → 黄 → 红。 */
    private static int progressColor(float progress) {
        int r, g;
        if (progress < 0.5f) {
            r = (int) (255 * progress * 2);
            g = 255;
        } else {
            r = 255;
            g = (int) (255 * (1 - progress) * 2);
        }
        return 0xAA000000 | (r << 16) | (g << 8);
    }

    /** 在目标方块上绘制从底部升起的进度柱。 */
    private void drawProgressBar(BlockPos pos, float progress) {
        AABB box = new AABB(pos);
        double height = (box.maxY - box.minY) * progress;
        AABB bar = new AABB(box.minX + 0.08, box.minY, box.minZ + 0.08,
                box.maxX - 0.08, box.minY + height, box.maxZ - 0.08);
        RenderUtils.drawFilledBox(bar, progressColor(progress));
    }

    // ── 渲染 ──────────────────────────────────────────────────────

    @SuppressWarnings("unused")
    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (mc.level == null || mc.player == null || renderBoxes.isEmpty()) return;

        if (render.enabled) {
            for (BlockPos pos : renderBoxes) {
                if (mc.level.getBlockState(pos).isAir()) continue;
                RenderUtils.drawOutlineBox(new AABB(pos), 0xFF55FF55);
            }
        }

        if (progressBar.enabled) {
            for (BlockPos pos : renderBoxes) {
                DigState state = activeProgress.get(pos);
                if (state == null) continue;
                float p = progressOf(pos, state);
                if (p <= 0f) continue;
                drawProgressBar(pos, p);
            }
        }
    }
}