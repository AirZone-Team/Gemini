// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
// Infinite perspective grid background with aurora and subtle noise.
//
// Uniforms (GridUniforms, std140):
//   Params.xy = screen resolution (width, height)
//   Params.z  = time in seconds
//   Params.w  = unused

layout(std140) uniform GridUniforms {
    vec4 Params;
};

in vec2 vUv;
out vec4 fragColor;

// ── Hash & Noise ──────────────────────────────────────────────

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);

    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));

    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    for (int i = 0; i < 5; i++) {
        value += amplitude * noise(p * frequency);
        amplitude *= 0.5;
        frequency *= 2.0;
    }
    return value;
}

// ── Main ──────────────────────────────────────────────────────

[shader("fragment")]

void main() {
    vec2 resolution = Params.xy;
    float time = Params.z;
    float aspect = resolution.x / resolution.y;

    vec2 uv = vUv;
    uv.x *= aspect;

    // ── Layer 1: Pure black base ──
    vec3 color = vec3(0.02);

    // ── Layer 2: Perspective infinite grid ──
    {
        vec2 gridUv = uv;
        gridUv.y += time * 0.03;

        float perspective = 1.0 / (gridUv.y * 2.5 + 1.0);
        gridUv.x *= perspective;

        vec2 gv = fract(gridUv * 40.0);

        // Detect proximity to grid edges (near 0 or near 1)
        float edgeX = 1.0 - smoothstep(0.0, 0.004, min(gv.x, 1.0 - gv.x));
        float edgeY = 1.0 - smoothstep(0.0, 0.004, min(gv.y, 1.0 - gv.y));
        float grid = max(edgeX, edgeY);

        // Fade grid toward the top (further away)
        float fadeTop = smoothstep(0.0, 0.6, vUv.y);
        grid *= (1.0 - fadeTop);

        // Fade grid toward edges
        float fadeEdge = smoothstep(0.0, 0.15, vUv.x)
                       * smoothstep(0.0, 0.15, 1.0 - vUv.x);
        grid *= fadeEdge;

        color += vec3(0.08) * grid;
    }

    // ── Layer 3: Subtle fluid noise (breathing feel) ──
    {
        float n = fbm(uv * 3.0 + time * 0.05);
        color += vec3(0.02) * n;
    }

    // ── Layer 4: Dynamic aurora ──
    {
        vec3 accent = vec3(0.25, 0.45, 1.0);

        float aurora = fbm(vec2(uv.x * 2.0 + time * 0.08, uv.y * 1.5));
        aurora *= smoothstep(0.3, 0.7, vUv.y);
        aurora *= smoothstep(0.8, 0.4, vUv.y);

        // Subtle horizontal variation
        float wave = sin(uv.x * 4.0 + time * 0.2) * 0.5 + 0.5;
        aurora *= mix(0.6, 1.0, wave);

        color += accent * aurora * 0.03;
    }

    fragColor = vec4(color, 1.0);
}
