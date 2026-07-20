package geminiclient.gemini.modules.impl.visual;

import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer;
import geminiclient.gemini.customRenderer.glsl.modules.SkyLanternRenderer.FrameCtx;
import geminiclient.gemini.event.annotations.EventTarget;
import geminiclient.gemini.event.events.impl.Render3DEvent;
import geminiclient.gemini.event.events.impl.UpdateEvent;
import geminiclient.gemini.modules.Module;
import geminiclient.gemini.modules.ModuleEnum;
import geminiclient.gemini.values.impl.BoolValue;
import geminiclient.gemini.values.impl.ColorValue;
import geminiclient.gemini.values.impl.FloatValue;
import geminiclient.gemini.values.impl.IntValue;
import geminiclient.gemini.values.impl.ListValue;
import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * A configurable airborne festival containing lanterns, spirit koi, origami
 * cranes, moon butterflies and shooting stars.
 *
 * <p>All objects use primitive-array pools and batched GPU draws. Even the
 * "Extravagant" quality preset does not allocate per particle every frame.</p>
 */
public class SkyLantern extends Module {

    // Scene composition
    private final ListValue scene = new ListValue("Sky Mix", "Festival", new String[]{
            "Festival", "Celestial", "Dream Garden", "All", "Custom"
    });
    private final ListValue palette = new ListValue("Palette", "Warm Festival", new String[]{
            "Warm Festival", "Sakura", "Aurora", "Celestial", "Rainbow", "Custom"
    });
    private final ListValue quality = new ListValue("Quality", "High", new String[]{
            "Low", "Medium", "High", "Extravagant"
    });

    private final BoolValue lanterns = new BoolValue("Lanterns", true, () -> scene.is("Custom"));
    private final BoolValue spiritKoi = new BoolValue("Spirit Koi", true, () -> scene.is("Custom"));
    private final BoolValue paperCranes = new BoolValue("Paper Cranes", true, () -> scene.is("Custom"));
    private final BoolValue moonButterflies = new BoolValue("Moon Butterflies", true, () -> scene.is("Custom"));
    private final BoolValue shootingStars = new BoolValue("Shooting Stars", true, () -> scene.is("Custom"));

    // Population and placement
    private final IntValue density = new IntValue("Density", 32, 4, 96);
    private final FloatValue spawnDelay = new FloatValue("Spawn Delay", 0.22f, 0.04f, 2.0f);
    private final FloatValue lifetime = new FloatValue("Lifetime", 15000f, 3000f, 45000f);
    private final FloatValue spawnRadius = new FloatValue("Spawn Radius", 18f, 4f, 48f);
    private final FloatValue minHeight = new FloatValue("Min Height", 2.5f, -2f, 24f);
    private final FloatValue maxHeight = new FloatValue("Max Height", 14f, 3f, 48f);
    private final FloatValue renderDistance = new FloatValue("Render Distance", 64f, 16f, 144f);

    // Motion
    private final FloatValue flightSpeed = new FloatValue("Flight Speed", 0.72f, 0.05f, 3.0f);
    private final FloatValue animationSpeed = new FloatValue("Animation", 1.0f, 0.1f, 3.0f);
    private final FloatValue drift = new FloatValue("Drift", 0.65f, 0f, 2.5f);
    private final FloatValue windStrength = new FloatValue("Wind", 0.32f, 0f, 1.5f);
    private final FloatValue windDirection = new FloatValue("Wind Direction", 35f, 0f, 360f);

    // Shape and lighting
    private final FloatValue objectSize = new FloatValue("Size", 0.72f, 0.18f, 2.2f);
    private final FloatValue sizeVariation = new FloatValue("Size Variation", 0.35f, 0f, 0.8f);
    private final FloatValue opacity = new FloatValue("Opacity", 0.92f, 0.1f, 1f);
    private final FloatValue glowPower = new FloatValue("Glow", 1.15f, 0f, 2.5f);
    private final FloatValue flamePower = new FloatValue("Flame Power", 0.95f, 0.2f, 1.8f);
    private final ColorValue primaryColor = new ColorValue("Primary Color", 0xFFFF9A3D,
            () -> palette.is("Custom"));
    private final ColorValue secondaryColor = new ColorValue("Secondary Color", 0xFFFF4F81,
            () -> palette.is("Custom"));
    private final ColorValue accentColor = new ColorValue("Accent Color", 0xFFFFE7A3,
            () -> palette.is("Custom"));
    private final BoolValue colorVariation = new BoolValue("Color Variation", true);

