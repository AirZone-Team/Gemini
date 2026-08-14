package geminiclient.gemini.modules.impl.combat.killaura;

import geminiclient.gemini.values.ValueParent;
import geminiclient.gemini.values.impl.*;

/**
 * KillAura 全部设置项集中声明，供模块注册到 GUI/配置，以及各子系统实时读取。
 * 可见性 lambda 捕获 this 延迟求值，声明顺序不影响相互引用。
 */
public final class KillAuraSettings {
    public final BoolValue noCoolDown = new BoolValue("NoCoolDown", false);
    public final IntRangeValue cps = new IntRangeValue("CPS", 10, 18, 1, 20, () -> noCoolDown.enabled);
    public final IntValue hurtTime = new IntValue("HurtTime", 10, 1, 10);
    public final FloatValue range = new FloatValue("Range", 3.0f, 1.0f, 6.0f);
    public final FloatValue fov = new FloatValue("FOV", 180f, 30f, 360f);
    public final FloatValue rotationSpeed = new FloatValue("RotationSpeed", 180f, 30f, 360f);
    public final ListValue pro = new ListValue("Priority", "Distance", new String[]{
            "Distance", "Health", "hurtTime"
    });
    public final CheckboxValue targets = new CheckboxValue("Targets", new BoolValue[]{
            new BoolValue("AttackPlayers", true), new BoolValue("AttackMobs", true),
            new BoolValue("AttackAnimals", false), new BoolValue("AttackDead"),
            new BoolValue("AttackTeammates")
    });
    public final CheckboxValue stop = new CheckboxValue("StopWorking", new BoolValue[]{
            new BoolValue("UsingItem"),
            new BoolValue("OpeningScreen")
    });
    public final ListValue rotationMode = new ListValue("RotationMode", "Linear", new String[]{
            "Linear", "Exponential", "Smooth", "Bezier", "Adaptive", "Perlin"
    });
    // Smooth 模式人化瞄准（声明在 easingCurve/overshoot 之前，避免前向引用）
    public final ListValue smoothStyle = new ListValue("SmoothStyle", "Eased", new String[]{
            "Eased", "Direct"
    }, () -> rotationMode.is("Smooth"));
    public final IntValue aimLatency = new IntValue("AimLatency", 100, 0, 250,
            () -> rotationMode.is("Smooth"));
    public final FloatValue latencyVariance = new FloatValue("LatencyVariance", 30f, 0f, 60f,
            () -> rotationMode.is("Smooth") && aimLatency.getValue() > 0);
    public final FloatValue speedFluctuation = new FloatValue("SpeedFluctuation", 0.35f, 0f, 0.8f,
            () -> rotationMode.is("Smooth"));
    public final BoolValue randomEasing = new BoolValue("RandomEasing", true,
            () -> rotationMode.is("Smooth") && smoothStyle.is("Eased"));
    public final FloatValue microTremor = new FloatValue("MicroTremor", 0.025f, 0f, 0.08f,
            () -> rotationMode.is("Smooth"));
    public final FloatValue pulseStrength = new FloatValue("PulseStrength", 1.0f, 0f, 1f,
            () -> rotationMode.is("Smooth"));
    public final ListValue easingCurve = new ListValue("EasingCurve", "Smoothstep", new String[]{
            "Smoothstep", "EaseInOutCubic", "EaseOutCubic", "EaseOutExpo"
    }, () -> (rotationMode.is("Smooth") && smoothStyle.is("Eased")) || rotationMode.is("Bezier"));
    public final FloatValue overshoot = new FloatValue("Overshoot", 0f, 0f, 1.5f,
            () -> rotationMode.is("Smooth") && smoothStyle.is("Eased"));
    public final FloatValue bezierRandomness = new FloatValue("BezierRandomness", 0.12f, 0f, 0.35f,
            () -> rotationMode.is("Bezier"));
    public final FloatValue perlinFrequency = new FloatValue("PerlinFrequency", 1.0f, 0.2f, 4.0f,
            () -> rotationMode.is("Perlin"));
    public final FloatValue perlinAmplitude = new FloatValue("PerlinAmplitude", 0.35f, 0.05f, 0.9f,
            () -> rotationMode.is("Perlin"));
    public final IntValue perlinOctaves = new IntValue("PerlinOctaves", 3, 1, 5,
            () -> rotationMode.is("Perlin"));
    public final FloatValue perlinPersistence = new FloatValue("PerlinPersistence", 0.5f, 0.1f, 0.9f,
            () -> rotationMode.is("Perlin"));
    public final FloatValue perlinLacunarity = new FloatValue("PerlinLacunarity", 2.0f, 1.3f, 3.0f,
            () -> rotationMode.is("Perlin"));
    public final FloatValue rotationJitter = new FloatValue("RotationJitter", 0.15f, 0f, 3f);
    public final FloatValue distanceSpread = new FloatValue("DistanceSpread", 0.75f, 0f, 2f,
            () -> rotationJitter.getValue() > 0f);
    public final FloatValue speedNoise = new FloatValue("SpeedNoise", 0.35f, 0f, 1f);
    public final FloatValue noiseSmoothness = new FloatValue("NoiseSmoothness", 0.5f, 0.05f, 1f,
            () -> speedNoise.getValue() > 0f);
    public final FloatValue prediction = new FloatValue("Prediction", 1f, 0f, 5f);
    public final BoolValue silentRotate = new BoolValue("SilentRotate", true);
    public final BoolValue rayTrace = new BoolValue("RayTrace", false);
    public final BoolValue blockRayTrace = new BoolValue("BlockRayTrace", true,
            () -> rayTrace.enabled);
    public final ListValue aimMode = new ListValue("AimMode", "Head", new String[]{
            "Head", "Chest", "Body", "Legs", "Random"
    });
    public final FloatValue aimPointSmooth = new FloatValue("AimPointSmooth", 0.30f, 0.05f, 1f);
    public final IntValue randomAimInterval = new IntValue("RandomAimInterval", 10, 3, 40,
            () -> aimMode.is("Random"));
    public final BoolValue movementReaction = new BoolValue("MovementReaction", true);
    public final IntRangeValue reactionDelay = new IntRangeValue("ReactionDelay", 60, 110, 0, 300,
            () -> movementReaction.enabled);
    public final FloatValue repositionThreshold = new FloatValue("RepositionThreshold", 0.20f, 0.05f, 1.5f,
            () -> movementReaction.enabled);
    // Click And Rotation
    public final FloatValue settleWobble = new FloatValue("SettleWobble", 1.0f, 0f, 3f);
    public final FloatValue settleRange = new FloatValue("SettleRange", 10f, 2f, 30f,
            () -> settleWobble.getValue() > 0f);
    public final FloatValue doubleClickChance = new FloatValue("DoubleClickChance", 0.06f, 0f, 0.5f,
            () -> noCoolDown.enabled);
    public final FloatValue doubleClickGap = new FloatValue("DoubleClickGap", 55f, 10f, 200f,
            () -> noCoolDown.enabled && doubleClickChance.getValue() > 0f);
    public final FloatValue clickVariance = new FloatValue("ClickVariance", 0.12f, 0f, 0.5f,
            () -> noCoolDown.enabled);
    public final IntRangeValue pauseEvery = new IntRangeValue("PauseEvery", 12, 24, 4, 60,
            () -> noCoolDown.enabled);
    public final IntRangeValue pauseLength = new IntRangeValue("PauseLength", 100, 220, 0, 500,
            () -> noCoolDown.enabled);
    public final FloatValue adrenalineRange = new FloatValue("AdrenalineRange", 0.45f, 0.1f, 0.9f,
            () -> noCoolDown.enabled);
    public final FloatValue adrenalineBoost = new FloatValue("AdrenalineBoost", 0.12f, 0f, 0.5f,
            () -> noCoolDown.enabled);

