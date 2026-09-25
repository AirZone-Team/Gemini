package geminiclient.gemini.base;

import geminiclient.gemini.customRenderer.glsl.CustomFontRenderer;
import geminiclient.gemini.modules.ModuleEnum;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 中英双语字典：所有面向用户的文案（模块名、配置项、选项、界面文字）都以英文
 * 字符串为键查表。英文本身无需翻译，未收录的键直接回退原文。
 *
 * <p>设计约束：{@code ValueParent.getName()} / {@code Module.getName()} 同时是
 * {@link FileSystem} 的配置键与 C# 增量同步的 key，因此内部 key 一律保持英文，
 * 只在渲染时通过 {@link #tr} 转成当前语言。</p>
 */
public final class I18n {

    public enum Language {
        CHINESE("zh", "中"),
        ENGLISH("en", "EN");

        private final String code;
        private final String label;

        Language(String code, String label) {
            this.code = code;
            this.label = label;
        }

        public String code() {
            return code;
        }

        /** 主菜单语言切换按钮上显示的文字。 */
        public String label() {
            return label;
        }

        public static Language fromCode(String code) {
            if ("zh".equalsIgnoreCase(code)) {
                return CHINESE;
            }
            return ENGLISH;
        }
    }

    private I18n() {
    }

    private static volatile Language language = Language.CHINESE;

    private static final Map<String, String> ZH = new HashMap<>();
    private static final Map<String, String> EN = new HashMap<>();

    /** 当前界面语言。 */
    public static Language getLanguage() {
        return language;
    }

    public static void setLanguage(Language lang) {
        language = lang != null ? lang : Language.CHINESE;
    }

    public static boolean isChinese() {
        return language == Language.CHINESE;
    }

    /** 翻译一个英文键；未收录则回退英文原文。 */
    public static String tr(String key) {
        if (key == null) {
            return "";
        }
        return language == Language.CHINESE ? ZH.getOrDefault(key, key) : EN.getOrDefault(key, key);
    }

    /** 模块名显示文本。 */
    public static String module(String moduleName) {
        return tr(moduleName);
    }

    /** 分类显示文本。 */
    public static String category(ModuleEnum category) {
        return tr(category.name());
    }

    /** ListValue 选项显示文本（列表项仍按英文原串比较/选择）。 */
    public static String option(String option) {
        return tr(option);
    }

    /** 支持 "%s" 占位符的翻译。 */
    public static String trf(String key, Object... args) {
        return String.format(Locale.ROOT, tr(key), args);
    }

    /**
     * 当前语言下所有中文文案的码点集合（供字形预热）。英文模式下返回空集，
     * 避免为不必要的字形做三角化。
     */
    public static Set<Integer> cjkCodepoints() {
        Set<Integer> cps = new LinkedHashSet<>();
        if (language != Language.CHINESE) {
            return cps;
        }
        Collection<String> values = ZH.values();
        for (String value : values) {
            for (int i = 0; i < value.length(); ) {
                int cp = value.codePointAt(i);
                cps.add(cp);
                i += Character.charCount(cp);
            }
        }
        return cps;
    }

    /**
     * 启动时预热的文案（按码点去重）：仅主菜单可见的少量文字。中文模式下这些
     * 是进入游戏后第一屏的全部中文，预热后首帧无需现场三角化。
     *
     * <p>覆盖范围刻意保持很小（约 20+ 个汉字）：三角化仍是一次性的（18px 实测稳态
     * 约 1.5ms/字形、p90 约 3.5ms，远低于旧 MSDF 的约 17ms/字形），但字形
     * 一旦预热就常驻数百个三角形的顶点数据，且进程里头几个字形还要承担 JIT 冷
     * 启动（数十毫秒）。现在扩大预热集的成本已经很低，若首屏文案变多可直接加
     * 进来。其余界面（ClickGui、AltManager 等）的中文仍由
     * {@link CustomFontRenderer} 惰性三角化，缺失字形有 vanilla 兜底，
     * 不会出现空白。英文模式返回空串。</p>
     */
    public static String warmupText() {
        if (language != Language.CHINESE) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String key : WARMUP_KEYS) {
            String v = ZH.get(key);
            if (v != null) {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    /** 预热覆盖的文案键（主菜单可见文字，按码点去重后成本受限于其规模）。 */
    private static final String[] WARMUP_KEYS = {
            "Singleplayer", "Multiplayer", "Settings", "Alt Manager", "Exit",
            "Modern Minecraft Client", "Gemini Client", "Github", "Discord",
            "↑↓  Select    Enter  Open"
    };

    private static void zh(String key, String value) {
        ZH.put(key, value);
    }

    private static void en(String key, String value) {
        EN.put(key, value);
    }

    private static void pair(String key, String zhValue, String enValue) {
        ZH.put(key, zhValue);
        EN.put(key, enValue);
    }

    static {
        // ── 分类 ──
        pair("Combat", "战斗", "Combat");
        pair("Movement", "移动", "Movement");
        pair("Player", "玩家", "Player");
        pair("Visual", "视觉", "Visual");

        // ── 模块名 ──
        pair("Arraylists", "阵列", "Arraylists");
        pair("AutoTool", "自动工具", "AutoTool");
        pair("BackTrack", "回溯", "BackTrack");
        pair("BlackHolePet", "黑洞宠物", "BlackHolePet");
        pair("Blink", "闪烁", "Blink");
        pair("BlockESP", "方块ESP", "BlockESP");
        pair("Breaker", "隔墙破坏", "Breaker");
        pair("ClickGui", "界面配置", "ClickGui");
        pair("Crit", "暴击", "Crit");
        pair("Disabler", "禁用器", "Disabler");
        pair("DynamicIsland", "动态岛", "DynamicIsland");
        pair("ESP", "透视", "ESP");
        pair("EffectDisplay", "特效显示", "EffectDisplay");
        pair("Fall00", "无摔落", "Fall00");
        pair("FullLight", "满亮度", "FullLight");
        pair("GhostAfterImage", "幻影残影", "GhostAfterImage");
        pair("Heypixel", "空岛", "Heypixel");
        pair("InstancedParticle", "实例粒子", "InstancedParticle");
        pair("InvManager", "物品管理", "InvManager");
        pair("InvMove", "背包移动", "InvMove");
        pair("ItemPhysical", "物品物理", "ItemPhysical");
        pair("JumpCircle", "跳跃光环", "JumpCircle");
        pair("KeepSprint", "保持疾跑", "KeepSprint");
        pair("KillAura", "杀戮光环", "KillAura");
        pair("KillEffect", "击杀特效", "KillEffect");
        pair("MagicHalo", "魔法光环", "MagicHalo");
        pair("MovementFix", "移动修正", "MovementFix");
        pair("NoFall", "无摔落", "NoFall");
        pair("NoJumpDelay", "跳跃无延迟", "NoJumpDelay");
        pair("NoSlow", "无减速", "NoSlow");
        pair("NoWeb", "无蛛网", "NoWeb");
        pair("Notification", "通知", "Notification");
        pair("Packet", "数据包", "Packet");
        pair("Radar", "雷达", "Radar");
        pair("Scaffold", "搭路", "Scaffold");
        pair("SkyLantern", "天灯", "SkyLantern");
        pair("Speed", "加速", "Speed");
        pair("SpoofLanding", "落地欺骗", "SpoofLanding");
        pair("Sprint", "疾跑", "Sprint");
        pair("Stealer", "窃取", "Stealer");
        pair("SuperKB", "超级击退", "SuperKB");
        pair("SweepingAttackVFX", "横扫特效", "SweepingAttackVFX");
        pair("TargetDisplay", "目标显示", "TargetDisplay");
        pair("Trail", "轨迹", "Trail");
        pair("Trajectories", "弹道预测", "Trajectories");
        pair("Velocity", "反击退", "Velocity");
        pair("WaterMark", "水印", "WaterMark");

        // ── 配置项 ──
        pair("Accent", "强调色", "Accent");
        pair("Accent Color", "强调色", "Accent Color");
        pair("Accent Mix", "强调混合", "Accent Mix");
        pair("AdrenalineBoost", "肾上腺素加成", "AdrenalineBoost");
        pair("AdrenalineRange", "肾上腺素范围", "AdrenalineRange");
        pair("Afterimages", "残影", "Afterimages");
        pair("AimLatency", "瞄准延迟", "AimLatency");
        pair("AimMode", "瞄准模式", "AimMode");
        pair("AimModulo360", "360度瞄准", "AimModulo360");
        pair("AimPointSmooth", "瞄准点平滑", "AimPointSmooth");
        pair("Air Tear", "撕裂空气", "Air Tear");
        pair("Alpha", "透明度", "Alpha");
        pair("Anim Speed", "动画速度", "Anim Speed");
        pair("Animal", "动物", "Animal");
        pair("Animals", "动物", "Animals");
        pair("Animation", "动画", "Animation");
        pair("Animation Speed", "动画速度", "Animation Speed");
        pair("Arc Angle", "弧线角度", "Arc Angle");
        pair("Arc Layers", "弧线层数", "Arc Layers");
        pair("Arc Lift", "弧线抬升", "Arc Lift");
        pair("Arrows & Tridents", "箭与三叉戟", "Arrows & Tridents");
        pair("AttackAnimals", "攻击动物", "AttackAnimals");
        pair("AttackDead", "攻击尸体", "AttackDead");
        pair("AttackMobs", "攻击怪物", "AttackMobs");
        pair("AttackPlayers", "攻击玩家", "AttackPlayers");
        pair("AttackTeammates", "攻击队友", "AttackTeammates");
        pair("Auto Armor", "自动护甲", "Auto Armor");
        pair("AutoDisableEmpty", "空手自动禁用", "AutoDisableEmpty");
        pair("BG Gradient", "背景渐变", "BG Gradient");
        pair("BG Tint", "背景着色", "BG Tint");
        pair("BG-Alpha", "背景透明度", "BG-Alpha");
        pair("BG-Expand", "背景展开", "BG-Expand");
        pair("BezierRandomness", "贝塞尔随机", "BezierRandomness");
        pair("Blend", "混合", "Blend");
        pair("BlockCounter", "方块计数", "BlockCounter");
        pair("BlockRayTrace", "方块射线", "BlockRayTrace");
        pair("Blocks", "方块", "Blocks");
        pair("Bloom", "泛光", "Bloom");
        pair("Blur", "模糊", "Blur");
        pair("Bolt Count", "闪电数量", "Bolt Count");
        pair("Bolt Width", "闪电宽度", "Bolt Width");
        pair("Border", "边框", "Border");
        pair("Bow Priority", "弓优先级", "Bow Priority");
        pair("BowMode", "弓模式", "BowMode");
        pair("Bows", "弓", "Bows");
        pair("Birth Burst", "出生爆发", "Birth Burst");
        pair("Box Color", "框体颜色", "Box Color");
        pair("Brightness", "亮度", "Brightness");
        pair("CPS", "每秒点击", "CPS");
        pair("Camera Shake", "镜头震动", "Camera Shake");
        pair("Capture Interval", "捕获间隔", "Capture Interval");
        pair("Card Color", "卡片颜色", "Card Color");
        pair("Case", "大小写", "Case");
        pair("Chance", "概率", "Chance");
        pair("Chromatic Split", "色差分离", "Chromatic Split");
        pair("Circle Color", "魔法阵颜色", "Circle Color");
        pair("Clarity", "清晰度", "Clarity");
        pair("ClickVariance", "点击偏差", "ClickVariance");
        pair("CloseDelay", "关闭延迟", "CloseDelay");
        pair("Color", "颜色", "Color");
        pair("Color Flow", "颜色流动", "Color Flow");
        pair("Color Mode", "颜色模式", "Color Mode");
        pair("Color Variation", "颜色变化", "Color Variation");
        pair("CombatSwitch", "战斗切换", "CombatSwitch");
        pair("Compact", "紧凑", "Compact");
        pair("Core Burst", "核心爆发", "Core Burst");
        pair("Core Color", "核心颜色", "Core Color");
        pair("Core Width", "核心宽度", "Core Width");
        pair("CountScope", "计数范围", "CountScope");
        pair("CounterShadow", "计数阴影", "CounterShadow");
        pair("Crown", "皇冠", "Crown");
        pair("Dash Density", "冲刺密度", "Dash Density");
        pair("Delay", "延迟", "Delay");
        pair("Density", "密度", "Density");
        pair("Depth Mode", "深度模式", "Depth Mode");
        pair("Detail", "细节", "Detail");
        pair("Detail Scale", "细节缩放", "Detail Scale");
        pair("DistanceSpread", "距离扩散", "DistanceSpread");
        pair("Distortion", "扭曲", "Distortion");
        pair("DoubleClickChance", "双击概率", "DoubleClickChance");
        pair("DoubleClickGap", "双击间隔", "DoubleClickGap");
        pair("Drift", "漂移", "Drift");
        pair("Dual Tone", "双色调", "Dual Tone");
        pair("Duration", "持续时间", "Duration");
        pair("Dynamic Color", "动态颜色", "Dynamic Color");
        pair("Dynamics", "动感", "Dynamics");
        pair("Easing", "缓动", "Easing");
        pair("EasingCurve", "缓动曲线", "EasingCurve");
        pair("Echo Spacing", "回声间隔", "Echo Spacing");
        pair("Echoes", "回声层", "Echoes");
        pair("Edge Glow", "边缘发光", "Edge Glow");
        pair("Eggs & Snowballs", "鸡蛋与雪球", "Eggs & Snowballs");
        pair("Ender Pearls", "末影珍珠", "Ender Pearls");
        pair("Energy Arc", "能量弧光", "Energy Arc");
        pair("Energy Flow", "能量流动", "Energy Flow");
        pair("Energy Ribbon", "能量飘带", "Energy Ribbon");
        pair("Entity Filters", "实体过滤", "Entity Filters");
        pair("Entity Hit", "命中实体", "Entity Hit");
        pair("FOV", "视野", "FOV");
        pair("Factor", "系数", "Factor");
        pair("Feather Color", "羽毛颜色", "Feather Color");
        pair("Feathers", "羽毛", "Feathers");
        pair("Fill Color", "填充颜色", "Fill Color");
        pair("Fishing Hooks", "钓鱼钩", "Fishing Hooks");
        pair("Fishing Rods", "钓鱼竿", "Fishing Rods");
        pair("Flame Power", "火焰强度", "Flame Power");
        pair("Flight Effects", "飞行特效", "Flight Effects");
        pair("Flight Entities", "飞行实体", "Flight Entities");
        pair("Flight Speed", "飞行速度", "Flight Speed");
        pair("Flow Speed", "流动速度", "Flow Speed");
        pair("Font", "字体", "Font");
        pair("Font Color", "字体颜色", "Font Color");
        pair("FoodMode", "食物模式", "FoodMode");
        pair("Ghost Color", "幻影颜色", "Ghost Color");
        pair("Glow", "发光", "Glow");
        pair("Grid", "网格", "Grid");
        pair("Guide Animation", "引导动画", "Guide Animation");
        pair("Guide End", "引导末端", "Guide End");
        pair("Guide Glow", "引导发光", "Guide Glow");
        pair("Guide Opacity", "引导透明度", "Guide Opacity");
        pair("Guide Start", "引导起点", "Guide Start");
        pair("Guide Style", "引导样式", "Guide Style");
        pair("Guide Width", "引导宽度", "Guide Width");
        pair("Halo Intensity", "光环强度", "Halo Intensity");
        pair("Halo Size", "光环大小", "Halo Size");
        pair("Hands", "手部数量", "Hands");
        pair("Health Alert", "生命警报", "Health Alert");
        pair("Held Only", "仅手持", "Held Only");
        pair("Heavy Color", "重落颜色", "Heavy Color");
        pair("Heavy Landing", "重落", "Heavy Landing");
        pair("Heavy Scale", "重落缩放", "Heavy Scale");
        pair("Heavy Threshold", "重落阈值", "Heavy Threshold");
        pair("Height", "高度", "Height");
        pair("Height Offset", "高度偏移", "Height Offset");
        pair("Hexagon Color", "六边形颜色", "Hexagon Color");
        pair("Hexagons", "六边形", "Hexagons");
        pair("Hue Range", "色相范围", "Hue Range");
        pair("HurtTime", "受击时间", "HurtTime");
        pair("Icon BG", "图标背景", "Icon BG");
        pair("Icon Color", "图标颜色", "Icon Color");
        pair("Icon Pulse", "图标脉冲", "Icon Pulse");
        pair("Icons", "图标", "Icons");
        pair("Impact Burst", "撞击爆裂", "Impact Burst");
        pair("Impact Duration", "撞击持续时间", "Impact Duration");
        pair("Impact Scaling", "撞击缩放", "Impact Scaling");
        pair("Impact Size", "撞击大小", "Impact Size");
        pair("Impact Vignette", "撞击暗角", "Impact Vignette");
        // ── KillAura Sorcery Array 指示器（魔导咒阵）──
        pair("Acquire Flash", "锁定闪光", "Acquire Flash");
        pair("Attack Pulse", "攻击脉冲", "Attack Pulse");
        pair("Aura Ring", "身后咒环", "Aura Ring");
        pair("Ground Sigil", "地面法阵", "Ground Sigil");
        pair("IndicatorGlow", "指示器辉光", "IndicatorGlow");
        pair("IndicatorLayers", "指示器图层", "IndicatorLayers");
        pair("Orbit Comets", "环绕彗星", "Orbit Comets");
        pair("Star Motes", "星屑", "Star Motes");
        pair("Theme", "主题", "Theme");
        pair("ThemeAccent", "主题点缀色", "ThemeAccent");
        pair("ThemePrimary", "主题主色", "ThemePrimary");
        pair("ThemeRainbowSpeed", "主题彩虹速度", "ThemeRainbowSpeed");
        pair("ThemeSecondary", "主题次色", "ThemeSecondary");

        pair("IndicatorOpacity", "指示器透明度", "IndicatorOpacity");
        pair("IndicatorParticleSize", "指示器粒子大小", "IndicatorParticleSize");
        pair("IndicatorParticles", "指示器粒子", "IndicatorParticles");
        pair("IndicatorPulse", "指示器脉冲", "IndicatorPulse");
        pair("IndicatorPulseSpeed", "指示器脉冲速度", "IndicatorPulseSpeed");
        pair("IndicatorRadius", "指示器半径", "IndicatorRadius");
        pair("IndicatorRotationSpeed", "指示器旋转速度", "IndicatorRotationSpeed");
        pair("IndicatorTargets", "指示器目标", "IndicatorTargets");
        pair("IndicatorYOffset", "指示器Y偏移", "IndicatorYOffset");
        pair("Intensity", "强度", "Intensity");
        pair("Interval", "间隔", "Interval");
        pair("Inventory Only", "仅背包", "Inventory Only");
        pair("Invisible", "隐形", "Invisible");
        pair("Item", "物品", "Item");
        pair("Items", "物品", "Items");
        pair("Keep Lava Buckets", "保留岩浆桶", "Keep Lava Buckets");
        pair("Keep Projectile", "保留投射物", "Keep Projectile");
        pair("Keep Water Buckets", "保留水桶", "Keep Water Buckets");
        pair("KeepY", "保持高度", "KeepY");
        pair("Landing", "落地", "Landing");
        pair("Landing Color", "落地颜色", "Landing Color");
        pair("Landing Marker", "落地标记", "Landing Marker");
        pair("Lantern Embers", "灯笼余烬", "Lantern Embers");
        pair("Lanterns", "灯笼", "Lanterns");
        pair("LatencyVariance", "延迟波动", "LatencyVariance");
        pair("Layers", "层数", "Layers");
        pair("Life", "寿命", "Life");
        pair("Lifetime", "生命周期", "Lifetime");
        pair("Light Trails", "光轨", "Light Trails");
        pair("Lightning", "闪电", "Lightning");
        pair("Line Count", "线条数量", "Line Count");
        pair("Line Length", "线条长度", "Line Length");
        pair("Line Thickness", "线条粗细", "Line Thickness");
        pair("Line Width", "线条宽度", "Line Width");
        pair("LowBlockThreshold", "低方块阈值", "LowBlockThreshold");
        pair("MD3 Button Row Height", "按钮行高", "MD3 Button Row Height");
        pair("MD3 Button Scale", "按钮缩放", "MD3 Button Scale");
        pair("MD3 Scale", "界面缩放", "MD3 Scale");
        pair("MD3 Slider Handle", "滑块手柄", "MD3 Slider Handle");
        pair("MD3 Slider Height", "滑块高度", "MD3 Slider Height");
        pair("MD3 Slider Track", "滑块轨道", "MD3 Slider Track");
        pair("Magic Projectiles", "魔法投射物", "Magic Projectiles");
        pair("Marker Pulse", "标记脉冲", "Marker Pulse");
        pair("Marker Size", "标记大小", "Marker Size");
        pair("Marker Style", "标记样式", "Marker Style");
        pair("Max Arrow Size", "最大箭矢大小", "Max Arrow Size");
        pair("Max Block Size", "最大方块大小", "Max Block Size");
        pair("Max Circles", "最大光环数", "Max Circles");
        pair("Max Effects", "最大特效数", "Max Effects");
        pair("Max Ghosts", "最大幻影数", "Max Ghosts");
        pair("Max Height", "最大高度", "Max Height");
        pair("Max Particles", "最大粒子数", "Max Particles");
        pair("Max Points", "最大点数", "Max Points");
        pair("Max Projectile Size", "最大投射物大小", "Max Projectile Size");
        pair("Message Color", "消息颜色", "Message Color");
        pair("MicroTremor", "微震动", "MicroTremor");
        pair("Min Height", "最小高度", "Min Height");
        pair("Min Movement", "最小移动", "Min Movement");
        pair("MinThreshold", "最小阈值", "MinThreshold");
        pair("Mob", "怪物", "Mob");
        pair("Mobs", "怪物", "Mobs");
        pair("Mode", "模式", "Mode");
        pair("Modes", "模式", "Modes");
        pair("Module BG", "模块背景", "Module BG");
        pair("Moon Butterflies", "月光蝴蝶", "Moon Butterflies");
        pair("MovementReaction", "移动反应", "MovementReaction");
        pair("NoCoolDown", "无冷却", "NoCoolDown");
        pair("NoRenderModules", "不渲染模块", "NoRenderModules");
        pair("NoiseSmoothness", "噪点平滑", "NoiseSmoothness");
        pair("Offhand", "副手", "Offhand");
        pair("Only Hidden", "仅隐藏", "Only Hidden");
        pair("Opacity", "透明度", "Opacity");
        pair("Open Delay", "打开延迟", "Open Delay");
        pair("OpenDelay", "打开延迟", "OpenDelay");
        pair("OpeningScreen", "打开界面", "OpeningScreen");
        pair("Orbit Height", "轨道高度", "Orbit Height");
        pair("Orbit Radius", "轨道半径", "Orbit Radius");
        pair("Orbit Ring", "环绕法阵", "Orbit Ring");
        pair("Orbitals", "环绕物", "Orbitals");
        pair("Orientation", "朝向", "Orientation");
        pair("OtherMode", "其他模式", "OtherMode");
        pair("Outline Color", "描边颜色", "Outline Color");
        pair("Outline Width", "描边宽度", "Outline Width");
        pair("Enchanted Only", "仅附魔物品", "Enchanted Only");
        pair("Overshoot", "过冲", "Overshoot");
        pair("Palette", "调色板", "Palette");
        pair("Paper Cranes", "纸鹤", "Paper Cranes");
        pair("Particle Count", "粒子数量", "Particle Count");
        pair("Particle Gravity", "粒子重力", "Particle Gravity");
        pair("Particle Size", "粒子大小", "Particle Size");
        pair("Particle Speed", "粒子速度", "Particle Speed");
        pair("Particle Spread", "粒子扩散", "Particle Spread");
        pair("Particles", "粒子", "Particles");
        pair("PauseEvery", "每帧暂停", "PauseEvery");
        pair("PauseLength", "暂停时长", "PauseLength");
        pair("Pearls", "珍珠", "Pearls");
        pair("PerlinAmplitude", "柏林振幅", "PerlinAmplitude");
        pair("PerlinFrequency", "柏林频率", "PerlinFrequency");
        pair("PerlinLacunarity", "柏林间隙", "PerlinLacunarity");
        pair("PerlinOctaves", "柏林倍频", "PerlinOctaves");
        pair("PerlinPersistence", "柏林持续性", "PerlinPersistence");
        pair("Player", "玩家", "Player");
        pair("Player Dot", "玩家圆点", "Player Dot");
        pair("Player Ring", "玩家圆环", "Player Ring");
        pair("Players", "玩家", "Players");
        pair("Post FX", "后期特效", "Post FX");
        pair("Potions", "药水", "Potions");
        pair("Predict Others", "预测他人", "Predict Others");
        pair("Predict Self", "预测自身", "Predict Self");
        pair("Prediction", "预测", "Prediction");
        pair("Prediction Items", "预测物品", "Prediction Items");
        pair("Prediction Steps", "预测步数", "Prediction Steps");
        pair("Preset", "预设", "Preset");
        pair("Primary", "主色", "Primary");
        pair("Primary Color", "主色", "Primary Color");
        pair("Priority", "优先级", "Priority");
        pair("Progress Bar", "进度条", "Progress Bar");
        pair("Progress Ring", "进度环", "Progress Ring");
        pair("Projectile Halo", "投射物光环", "Projectile Halo");
        pair("ProtectTool", "保护工具", "ProtectTool");
        pair("Pulse", "脉冲", "Pulse");
        pair("Pulse Speed", "脉冲速度", "Pulse Speed");
        pair("PulseStrength", "脉冲强度", "PulseStrength");
        pair("Quality", "质量", "Quality");
        pair("Radius", "半径", "Radius");
        pair("Rainbow Speed", "彩虹速度", "Rainbow Speed");
        pair("RandomAimInterval", "随机瞄准间隔", "RandomAimInterval");
        pair("RandomEasing", "随机缓动", "RandomEasing");
        pair("Range", "范围", "Range");
        pair("RayCheck", "射线检测", "RayCheck");
        pair("RayTrace", "射线检测", "RayTrace");
        pair("ReactionDelay", "反应延迟", "ReactionDelay");
        pair("Rect", "矩形", "Rect");
        pair("Rect Color", "矩形颜色", "Rect Color");
        pair("Rect-Color", "矩形颜色", "Rect-Color");
        pair("Render", "渲染", "Render");
        pair("Render Distance", "渲染距离", "Render Distance");
        pair("RepositionThreshold", "重定位阈值", "RepositionThreshold");
        pair("RequireSneak", "需潜行", "RequireSneak");
        pair("Reset On Target Switch", "切换目标时重置", "Reset On Target Switch");
        pair("Reset On Velocity", "受击时重置", "Reset On Velocity");
        pair("Ribbon Width", "飘带宽度", "Ribbon Width");
        pair("Ring Count", "圆环数量", "Ring Count");
        pair("Ring Layers", "圆环层数", "Ring Layers");
        pair("Ring Radius", "圆环半径", "Ring Radius");
        pair("Ring Scale", "圆环缩放", "Ring Scale");
        pair("Ring Thickness", "圆环粗细", "Ring Thickness");
        pair("Ring Track", "圆环轨道", "Ring Track");
        pair("Ring Wave Bottom", "圆环底部波动", "Ring Wave Bottom");
        pair("Ring Wave Top", "圆环顶部波动", "Ring Wave Top");
        pair("RotateSpeed", "旋转速度", "RotateSpeed");
        pair("Rotation", "旋转", "Rotation");
        pair("RotationBackSpeed", "回正速度", "RotationBackSpeed");
        pair("RotationJitter", "旋转抖动", "RotationJitter");
        pair("RotationMode", "旋转模式", "RotationMode");
        pair("RotationSpeed", "旋转速度", "RotationSpeed");
        pair("RotationVariation", "旋转变化", "RotationVariation");
        pair("Rune Color", "符文颜色", "Rune Color");
        pair("Rune Crown", "符文皇冠", "Rune Crown");
        pair("Rune Detail", "符文细节", "Rune Detail");
        pair("Runes", "符文", "Runes");
        pair("SafeWalk", "安全行走", "SafeWalk");
        pair("Saturation", "饱和度", "Saturation");
        pair("Screen Distortion", "屏幕扭曲", "Screen Distortion");
        pair("Screen Flash", "屏幕闪光", "Screen Flash");
        pair("Secondary", "次色", "Secondary");
        pair("Secondary Color", "次色", "Secondary Color");
        pair("Separators", "分隔线", "Separators");
        pair("SettleRange", "稳定范围", "SettleRange");
        pair("SettleWobble", "稳定摆动", "SettleWobble");
        pair("Shadow", "阴影", "Shadow");
        pair("Shadow Color", "阴影颜色", "Shadow Color");
        pair("Shadow Size", "阴影大小", "Shadow Size");
        pair("Sharpness", "锋利度", "Sharpness");
        pair("Shockwave", "冲击波", "Shockwave");
        pair("Shockwave Rings", "冲击波圆环", "Shockwave Rings");
        pair("Shooting Stars", "流星", "Shooting Stars");
        pair("SilentRotate", "静默旋转", "SilentRotate");
        pair("Size", "大小", "Size");
        pair("Size Variation", "大小变化", "Size Variation");
        pair("Sky Mix", "天空混合", "Sky Mix");
        pair("Slash Style", "斩击样式", "Slash Style");
        pair("Slot 1", "槽位1", "Slot 1");
        pair("Slot 2", "槽位2", "Slot 2");
        pair("Slot 3", "槽位3", "Slot 3");
        pair("Slot 4", "槽位4", "Slot 4");
        pair("Slot 5", "槽位5", "Slot 5");
        pair("Slot 6", "槽位6", "Slot 6");
        pair("Slot 7", "槽位7", "Slot 7");
        pair("Slot 8", "槽位8", "Slot 8");
        pair("Slot 9", "槽位9", "Slot 9");
        pair("SmoothStyle", "平滑样式", "SmoothStyle");
        pair("Soft Halos", "柔和光晕", "Soft Halos");
        pair("Sort", "排序", "Sort");
        pair("Space", "间距", "Space");
        pair("Spark Density", "火花密度", "Spark Density");
        pair("Spark Size", "火花大小", "Spark Size");
        pair("Sparkles", "火花", "Sparkles");
        pair("Spawn Delay", "生成延迟", "Spawn Delay");
        pair("Spawn Radius", "生成半径", "Spawn Radius");
        pair("Spawn Rate", "生成速率", "Spawn Rate");
        pair("Speed", "速度", "Speed");
        pair("Speed Lines", "速度线", "Speed Lines");
        pair("SpeedFluctuation", "速度波动", "SpeedFluctuation");
        pair("SpeedNoise", "速度噪点", "SpeedNoise");
        pair("Spike Count", "尖刺数量", "Spike Count");
        pair("Spike Length", "尖刺长度", "Spike Length");
        pair("Spikes", "尖刺", "Spikes");
        pair("Spin", "旋转", "Spin");
        pair("Spirit Koi", "灵鲤", "Spirit Koi");
        pair("Star Density", "星密度", "Star Density");
        pair("Stardust", "星尘", "Stardust");
        pair("Starfield", "星空", "Starfield");
        pair("Starlight Color", "星光颜色", "Starlight Color");
        pair("Starlights", "星光", "Starlights");
        pair("StealDelay", "窃取延迟", "StealDelay");
        pair("StopWorking", "停止工作", "StopWorking");
        pair("Style", "样式", "Style");
        pair("Surface Color", "表面颜色", "Surface Color");
        pair("SwapBack", "换回", "SwapBack");
        pair("SwapMode", "切换模式", "SwapMode");
        pair("SwingHand", "挥动手臂", "SwingHand");
        pair("SwitchDelay", "切换延迟", "SwitchDelay");
        pair("SwitchMode", "切换模式", "SwitchMode");
        pair("SwordMode", "剑模式", "SwordMode");
        pair("Tag Color", "标签颜色", "Tag Color");
        pair("Tag-Color", "标签颜色", "Tag-Color");
        pair("Tail Width", "尾部宽度", "Tail Width");
        pair("Takeoff", "起飞", "Takeoff");
        pair("Takeoff Color", "起飞颜色", "Takeoff Color");
        pair("Tear Hold", "裂口滞留", "Tear Hold");
        pair("Tear Slices", "裂空条数", "Tear Slices");
        pair("Tear Width", "裂口宽度", "Tear Width");
        pair("TargetEffects", "目标特效", "TargetEffects");
        pair("TargetIndicator", "目标指示器", "TargetIndicator");
        pair("Targets", "目标", "Targets");
        pair("TellyTick", "搭路周期", "TellyTick");
        pair("Text Gradient", "文字渐变", "Text Gradient");
        pair("Text Shadow", "文字阴影", "Text Shadow");
        pair("Text-Color", "文字颜色", "Text-Color");
        pair("TextAlpha", "文字透明度", "TextAlpha");
        pair("TextY", "文字Y偏移", "TextY");
        pair("Thickness", "粗细", "Thickness");
        pair("Through Walls", "透视墙体", "Through Walls");
        pair("Throw Items", "投掷物品", "Throw Items");
        pair("Thrown Items", "已投掷物品", "Thrown Items");
        pair("Thrown Potions", "已投掷药水", "Thrown Potions");
        pair("Tilt", "倾斜", "Tilt");
        pair("Time Scale", "时间缩放", "Time Scale");
        pair("Title Color", "标题颜色", "Title Color");
        pair("Trail Colors", "轨迹颜色", "Trail Colors");
        pair("Trail Detail", "轨迹细节", "Trail Detail");
        pair("Trail Glow", "轨迹发光", "Trail Glow");
        pair("Trail Lifetime", "轨迹寿命", "Trail Lifetime");
        pair("Trail Opacity", "轨迹透明度", "Trail Opacity");
        pair("Trail Primary", "轨迹主色", "Trail Primary");
        pair("Trail Secondary", "轨迹次色", "Trail Secondary");
        pair("Trail Sparks", "轨迹火花", "Trail Sparks");
        pair("Trail While Idle", "空闲时轨迹", "Trail While Idle");
        pair("Triangle Color", "三角形颜色", "Triangle Color");
        pair("Triangles", "三角形", "Triangles");
        pair("Tridents", "三叉戟", "Tridents");
        pair("Triggers", "触发器", "Triggers");
        pair("Turbulence", "湍流", "Turbulence");
        pair("UsingItem", "使用物品", "UsingItem");
        pair("Wave Amount", "波动数量", "Wave Amount");
        pair("Wave Frequency", "波动频率", "Wave Frequency");
        pair("Width", "宽度", "Width");
        pair("Wind", "风", "Wind");
        pair("Wind Direction", "风向", "Wind Direction");

        // ── BlockESP 方块 ──
        pair("diamond ore", "钻石矿石", "diamond ore");
        pair("deepslate diamond ore", "深层钻石矿石", "deepslate diamond ore");
        pair("emerald ore", "绿宝石矿石", "emerald ore");
        pair("deepslate emerald ore", "深层绿宝石矿石", "deepslate emerald ore");
        pair("gold ore", "金矿石", "gold ore");
        pair("deepslate gold ore", "深层金矿石", "deepslate gold ore");
        pair("iron ore", "铁矿石", "iron ore");
        pair("deepslate iron ore", "深层铁矿石", "deepslate iron ore");
        pair("coal ore", "煤矿石", "coal ore");
        pair("deepslate coal ore", "深层煤矿石", "deepslate coal ore");
        pair("redstone ore", "红石矿石", "redstone ore");
        pair("deepslate redstone ore", "深层红石矿石", "deepslate redstone ore");
        pair("lapis ore", "青金石矿石", "lapis ore");
        pair("deepslate lapis ore", "深层青金石矿石", "deepslate lapis ore");
        pair("copper ore", "铜矿石", "copper ore");
        pair("deepslate copper ore", "深层铜矿石", "deepslate copper ore");
        pair("ancient debris", "远古残骸", "ancient debris");
        pair("nether quartz ore", "下界石英矿石", "nether quartz ore");
        pair("nether gold ore", "下界金矿石", "nether gold ore");
        pair("chest", "箱子", "chest");
        pair("trapped chest", "陷阱箱", "trapped chest");
        pair("ender chest", "末影箱", "ender chest");
        pair("spawner", "刷怪笼", "spawner");
        pair("furnace", "熔炉", "furnace");
        pair("barrel", "木桶", "barrel");
        pair("tnt", "TNT", "tnt");
        pair("bedrock", "基岩", "bedrock");

        // ── BlackHolePet 黑洞宠物 ──
        pair("Shoulder", "肩膀", "Shoulder");
        pair("Follow Speed", "跟随速度", "Follow Speed");
        pair("Swing", "摆动", "Swing");
        pair("Side Offset", "侧向距离", "Side Offset");
        pair("Forward Offset", "前后偏移", "Forward Offset");
        pair("Idle Orbit", "悬浮轨道", "Idle Orbit");
        pair("Orbit Speed", "轨道速度", "Orbit Speed");
        pair("Bob", "上下浮动", "Bob");
        pair("Disk Scale", "吸积盘尺度", "Disk Scale");
        pair("Disk Tilt", "盘面倾角", "Disk Tilt");
        pair("Spin Speed", "自转速度", "Spin Speed");
        pair("Doppler Beaming", "多普勒集束", "Doppler Beaming");
        pair("Grav Redshift", "引力红移", "Grav Redshift");
        pair("Filament Scale", "条纹尺度", "Filament Scale");
        pair("Accretion Disk", "吸积盘", "Accretion Disk");
        pair("Lensed Arc", "透镜弧", "Lensed Arc");
        pair("Relativistic Jets", "相对论喷流", "Relativistic Jets");
        pair("Jet Length", "喷流长度", "Jet Length");
        pair("Infall Motes", "落入尘埃", "Infall Motes");
        pair("Mote Count", "尘埃数量", "Mote Count");
        pair("Gravitational Lensing", "引力透镜", "Gravitational Lensing");
        pair("Lens Strength", "透镜强度", "Lens Strength");
        pair("Photon Ring", "光子环", "Photon Ring");
        pair("Frame Drag", "参考系拖曳", "Frame Drag");
        pair("Light Capture", "光线捕获", "Light Capture");
        pair("Gargantua", "卡冈图雅", "Gargantua");
        pair("Ice", "寒冰", "Ice");
        pair("Spectrum", "光谱", "Spectrum");

        // ── ListValue 选项 ──
        pair("2D", "2D", "2D");
        pair("3D", "3D", "3D");
        pair("Minimal", "极简", "Minimal");
        pair("Alphabetical", "按字母顺序", "Alphabetical");
        pair("Blade", "刀刃", "Blade");
        pair("Category", "按分类", "Category");
        pair("Cyber", "赛博", "Cyber");
        pair("Dashed", "虚线", "Dashed");
        pair("EaseInOutCubic", "立方缓入缓出", "EaseInOutCubic");
        pair("Elastic", "弹性", "Elastic");
        pair("Expanded", "展开", "Expanded");
        pair("Fade", "渐隐", "Fade");
        pair("Fishing Rod", "钓鱼竿", "Fishing Rod");
        pair("Frost", "冰霜", "Frost");
        pair("GodBridge", "上帝搭路", "GodBridge");
        pair("Horizontal", "水平", "Horizontal");
        pair("Hotbar", "快捷栏", "Hotbar");
        pair("hurtTime", "受击时间", "hurtTime");
        pair("Inferno", "地狱火", "Inferno");
        pair("InvSwitch", "背包切换", "InvSwitch");
        pair("Left", "左", "Left");
        pair("Length", "按长度", "Length");
        pair("Lower", "小写", "Lower");
        pair("Material", "材质", "Material");
        pair("Mellow", "柔和", "Mellow");
        pair("Cards", "卡片", "Cards");
        pair("Adaptive", "自适应", "Adaptive");
        pair("Additive", "叠加", "Additive");
        pair("All", "全部", "All");
        pair("Angle", "角度", "Angle");
        pair("Arcane", "奥术", "Arcane");
        pair("Aurora", "极光", "Aurora");
        pair("Axe", "斧", "Axe");
        pair("Bezier", "贝塞尔", "Bezier");
        pair("Billboard", "公告板", "Billboard");
        pair("Block", "方块", "Block");
        pair("Body", "身体", "Body");
        pair("Both", "两者", "Both");
        pair("Bow", "弓", "Bow");
        pair("Box", "框体", "Box");
        pair("Celestial", "天象", "Celestial");
        pair("Chest", "箱子", "Chest");
        pair("Classic", "经典", "Classic");
        pair("Comet", "彗星", "Comet");
        pair("Crescent", "新月", "Crescent");
        pair("Crossbow", "弩", "Crossbow");
        pair("Current", "当前", "Current");
        pair("Custom", "自定义", "Custom");
        pair("Direct", "直接", "Direct");
        pair("Distance", "距离", "Distance");
        pair("Dream Garden", "梦幻花园", "Dream Garden");
        pair("Ease In Out Cubic", "立方缓入缓出", "Ease In Out Cubic");
        pair("Ease Out", "缓出", "Ease Out");
        pair("EaseOutCubic", "立方缓出", "EaseOutCubic");
        pair("EaseOutExpo", "指数缓出", "EaseOutExpo");
        pair("Eased", "缓动", "Eased");
        pair("Ender Pearl", "末影珍珠", "Ender Pearl");
        pair("Entity", "实体", "Entity");
        pair("Exponential", "指数", "Exponential");
        pair("Extravagant", "奢华", "Extravagant");
        pair("Festival", "节日", "Festival");
        pair("Fill", "填充", "Fill");
        pair("Fire Charge", "火焰弹", "Fire Charge");
        pair("Gradient", "渐变", "Gradient");
        pair("Golden Apple", "金苹果", "Golden Apple");
        pair("Head", "头部", "Head");
        pair("Health", "生命", "Health");
        pair("Hell Hand", "地狱之手", "Hell Hand");
        pair("Heypixel", "空岛", "Heypixel");
        pair("High", "高", "High");
        pair("Hypernova", "极超新星", "Hypernova");
        pair("Inventory", "背包", "Inventory");
        pair("Jump", "跳跃", "Jump");
        pair("Legs", "腿部", "Legs");
        pair("Linear", "线性", "Linear");
        pair("Low", "低", "Low");
        pair("Material3", "Material3", "Material3");
        pair("Matrix", "矩阵", "Matrix");
        pair("Medium", "中", "Medium");
        pair("None", "无", "None");
        pair("MiSans", "MiSans", "MiSans");
        pair("NoFall00", "无摔落", "NoFall00");
        pair("Normal", "普通", "Normal");
        pair("Occluded", "被遮挡", "Occluded");
        pair("Outline", "描边", "Outline");
        pair("Packet", "数据包", "Packet");
        pair("Perlin", "柏林", "Perlin");
        pair("Packet2", "数据包2", "Packet2");
        pair("Pickaxe", "镐", "Pickaxe");
        pair("Pillars", "光柱数量", "Pillars");
        pair("Rainbow", "彩虹", "Rainbow");
        pair("Plasma", "等离子", "Plasma");
        pair("Power Bow", "力量弓", "Power Bow");
        pair("Prism", "棱镜", "Prism");
        pair("Prismatic", "棱镜", "Prismatic");
        pair("Projectile", "投射物", "Projectile");
        pair("Punch Bow", "击退弓", "Punch Bow");
        pair("Random", "随机", "Random");
        pair("Ring", "圆环", "Ring");
        pair("Reticle", "准星", "Reticle");
        pair("Right", "右", "Right");
        pair("Sakura", "樱花", "Sakura");
        pair("Runic", "符文", "Runic");
        pair("Seraphic", "炽天使", "Seraphic");
        pair("Smooth", "平滑", "Smooth");
        pair("Shatter", "碎裂", "Shatter");
        pair("Silent", "静默", "Silent");
        pair("SkyRainbow", "天空彩虹", "SkyRainbow");
        pair("Slowly", "缓慢", "Slowly");
        pair("Smoothstep", "平滑步进", "Smoothstep");
        pair("Sword", "剑", "Sword");
        pair("Soft", "柔和", "Soft");
        pair("Solid", "实线", "Solid");
        pair("Special", "特殊", "Special");
        pair("Static", "静态", "Static");
        pair("TellyBridge", "Telly搭路", "TellyBridge");
        pair("Sync", "同步", "Sync");
        pair("Thaumaturgy Strike", "奇术打击", "Thaumaturgy Strike");
        pair("Vanilla", "原版", "Vanilla");
        pair("Top", "上", "Top");
        pair("Ultra", "终极", "Ultra");
        pair("Upper", "大写", "Upper");
        pair("Warm Festival", "温暖节日", "Warm Festival");
        pair("Vertical", "垂直", "Vertical");
        pair("Void", "虚空", "Void");
        pair("Water Bucket", "水桶", "Water Bucket");
        pair("Wireframe", "线框", "Wireframe");
        pair("Wave", "波浪", "Wave");

        // ── 主菜单 ──
        pair("Singleplayer", "单人游戏", "Singleplayer");
        pair("Multiplayer", "多人游戏", "Multiplayer");
        pair("Settings", "设置", "Settings");
        pair("Alt Manager", "账号管理", "Alt Manager");
        pair("Exit", "退出游戏", "Exit");
        pair("Modern Minecraft Client", "现代 Minecraft 客户端", "Modern Minecraft Client");
        pair("Gemini Client", "Gemini 客户端", "Gemini Client");
        pair("Github", "Github", "Github");
        pair("Discord", "Discord", "Discord");
        pair("↑↓  Select    Enter  Open", "↑↓ 选择    Enter 打开", "↑↓  Select    Enter  Open");

        // ── MD3 ClickGui ──
        pair("CONTROL CENTER", "控制中心", "CONTROL CENTER");
        pair("CLIENT HUD", "客户端 HUD", "CLIENT HUD");
        pair("Matching modules", "匹配的模块", "Matching modules");
        pair("Modules", "模块", "Modules");
        pair("No modules found", "未找到模块", "No modules found");
        pair("No favorites yet", "暂无收藏", "No favorites yet");
        pair("Try a shorter or different module name", "尝试输入更短或不同的模块名", "Try a shorter or different module name");
        pair("Select the heart on a module to pin it here", "点击模块上的爱心即可固定到这里", "Select the heart on a module to pin it here");
        pair("Favorites", "收藏", "Favorites");
        pair("Search results", "搜索结果", "Search results");
        pair("Search modules", "搜索模块", "Search modules");
        pair("BROWSE", "浏览", "BROWSE");
        pair("Choose color", "选择颜色", "Choose color");
        pair("Done", "完成", "Done");
        pair("Hue", "色相", "Hue");
        pair("Material colors", "材质色板", "Material colors");
        pair("Opacity", "透明度", "Opacity");
        pair("shown", "个显示", " shown");
        pair("module", "个模块", " module");
        pair("modules", "个模块", " modules");
        pair("active", "个启用", " active");
        pair("setting", "项设置", " setting");
        pair("settings", "项设置", " settings");

        // ── 按键绑定 ──
        pair("Keybind", "按键绑定", "Keybind");
        pair("Press a key...", "按下按键...", "Press a key...");

        // ── 模块描述 ──
        pair("Automatically attacks nearby entities", "自动攻击附近的实体", "Automatically attacks nearby entities");
        pair("Modifies knockback taken", "修改受到的击退", "Modifies knockback taken");
        pair("Throws projectiles automatically", "自动投掷投射物", "Throws projectiles automatically");
        pair("Opens this configuration screen", "打开此配置界面", "Opens this configuration screen");
        pair("Shows information about your target", "显示目标的信息", "Shows information about your target");
        pair("Shows module toggle notifications", "显示模块开关通知", "Shows module toggle notifications");
        pair("Custom sweep attack visual effects", "自定义横扫攻击视觉特效", "Custom sweep attack visual effects");
        pair("Plays a cinematic effect on every kill", "每次击杀播放电影级特效（极超新星/地狱之手/奇术打击）", "Plays a cinematic effect on every kill");
        pair("Makes held items glow along their edges", "让手持物品的边缘发光", "Makes held items glow along their edges");
        pair("A black hole that rides your shoulder", "停在你肩上的黑洞宠物", "A black hole that rides your shoulder");

        // ── 通知 ──
        pair("Module", "模块", "Module");
        pair("Enabled: ", "已启用：", "Enabled: ");
        pair("Disabled: ", "已禁用：", "Disabled: ");

        // ── 背景选择器 ──
        pair("BackGround", "背景", "BackGround");
        pair("Background Selector", "背景选择器", "Background Selector");
        pair("Add BackGround..", "添加背景..", "Add BackGround..");
        pair("Animated", "动态", "Animated");
        pair("Select Wallpaper", "选择壁纸", "Select Wallpaper");
        pair("启用壁纸", "启用壁纸", "Enable Wallpaper");
        pair("关闭壁纸", "关闭壁纸", "Disable Wallpaper");
        pair("拖入文件可添加壁纸", "拖入文件可添加壁纸", "Drop image files here to add");

        // ── AltManager ──
        pair("Alt Manager Title", "账号管理", "Alt Manager");
        pair("AltManagerSubtitle", "共 %d 个账号    ·    当前会话：%s",
                "%d accounts    ·    Current session: %s");
        pair("使用中", "使用中", "In use");
        pair("上次使用", "上次使用", "Last used");
        pair("列表为空", "列表为空", "List is empty");
        pair("使用下方链接添加 Microsoft 或离线账号", "使用下方链接添加 Microsoft 或离线账号",
                "Use the links below to add a Microsoft or offline account");
        pair("点击右上角按钮添加 Microsoft 或离线账号", "点击右上角按钮添加 Microsoft 或离线账号",
                "Use the buttons in the top-right to add a Microsoft or offline account");
        pair("未选择账号", "未选择账号", "No account selected");
        pair("未使用", "未使用", "Not in use");
        pair("状态", "状态", "Status");
        pair("+ Microsoft 账号", "+ Microsoft 账号", "+ Microsoft account");
        pair("+ 离线账号", "+ 离线账号", "+ Offline account");
        pair("应用", "应用", "Apply");
        pair("删除", "删除", "Delete");
        pair("添加", "添加", "Add");
        pair("Microsoft 登录", "Microsoft 登录", "Microsoft Login");
        pair("添加离线账号", "添加离线账号", "Add Offline Account");
        pair("删除账号", "删除账号", "Delete Account");
        pair("未知", "未知", "Unknown");
        pair("Microsoft", "Microsoft", "Microsoft");
        pair("离线", "离线", "Offline");
        pair("↑↓  选择    Enter  应用    Delete  删除    Esc  返回",
                "↑↓  选择    Enter  应用    Delete  删除    Esc  返回",
                "↑↓  Select    Enter  Apply    Delete  Remove    Esc  Back");
        pair("正在应用账号，请稍候…", "正在应用账号，请稍候…", "Applying account, please wait…");
        pair("已应用离线账号：%s", "已应用离线账号：%s", "Applied offline account: %s");
        pair("已应用 Microsoft 账号：%s（本地令牌）", "已应用 Microsoft 账号：%s（本地令牌）",
                "Applied Microsoft account: %s (local token)");
        pair("正在刷新 %s 的令牌…", "正在刷新 %s 的令牌…", "Refreshing token for %s…");
        pair("已应用 Microsoft 账号：%s（令牌已刷新）", "已应用 Microsoft 账号：%s（令牌已刷新）",
                "Applied Microsoft account: %s (token refreshed)");
        pair("刷新被中断", "刷新被中断", "Refresh interrupted");
        pair("刷新失败（%s），已使用本地令牌应用", "刷新失败（%s），已使用本地令牌应用",
                "Refresh failed (%s), applied local token instead");
        pair("刷新失败：%s", "刷新失败：%s", "Refresh failed: %s");
        pair("已更新 Microsoft 账号：%s", "已更新 Microsoft 账号：%s", "Updated Microsoft account: %s");
        pair("已添加 Microsoft 账号：%s", "已添加 Microsoft 账号：%s", "Added Microsoft account: %s");
        pair("已添加离线账号：%s", "已添加离线账号：%s", "Added offline account: %s");
        pair("粘贴重定向 URL 或授权码…", "粘贴重定向 URL 或授权码…", "Paste the redirect URL or authorization code…");
        pair("自动模式：打开浏览器并在本地接收登录回调（推荐）。",
                "自动模式：打开浏览器并在本地接收登录回调（推荐）。",
                "Auto mode: opens your browser and receives the login callback locally (recommended).");
        pair("手动模式：自行完成登录后粘贴浏览器重定向链接。",
                "手动模式：自行完成登录后粘贴浏览器重定向链接。",
                "Manual mode: complete the login yourself, then paste the browser redirect link.");
        pair("自动登录（推荐）", "自动登录（推荐）", "Auto login (recommended)");
        pair("手动粘贴链接", "手动粘贴链接", "Paste link manually");
        pair("等待浏览器回调", "等待浏览器回调", "Waiting for browser callback");
        pair("剩余 %d:%02d", "剩余 %d:%02d", "%d:%02d remaining");
        pair("页面没有打开？可以改用手动模式自行复制链接。",
                "页面没有打开？可以改用手动模式自行复制链接。",
                "Page didn't open? You can copy the link manually in manual mode.");
        pair("在浏览器中完成登录后，地址栏会跳转到 localhost 链接",
                "在浏览器中完成登录后，地址栏会跳转到 localhost 链接",
                "After logging in, the browser's address bar will jump to a localhost link");
        pair("（页面可能无法打开）——复制完整链接粘贴到下方：",
                "（页面可能无法打开）——复制完整链接粘贴到下方：",
                "(the page may not open) — copy the full link and paste it below:");
        pair("正在与微软服务器通信", "正在与微软服务器通信", "Contacting Microsoft servers");
        pair("请稍候，正在完成令牌交换…", "请稍候，正在完成令牌交换…", "Please wait, exchanging tokens…");
        pair("取消", "取消", "Cancel");
        pair("返回", "返回", "Back");
        pair("关闭", "关闭", "Close");
        pair("改用手动模式", "改用手动模式", "Switch to manual mode");
        pair("打开授权页面", "打开授权页面", "Open authorization page");
        pair("开始登录", "开始登录", "Start login");
        pair("用户名（3-16 位字母、数字、下划线）", "用户名（3-16 位字母、数字、下划线）",
                "Username (3-16 letters, digits, underscores)");
        pair("请输入用户名", "请输入用户名", "Please enter a username");
        pair("用户名至少 3 个字符", "用户名至少 3 个字符", "Username must be at least 3 characters");
        pair("已存在同名账号", "已存在同名账号", "An account with this name already exists");
        pair("离线账号无需联网验证，仅适用于离线模式服务器。",
                "离线账号无需联网验证，仅适用于离线模式服务器。",
                "Offline accounts need no online verification and only work on offline-mode servers.");
        pair("已删除账号：%s", "已删除账号：%s", "Deleted account: %s");
        pair("确定删除账号 %s（%s）？", "确定删除账号 %s（%s）？", "Delete account %s (%s)?");
        pair("此操作不可撤销。", "此操作不可撤销。", "This action cannot be undone.");
        pair("未知错误", "未知错误", "Unknown error");
        pair("未知错误（%s）", "未知错误（%s）", "Unknown error (%s)");

        // ── OSU4k ──
        pair("OSU4k", "OSU4k", "OSU4k");
        pair("Offset", "判定偏移", "Offset");
        pair("Volume", "音量", "Volume");
        pair("Scroll Speed", "滚动速度", "Scroll Speed");
        pair("Debug", "调试", "Debug");
        pair("Hit Effects", "打击特效", "Hit Effects");
        pair("Hit Effects Off", "打击特效：关", "Hit Effects Off");
        pair("Lane Highlight", "轨道高亮", "Lane Highlight");
        pair("Judgement", "判定范围", "Judgement");
        pair("Lane Colors", "音轨颜色", "Lane Colors");
        pair("Default", "默认", "Default");
        pair("Same", "同色", "Same");
        pair("Very Lenient", "非常宽松", "Very Lenient");
        pair("Lenient", "宽松", "Lenient");
        pair("Moderate", "中等", "Moderate");
        pair("Strict", "严格", "Strict");
        pair("Very Strict", "非常严格", "Very Strict");
        pair("Perfect", "完美", "Perfect");
        pair("Great", "优秀", "Great");
        pair("Good", "良好", "Good");
        pair("Miss", "未命中", "Miss");
        pair("Get Ready", "准备", "Get Ready");
        pair("Go!", "开始!", "Go!");
        pair("Clear!", "完成!", "Clear!");
        pair("Results", "结算", "Results");
        pair("Retry", "重试", "Retry");
        pair("Accuracy", "准确率", "Accuracy");
        pair("Max Combo", "最大连击", "Max Combo");
        pair("Full Combo", "全连", "Full Combo");
        pair("Score", "得分", "Score");
        pair("Song Time", "歌曲时长", "Song Time");
        pair("max", "最大", "max");
        pair("Mania Rhythm Game", "Mania 节奏游戏", "Mania Rhythm Game");
        pair("Select .osz beatmap", "选择 .osz 谱面", "Select .osz beatmap");
        pair("Open .osz", "打开 .osz", "Open .osz");
        pair("Library", "谱面库", "Library");
        pair("Custom Keybinds", "自定义按键", "Custom Keybinds");
        pair("DIFFICULTIES", "难度", "DIFFICULTIES");
        pair("+ %d more difficulties", "还有 %d 个难度", "+ %d more difficulties");
        pair("Click \"Open .osz\" to load a beatmap set", "点击“打开 .osz”载入谱面集", "Click \"Open .osz\" to load a beatmap set");
        pair("Loaded %d difficulties", "已载入 %d 个难度", "Loaded %d difficulties");
        pair("%d notes", "%d 个音符", "%d notes");
        pair("Esc: back", "Esc: 返回", "Esc: back");
        pair("Beatmap Library", "谱面库", "Beatmap Library");
        pair("no beatmaps yet", "暂无谱面", "no beatmaps yet");
        pair("1 beatmap set", "1 个谱面集", "1 beatmap set");
        pair("%d beatmap sets", "%d 个谱面集", "%d beatmap sets");
        pair("Add .osz", "添加 .osz", "Add .osz");
        pair("Add .osz beatmaps", "添加 .osz 谱面", "Add .osz beatmaps");
        pair("Clear All", "清空", "Clear All");
        pair("Back", "返回", "Back");
        pair("No beatmaps saved yet", "尚未保存谱面", "No beatmaps saved yet");
        pair("Click \"Add .osz\" or drop .osz files here", "点击“添加 .osz”或将 .osz 文件拖到此处", "Click \"Add .osz\" or drop .osz files here");
        pair("Drop .osz files here to add   •   Esc: back", "将 .osz 文件拖到此处添加   •   Esc: 返回", "Drop .osz files here to add   •   Esc: back");
        pair("Added 1 beatmap set", "已添加 1 个谱面集", "Added 1 beatmap set");
        pair("Added %d beatmap sets", "已添加 %d 个谱面集", "Added %d beatmap sets");
        pair("File not found: %s", "找不到文件：%s", "File not found: %s");
        pair("Removed \"%s\"", "已移除“%s”", "Removed \"%s\"");
        pair("Library cleared", "谱面库已清空", "Library cleared");
        pair("missing", "缺失", "missing");
        pair("loaded", "已载入", "loaded");
        pair("Column %d", "第 %d 列", "Column %d");
        pair("PRESS A KEY TO TEST IT LIVE", "按任意键实时测试", "PRESS A KEY TO TEST IT LIVE");
        pair("Keys", "按键", "Keys");
        pair("ExitGame", "退出", "Exit");
        pair("Pause", "暂停", "Pause");
        pair("Resume", "继续", "Resume");
        pair("Play", "播放", "Play");
        pair("Audio load failed: %s", "音频加载失败：%s", "Audio load failed: %s");
        pair("Failed to switch difficulty: %s", "切换难度失败：%s", "Failed to switch difficulty: %s");
        pair("Failed to read .osz: %s", "读取 .osz 失败：%s", "Failed to read .osz: %s");
        pair("Not a valid .osz archive: %s", "不是有效的 .osz 压缩包：%s", "Not a valid .osz archive: %s");
        pair("Cannot read .osz file: %s", "无法读取 .osz 文件：%s", "Cannot read .osz file: %s");
        pair("Unreadable entry: %s", "无法读取的条目：%s", "Unreadable entry: %s");
        pair("Map \"%s\" references missing audio: %s", "谱面“%s”引用的音频缺失：%s", "Map \"%s\" references missing audio: %s");
        pair("Failed to extract audio: %s", "音频提取失败：%s", "Failed to extract audio: %s");
        pair("No playable mania map in this .osz", "此 .osz 中没有可游玩的 mania 谱面", "No playable mania map in this .osz");
        pair("Map \"%s\" is missing its audio file: %s", "谱面“%s”缺少音频文件：%s", "Map \"%s\" is missing its audio file: %s");
        pair("audio file exceeds the %d MB limit", "音频文件超过 %d MB 上限", "audio file exceeds the %d MB limit");
        pair("audio file truncated (%d bytes declared, %d read)", "音频文件不完整（声明 %d 字节，实际读取 %d）", "audio file truncated (%d bytes declared, %d read)");
        pair("[%s] is a %dK map, only 1K-10K is supported", "[%s] 是 %dK 谱面，仅支持 1K-10K", "[%s] is a %dK map, only 1K-10K is supported");
        pair("[%s] has no AudioFilename", "[%s] 缺少 AudioFilename", "[%s] has no AudioFilename");
        pair("[%s] has no hit objects", "[%s] 没有音符", "[%s] has no hit objects");
        pair("[%s] is not an osu!mania beatmap (Mode=%d)", "[%s] 不是 osu!mania 谱面（Mode=%d）", "[%s] is not an osu!mania beatmap (Mode=%d)");
    }
}