    // Atmospheric layers
    private final BoolValue showEmbers = new BoolValue("Lantern Embers", true);
    private final BoolValue showTrails = new BoolValue("Light Trails", true);
    private final BoolValue showStardust = new BoolValue("Stardust", true);
    private final BoolValue halos = new BoolValue("Soft Halos", true);

    private static final int MAX_OBJECTS = 96;
    private static final int MAX_PARTICLES = 768;

    private final float[] ox = new float[MAX_OBJECTS];
    private final float[] oy = new float[MAX_OBJECTS];
    private final float[] oz = new float[MAX_OBJECTS];
    private final float[] opx = new float[MAX_OBJECTS];
    private final float[] opy = new float[MAX_OBJECTS];
    private final float[] opz = new float[MAX_OBJECTS];
    private final float[] ovx = new float[MAX_OBJECTS];
    private final float[] ovy = new float[MAX_OBJECTS];
    private final float[] ovz = new float[MAX_OBJECTS];
    private final float[] ophase = new float[MAX_OBJECTS];
    private final float[] oscale = new float[MAX_OBJECTS];
    private final float[] oyaw = new float[MAX_OBJECTS];
    private final float[] oseed = new float[MAX_OBJECTS];
    private final long[] ostart = new long[MAX_OBJECTS];
    private final byte[] otype = new byte[MAX_OBJECTS];
    private final boolean[] oalive = new boolean[MAX_OBJECTS];
    private int objectCount;

    private final float[] particleX = new float[MAX_PARTICLES];
    private final float[] particleY = new float[MAX_PARTICLES];
    private final float[] particleZ = new float[MAX_PARTICLES];
    private final float[] particleVX = new float[MAX_PARTICLES];
    private final float[] particleVY = new float[MAX_PARTICLES];
    private final float[] particleVZ = new float[MAX_PARTICLES];
    private final float[] particleR = new float[MAX_PARTICLES];
    private final float[] particleG = new float[MAX_PARTICLES];
    private final float[] particleB = new float[MAX_PARTICLES];
    private final float[] particleSize = new float[MAX_PARTICLES];
    private final long[] particleTime = new long[MAX_PARTICLES];
    private final int[] particleLife = new int[MAX_PARTICLES];
    private final boolean[] particleAlive = new boolean[MAX_PARTICLES];
    private int particleHead;

    // x,y,z,size,r,g,b,alpha
    private final float[] lanternBatch = new float[MAX_OBJECTS * 8];
    private final float[] innerBatch = new float[MAX_OBJECTS * 8];
    private final float[] outerBatch = new float[MAX_OBJECTS * 8];
    private final float[] glowBatch = new float[MAX_OBJECTS * 8];
    private final float[] particleBatch = new float[MAX_PARTICLES * 8];
    // x,y,z,size,yaw,roll,r,g,b,alpha,type,phase
    private final float[] flyerBatch = new float[MAX_OBJECTS * 12];

    private int tickCounter;

    public SkyLantern() {
        super("SkyLantern", ModuleEnum.Visual);
        addValue(
                scene, palette, quality,
                lanterns, spiritKoi, paperCranes, moonButterflies, shootingStars,
                density, spawnDelay, lifetime, spawnRadius, minHeight, maxHeight, renderDistance,
                flightSpeed, animationSpeed, drift, windStrength, windDirection,
                objectSize, sizeVariation, opacity, glowPower, flamePower,
                primaryColor, secondaryColor, accentColor, colorVariation,
                showEmbers, showTrails, showStardust, halos
        );
    }

    @Override
    public void onEnabled() {
        tickCounter = 10000; // Populate immediately on the first update.
    }

    @Override
    public void onDisabled() {
        Arrays.fill(oalive, false);
        Arrays.fill(particleAlive, false);
        objectCount = 0;
        particleHead = 0;
        tickCounter = 0;
    }

