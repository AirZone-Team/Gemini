#version 330

// 孔明灯 (Sky Lantern) fragment shader — paper translucency with Fresnel backlight.
//
// vertexColor.rgb = material tint:
//   Paper shell:  warm amber from the module color setting
//   Bamboo frame: darker brown (set by renderer)
// vertexColor.a   = alpha (lifetime fade × FOV-adjusted intensity)
//
// Effects:
//   - Fresnel edge glow: paper edges brighten at grazing angles (backlit paper)
//   - Internal candle diffusion: light shines through the paper
//   - Subtle flicker from candle flame

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec3 viewPos;

out vec4 fragColor;

void main() {
    // Surface normal from screen-space derivatives
    vec3 N = normalize(cross(dFdx(viewPos), dFdy(viewPos)));

    // View direction (camera at origin in view space)
    vec3 V = normalize(-viewPos);

    float NdotV = abs(dot(N, V));

    // ── Fresnel backlight — paper edges glow brighter ──────────────
    // As the surface turns away from the camera, light scatters through
    // the paper fibers, creating a luminous edge.
    float fresnel = pow(1.0 - NdotV, 3.5);

    // ── Paper diffusion — light transmits through the shell ─────────
    // Strongest when facing the camera (candle light shines through).
    float diffuse = NdotV * 0.55 + 0.25;

    // ── Candle light spectrum — warm amber/orange ───────────────────
    vec3 innerLight = vec3(1.0, 0.70, 0.22);

    // ── Flicker — organic candle movement ───────────────────────────
    float flicker = 1.0
        + sin(viewPos.y * 67.0 + viewPos.x * 43.0)  * 0.035
        + cos(viewPos.x * 89.0 - viewPos.y * 55.0)  * 0.025
        + sin(viewPos.x * 113.0 + viewPos.y * 97.0) * 0.02;

    // ── Combine ─────────────────────────────────────────────────────
    // Paper tint from vertex color
    vec3 paperTint = vertexColor.rgb;

    // Fresnel edge glow × paper tint (edges are tinted by paper color)
    vec3 edgeGlow = paperTint * fresnel * 0.55 * flicker;

    // Diffuse internal light (candle shining through paper)
    vec3 bodyGlow = innerLight * paperTint * diffuse * 0.75 * flicker;

    vec3 rgb = bodyGlow + edgeGlow;
    float alpha = vertexColor.a * (diffuse * 0.7 + fresnel * 0.6 + 0.15) * flicker;

    fragColor = vec4(rgb, alpha) * ColorModulator;

    if (fragColor.a < 0.001) {
        discard;
    }
}