    // Target visuals
    public final BoolValue targetIndicator = new BoolValue("TargetIndicator", true);
    public final ListValue indicatorStyle = new ListValue("IndicatorStyle", "Arcane Array",
            new String[]{"Arcane Array", "Energy Helix", "Health Ring"},
            () -> targetIndicator.enabled);
    public final BoolValue orbitingOrbsEffect = new BoolValue("Comet Vortex", true);
    public final BoolValue pulseSphereEffect = new BoolValue("Prismatic Cage");
    public final BoolValue runeCrownEffect = new BoolValue("Rune Crown", true);
    public final CheckboxValue targetEffects = new CheckboxValue("TargetEffects", new BoolValue[]{
            orbitingOrbsEffect, pulseSphereEffect, runeCrownEffect
    });
    public final ListValue indicatorTargets = new ListValue("IndicatorTargets", "Current",
            new String[]{"Current", "All"});
    public final ListValue indicatorColorMode = new ListValue("IndicatorColors", "Health",
            new String[]{"Health", "Custom", "Rainbow"});
    public final ColorValue indicatorPrimaryColor = new ColorValue("IndicatorPrimary", 0xFF64E8FF,
            () -> indicatorColorMode.is("Custom"));
    public final ColorValue indicatorSecondaryColor = new ColorValue("IndicatorSecondary", 0xFFC06CFF,
            () -> indicatorColorMode.is("Custom"));
    public final FloatValue indicatorRainbowSpeed = new FloatValue("IndicatorRainbowSpeed",
            0.15f, 0.0f, 2.0f, () -> indicatorColorMode.is("Rainbow"));
    public final IntValue indicatorParticleCount = new IntValue("IndicatorParticles", 24, 6, 64);
    public final FloatValue indicatorRadius = new FloatValue("IndicatorRadius", 1.25f, 0.5f, 2.5f);
    public final FloatValue indicatorParticleSize = new FloatValue("IndicatorParticleSize",
            0.065f, 0.015f, 0.20f);
    public final FloatValue indicatorOpacity = new FloatValue("IndicatorOpacity", 0.80f, 0.05f, 1.0f);
    public final FloatValue indicatorRotationSpeed = new FloatValue("IndicatorRotationSpeed",
            0.35f, -2.5f, 2.5f);
    public final FloatValue indicatorPulse = new FloatValue("IndicatorPulse", 0.16f, 0.0f, 1.0f);
    public final FloatValue indicatorPulseSpeed = new FloatValue("IndicatorPulseSpeed",
            1.4f, 0.0f, 5.0f);
    public final FloatValue indicatorYOffset = new FloatValue("IndicatorYOffset", 0.0f, -0.5f, 1.0f);
    public final FloatValue indicatorHelixHeight = new FloatValue("IndicatorHelixHeight",
            1.0f, 0.2f, 1.5f,
            () -> targetIndicator.enabled && indicatorStyle.is("Energy Helix"));
    public final FloatValue indicatorHelixTurns = new FloatValue("IndicatorHelixTurns",
            1.8f, 0.5f, 4.0f,
            () -> targetIndicator.enabled && indicatorStyle.is("Energy Helix"));
    public final BoolValue indicatorDoubleHelix = new BoolValue("IndicatorDoubleHelix",
            true, () -> targetIndicator.enabled && indicatorStyle.is("Energy Helix"));