    @EventTarget
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.tickCount <= 1) {
            onDisabled();
            return;
        }

        long now = System.currentTimeMillis();
        float baseLife = lifetime.getValue();
        float speed = flightSpeed.getValue() / 20f;
        float anim = animationSpeed.getValue();
        float windAngle = (float) Math.toRadians(windDirection.getValue());
        float windX = (float) Math.cos(windAngle) * windStrength.getValue() / 20f;
        float windZ = (float) Math.sin(windAngle) * windStrength.getValue() / 20f;
        int target = Math.min(MAX_OBJECTS, density.getValue());

        for (int i = 0; i < objectCount; i++) {
            if (!oalive[i]) continue;
            if (i >= target || !typeEnabled(otype[i])) {
                oalive[i] = false;
                continue;
            }
            float life = (now - ostart[i]) / objectLifetime(i, baseLife);
            if (life >= 1f) {
                oalive[i] = false;
                continue;
            }

            opx[i] = ox[i];
            opy[i] = oy[i];
            opz[i] = oz[i];
            ophase[i] += (0.055f + oseed[i] * 0.025f) * anim;
            float wave = (float) Math.sin(ophase[i]);
            float cross = (float) Math.cos(ophase[i] * 0.73f + oseed[i] * 4f);

            switch (otype[i]) {
                case SkyLanternRenderer.TYPE_LANTERN -> {
                    oy[i] += speed * 0.72f;
                    ox[i] += windX + wave * drift.getValue() * 0.006f;
                    oz[i] += windZ + cross * drift.getValue() * 0.006f;
                    oyaw[i] += wave * 0.004f;
                    if (showEmbers.enabled && tickCounter % particleStep() == 0) {
                        spawnParticle(i, 0.8f, true, now);
                    }
                }
                case SkyLanternRenderer.TYPE_KOI -> {
                    ox[i] += ovx[i] + windX * 0.35f;
                    oz[i] += ovz[i] + windZ * 0.35f;
                    oy[i] += wave * drift.getValue() * 0.009f + ovy[i];
                    oyaw[i] = (float) Math.atan2(ovx[i], ovz[i]);
                    if (showTrails.enabled && tickCounter % particleStep() == 0) {
                        spawnParticle(i, 1.2f, false, now);
                    }
                }
                case SkyLanternRenderer.TYPE_CRANE -> {
                    ox[i] += ovx[i] + windX;
                    oz[i] += ovz[i] + windZ;
                    oy[i] += wave * drift.getValue() * 0.005f + ovy[i];
                    oyaw[i] = (float) Math.atan2(ovx[i] + windX, ovz[i] + windZ);
                    if (showStardust.enabled && tickCounter % (particleStep() * 2) == 0) {
                        spawnParticle(i, 0.65f, false, now);
                    }
                }
                case SkyLanternRenderer.TYPE_BUTTERFLY -> {
                    ox[i] += ovx[i] + cross * 0.008f * drift.getValue() + windX * 0.45f;
                    oz[i] += ovz[i] + wave * 0.008f * drift.getValue() + windZ * 0.45f;
                    oy[i] += (wave + cross * 0.5f) * 0.012f * drift.getValue();
                    oyaw[i] = (float) Math.atan2(ovx[i] + cross * 0.008f, ovz[i] + wave * 0.008f);
                    if (showStardust.enabled && tickCounter % particleStep() == 0) {
                        spawnParticle(i, 0.75f, false, now);
                    }
                }
                case SkyLanternRenderer.TYPE_STAR -> {
                    ox[i] += ovx[i] * 3.2f + windX;
                    oy[i] += ovy[i] * 3.2f;
                    oz[i] += ovz[i] * 3.2f + windZ;
                    oyaw[i] = (float) Math.atan2(ovx[i], ovz[i]);
                    if (showTrails.enabled) {
                        spawnParticle(i, 1.8f, false, now);
                        if (quality.is("Extravagant")) spawnParticle(i, 1.25f, false, now);
                    }
                }
            }
        }

        updateParticles(now);
        compactObjects();

        tickCounter++;
        int interval = Math.max(1, (int) (spawnDelay.getValue() * 20f));
        int burst = objectCount == 0 ? Math.min(target, 10) : 1;
        if (objectCount < target && (tickCounter % interval == 0 || objectCount == 0)) {
            for (int i = 0; i < burst && objectCount < target; i++) spawnObject(now);
        }
    }

    @EventTarget
    public void onRender3D(Render3DEvent event) {
        if (objectCount == 0 || mc.player == null) return;

        long now = System.currentTimeMillis();
        float partialTick = event.partialTick();
        float baseLife = lifetime.getValue();
        float globalAlpha = opacity.getValue();
        float glow = glowPower.getValue();
        float fp = flamePower.getValue();
        float maxDist = renderDistance.getValue();
        FrameCtx ctx = new FrameCtx();

        int lanternN = 0;
        int flyerN = 0;
        int innerN = 0;
        int outerN = 0;
        int glowN = 0;

        for (int i = 0; i < objectCount; i++) {
            if (!oalive[i]) continue;
            float life = (now - ostart[i]) / objectLifetime(i, baseLife);
            float alpha = computeAlpha(life) * globalAlpha;
            if (alpha <= 0.001f) continue;

            float x = Mth.lerp(partialTick, opx[i], ox[i]);
            float y = Mth.lerp(partialTick, opy[i], oy[i]);
            float z = Mth.lerp(partialTick, opz[i], oz[i]);
            float dx = x - ctx.camX;
            float dy = y - ctx.camY;
            float dz = z - ctx.camZ;
            float distSq = dx * dx + dy * dy + dz * dz;
            if (distSq > maxDist * maxDist) continue;
            float distance = (float) Math.sqrt(distSq);
            float nearFade = Mth.clamp((distance - 1.25f) / 2.75f, 0f, 1f);
            alpha *= nearFade;
            if (alpha <= 0.001f) continue;

            float size = objectSize.getValue() * oscale[i];
            float[] color = objectColor(i, now);

            if (otype[i] == SkyLanternRenderer.TYPE_LANTERN) {
                int o = lanternN++ * 8;
                put8(lanternBatch, o, x, y, z, size, color[0], color[1], color[2], alpha);

                float flameY = y - 0.62f * size;
                float flicker = 0.82f
                        + (float) Math.sin(now * 0.024 + ophase[i] * 2.1f) * 0.13f
                        + (float) Math.cos(now * 0.039 - oseed[i] * 7f) * 0.08f;
                int io = innerN++ * 8;
                put8(innerBatch, io, x, flameY, z, size * 0.085f * fp * flicker,
                        1f, 0.94f, 0.62f, alpha * flicker);
                int oo = outerN++ * 8;
                put8(outerBatch, oo, x, flameY + size * 0.025f, z,
                        size * 0.16f * fp * flicker, 1f, 0.36f, 0.06f, alpha * 0.82f);
            } else {
                int o = flyerN++ * 12;
                flyerBatch[o] = x;
                flyerBatch[o + 1] = y;
                flyerBatch[o + 2] = z;
                flyerBatch[o + 3] = size;
                flyerBatch[o + 4] = oyaw[i];
                flyerBatch[o + 5] = (float) Math.sin(ophase[i] * 0.7f) * 0.16f;
                flyerBatch[o + 6] = color[0];
                flyerBatch[o + 7] = color[1];
                flyerBatch[o + 8] = color[2];
                flyerBatch[o + 9] = alpha;
                flyerBatch[o + 10] = otype[i];
                flyerBatch[o + 11] = ophase[i];
            }

            if (halos.enabled && glow > 0.01f) {
                float haloScale = otype[i] == SkyLanternRenderer.TYPE_STAR ? 1.1f : 0.62f;
                int go = glowN++ * 8;
                float haloSize = Math.min(size * haloScale * glow, 1.35f);
                put8(glowBatch, go, x, y, z, haloSize,
                        color[0], color[1], color[2], alpha * 0.12f * glow);
            }
        }

        int particleN = buildParticleBatch(now, globalAlpha);
        SkyLanternRenderer.drawLanterns(lanternBatch, lanternN, ctx);
        SkyLanternRenderer.drawFlyingObjects(flyerBatch, flyerN, ctx);
        SkyLanternRenderer.drawFlameInner(innerBatch, innerN, ctx);
        SkyLanternRenderer.drawFlameOuter(outerBatch, outerN, ctx);
        SkyLanternRenderer.drawGlowSprites(glowBatch, glowN, ctx);
        SkyLanternRenderer.drawEmbers(particleBatch, particleN, ctx);
    }

    private void spawnObject(long now) {
        if (mc.player == null || objectCount >= MAX_OBJECTS) return;
        int type = chooseType();
        if (type < 0) return;

        int i = objectCount++;
        oalive[i] = true;
        otype[i] = (byte) type;
        ostart[i] = now;
        oseed[i] = rand(0f, 1f);
        ophase[i] = rand(0f, (float) (Math.PI * 2));
        oscale[i] = 1f + rand(-sizeVariation.getValue(), sizeVariation.getValue());

        var p = mc.player.position();
        float radius = spawnRadius.getValue();
        float angle = rand(0f, (float) (Math.PI * 2));
        float radial = (float) Math.sqrt(Math.random()) * radius;
        ox[i] = opx[i] = (float) p.x + (float) Math.cos(angle) * radial;
        oz[i] = opz[i] = (float) p.z + (float) Math.sin(angle) * radial;
        float low = Math.min(minHeight.getValue(), maxHeight.getValue());
        float high = Math.max(minHeight.getValue(), maxHeight.getValue());
        oy[i] = opy[i] = (float) p.y + rand(low, high);

        float heading = rand(0f, (float) (Math.PI * 2));
        float speed = flightSpeed.getValue() / 20f;
        ovx[i] = (float) Math.sin(heading) * speed;
        ovz[i] = (float) Math.cos(heading) * speed;
        ovy[i] = rand(-0.003f, 0.006f);
        oyaw[i] = heading;

        if (type == SkyLanternRenderer.TYPE_STAR) {
            float starHeading = (float) Math.toRadians(windDirection.getValue() + rand(-22f, 22f));
            ovx[i] = (float) Math.cos(starHeading) * speed;
            ovz[i] = (float) Math.sin(starHeading) * speed;
            ovy[i] = -speed * rand(0.18f, 0.42f);
            oy[i] = opy[i] = (float) p.y + rand(Math.max(8f, low), Math.max(12f, high));
        }
    }

    private int chooseType() {
        int[] candidates = new int[5];
        int n = 0;
        if (typeEnabled(SkyLanternRenderer.TYPE_LANTERN)) candidates[n++] = SkyLanternRenderer.TYPE_LANTERN;
        if (typeEnabled(SkyLanternRenderer.TYPE_KOI)) candidates[n++] = SkyLanternRenderer.TYPE_KOI;
        if (typeEnabled(SkyLanternRenderer.TYPE_CRANE)) candidates[n++] = SkyLanternRenderer.TYPE_CRANE;
        if (typeEnabled(SkyLanternRenderer.TYPE_BUTTERFLY)) candidates[n++] = SkyLanternRenderer.TYPE_BUTTERFLY;
        if (typeEnabled(SkyLanternRenderer.TYPE_STAR)) candidates[n++] = SkyLanternRenderer.TYPE_STAR;
        if (n == 0) return -1;

        // Stars stay rare in mixed scenes so they remain a visual accent.
        int picked = candidates[(int) (Math.random() * n)];
        if (picked == SkyLanternRenderer.TYPE_STAR && n > 1 && Math.random() < 0.68) {
            picked = candidates[(int) (Math.random() * (n - 1))];
        }
        return picked;
    }

    private boolean typeEnabled(int type) {
        if (scene.is("Custom")) {
            return switch (type) {
                case SkyLanternRenderer.TYPE_LANTERN -> lanterns.enabled;
                case SkyLanternRenderer.TYPE_KOI -> spiritKoi.enabled;
                case SkyLanternRenderer.TYPE_CRANE -> paperCranes.enabled;
                case SkyLanternRenderer.TYPE_BUTTERFLY -> moonButterflies.enabled;
                case SkyLanternRenderer.TYPE_STAR -> shootingStars.enabled;
                default -> false;
            };
        }
        return switch (scene.get()) {
            case "Festival" -> type == SkyLanternRenderer.TYPE_LANTERN
                    || type == SkyLanternRenderer.TYPE_CRANE
                    || type == SkyLanternRenderer.TYPE_BUTTERFLY;
            case "Celestial" -> type == SkyLanternRenderer.TYPE_KOI
                    || type == SkyLanternRenderer.TYPE_BUTTERFLY
                    || type == SkyLanternRenderer.TYPE_STAR;
            case "Dream Garden" -> type == SkyLanternRenderer.TYPE_KOI
                    || type == SkyLanternRenderer.TYPE_CRANE
                    || type == SkyLanternRenderer.TYPE_BUTTERFLY;
            default -> true;
        };
    }

    private void spawnParticle(int object, float sizeMultiplier, boolean ember, long now) {
        int limit = particleLimit();
        int slot = particleHead % limit;
        for (int tries = 0; tries < limit; tries++) {
            if (!particleAlive[slot]) break;
            slot = (slot + 1) % limit;
        }
        if (particleAlive[slot]) {
            slot = particleHead % limit; // Replace the oldest ring entry.
        }
        particleHead = (slot + 1) % limit;
        particleAlive[slot] = true;

        float[] c = objectColor(object, now);
        particleX[slot] = ox[object] + rand(-0.035f, 0.035f);
        particleY[slot] = oy[object] - oscale[object] * objectSize.getValue() * 0.18f;
        particleZ[slot] = oz[object] + rand(-0.035f, 0.035f);
        particleVX[slot] = ember ? rand(-0.004f, 0.004f) : -ovx[object] * rand(0.08f, 0.2f);
        particleVY[slot] = ember ? rand(0.009f, 0.025f) : rand(-0.002f, 0.005f);
        particleVZ[slot] = ember ? rand(-0.004f, 0.004f) : -ovz[object] * rand(0.08f, 0.2f);
        particleR[slot] = ember ? 1f : c[0];
        particleG[slot] = ember ? rand(0.35f, 0.75f) : c[1];
        particleB[slot] = ember ? 0.08f : c[2];
        particleSize[slot] = objectSize.getValue() * 0.035f * sizeMultiplier;
        particleTime[slot] = now;
        particleLife[slot] = ember ? 900 : (otype[object] == SkyLanternRenderer.TYPE_STAR ? 1300 : 1050);
    }

    private void updateParticles(long now) {
        int limit = particleLimit();
        for (int i = 0; i < limit; i++) {
            if (!particleAlive[i]) continue;
            if (now - particleTime[i] >= particleLife[i]) {
                particleAlive[i] = false;
                continue;
            }
            particleX[i] += particleVX[i];
            particleY[i] += particleVY[i];
            particleZ[i] += particleVZ[i];
            particleVX[i] *= 0.985f;
            particleVZ[i] *= 0.985f;
            particleVY[i] += 0.00015f;
        }
    }

    private int buildParticleBatch(long now, float globalAlpha) {
        int n = 0;
        int limit = particleLimit();
        for (int i = 0; i < limit; i++) {
            if (!particleAlive[i]) continue;
            float age = (now - particleTime[i]) / (float) particleLife[i];
            if (age >= 1f) continue;
            float fade = (1f - age) * (1f - age);
            int o = n++ * 8;
            put8(particleBatch, o,
                    particleX[i], particleY[i], particleZ[i],
                    particleSize[i] * (0.65f + fade * 0.7f),
                    particleR[i], particleG[i], particleB[i],
                    fade * globalAlpha * 0.88f);
        }
        return n;
    }

    private float[] objectColor(int object, long now) {
        float seed = colorVariation.enabled ? oseed[object] : 0.35f;
        int a;
        int b;
        int c;
        switch (palette.get()) {
            case "Sakura" -> {
                a = 0xFFFF8FB4;
                b = 0xFFFFC4D9;
                c = 0xFFFFF0C9;
            }
            case "Aurora" -> {
                a = 0xFF55F4C4;
                b = 0xFF7A8CFF;
                c = 0xFFC46DFF;
            }
            case "Celestial" -> {
                a = 0xFF76C9FF;
                b = 0xFF8F78FF;
                c = 0xFFFFE29A;
            }
            case "Rainbow" -> {
                float hue = (seed + now * 0.000025f) % 1f;
                return hsv(hue, 0.68f, 1f);
            }
            case "Custom" -> {
                a = primaryColor.getColor();
                b = secondaryColor.getColor();
                c = accentColor.getColor();
            }
            default -> {
                a = 0xFFFF9A3D;
                b = 0xFFFF4F70;
                c = 0xFFFFE7A3;
            }
        }

        float[] ca = rgb(a);
        float[] cb = rgb(b);
        float[] cc = rgb(c);
        if (seed < 0.5f) return mix(ca, cb, seed * 2f);
        return mix(cb, cc, (seed - 0.5f) * 2f);
    }

    private int particleLimit() {
        return switch (quality.get()) {
            case "Low" -> 128;
            case "Medium" -> 288;
            case "Extravagant" -> MAX_PARTICLES;
            default -> 512;
        };
    }

    private int particleStep() {
        return switch (quality.get()) {
            case "Low" -> 4;
            case "Medium" -> 2;
            default -> 1;
        };
    }

    private float objectLifetime(int i, float baseLife) {
        float variation = 0.8f + oseed[i] * 0.4f;
        if (otype[i] == SkyLanternRenderer.TYPE_STAR) return baseLife * 0.24f * variation;
        return baseLife * variation;
    }

    private void compactObjects() {
        int w = 0;
        for (int r = 0; r < objectCount; r++) {
            if (!oalive[r]) continue;
            if (r != w) copyObject(r, w);
            w++;
        }
        for (int i = w; i < objectCount; i++) oalive[i] = false;
        objectCount = w;
    }

    private void copyObject(int from, int to) {
        ox[to] = ox[from];
        oy[to] = oy[from];
        oz[to] = oz[from];
        opx[to] = opx[from];
        opy[to] = opy[from];
        opz[to] = opz[from];
        ovx[to] = ovx[from];
        ovy[to] = ovy[from];
        ovz[to] = ovz[from];
        ophase[to] = ophase[from];
        oscale[to] = oscale[from];
        oyaw[to] = oyaw[from];
        oseed[to] = oseed[from];
        ostart[to] = ostart[from];
        otype[to] = otype[from];
        oalive[to] = true;
    }

    private static void put8(float[] array, int o, float x, float y, float z, float size,
                             float r, float g, float b, float alpha) {
        array[o] = x;
        array[o + 1] = y;
        array[o + 2] = z;
        array[o + 3] = size;
        array[o + 4] = r;
        array[o + 5] = g;
        array[o + 6] = b;
        array[o + 7] = alpha;
    }

    private static float computeAlpha(float life) {
        if (life >= 1f) return 0f;
        if (life < 0.08f) return life / 0.08f;
        if (life < 0.76f) return 1f;
        return 1f - (life - 0.76f) / 0.24f;
    }

    private static float[] rgb(int argb) {
        return new float[]{
                ((argb >> 16) & 0xFF) / 255f,
                ((argb >> 8) & 0xFF) / 255f,
                (argb & 0xFF) / 255f
        };
    }

    private static float[] mix(float[] a, float[] b, float t) {
        return new float[]{
                Mth.lerp(t, a[0], b[0]),
                Mth.lerp(t, a[1], b[1]),
                Mth.lerp(t, a[2], b[2])
        };
    }

    private static float[] hsv(float hue, float saturation, float value) {
        float h = (hue - (float) Math.floor(hue)) * 6f;
        int sector = (int) h;
        float f = h - sector;
        float p = value * (1f - saturation);
        float q = value * (1f - saturation * f);
        float t = value * (1f - saturation * (1f - f));
        return switch (sector) {
            case 0 -> new float[]{value, t, p};
            case 1 -> new float[]{q, value, p};
            case 2 -> new float[]{p, value, t};
            case 3 -> new float[]{p, q, value};
            case 4 -> new float[]{t, p, value};
            default -> new float[]{value, p, q};
        };
    }

    private static float rand(float min, float max) {
        return min + (float) Math.random() * (max - min);
    }
}
