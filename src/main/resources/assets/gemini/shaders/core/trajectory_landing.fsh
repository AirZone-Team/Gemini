#version 330

// Landing-point ring indicator.
// UV0 maps the billboard quad 0→1; the ring is a procedural annulus
// with a central glow.  vertexColor.rgb = ring colour,
// vertexColor.a       = master opacity.

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec4 vertexColor;
in vec2 uvCoord;

out vec4 fragColor;

void main() {
    vec2 uv = uvCoord - 0.5;
    float dist = length(uv);

    // Ring (annulus) centred at ~0.40, half-width ~0.06
    float ringOuter = 0.46;
    float ringInner = 0.34;
    float ring = 1.0 - smoothstep(ringInner, ringInner + 0.04, dist)
               - 1.0 + smoothstep(ringOuter - 0.04, ringOuter, dist);
    ring = clamp(ring, 0.0, 1.0);

    // Central glow — bright spot that fades radially
    float glow = exp(-dist * 8.0) * 0.6;

    float alpha = max(ring, glow) * vertexColor.a;
    if (alpha < 0.004) discard;

    fragColor = vec4(vertexColor.rgb, alpha) * ColorModulator;
}