    /** 按模块原先 addValue(...) 的注册顺序返回，保证 GUI/配置里的值顺序不变。 */
    public ValueParent[] allValues() {
        return new ValueParent[]{
                noCoolDown, cps, range, fov, hurtTime, pro,
                targets, stop, rotationSpeed, rotationMode,
                smoothStyle, aimLatency, latencyVariance,
                speedFluctuation, randomEasing, microTremor, pulseStrength,
                easingCurve, overshoot, bezierRandomness, perlinFrequency, perlinAmplitude,
                perlinOctaves, perlinPersistence, perlinLacunarity,
                rotationJitter, distanceSpread,
                speedNoise, noiseSmoothness, prediction,
                silentRotate, rayTrace, blockRayTrace, aimMode, aimPointSmooth, randomAimInterval,
                movementReaction, reactionDelay, repositionThreshold,
                settleWobble, settleRange,
                doubleClickChance, doubleClickGap, clickVariance,
                pauseEvery, pauseLength, adrenalineRange, adrenalineBoost,
                targetIndicator, indicatorStyle, targetEffects,
                indicatorTargets, indicatorColorMode,
                indicatorPrimaryColor, indicatorSecondaryColor, indicatorRainbowSpeed,
                indicatorParticleCount, indicatorRadius, indicatorParticleSize,
                indicatorOpacity, indicatorRotationSpeed, indicatorPulse,
                indicatorPulseSpeed, indicatorYOffset,
                indicatorHelixHeight, indicatorHelixTurns, indicatorDoubleHelix
        };
    }
}
