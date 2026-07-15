#version 330

// Full-screen triangle vertex shader for mip bloom post-processing.
// Covers clip space with a single triangle (no vertex buffer needed).

out vec2 vUv;

void main() {
    vec2 pos;
    if (gl_VertexID == 0)      pos = vec2(-1.0, -1.0);
    else if (gl_VertexID == 1) pos = vec2( 3.0, -1.0);
    else                       pos = vec2(-1.0,  3.0);
    vUv = pos * 0.5 + 0.5;
    gl_Position = vec4(pos, 0.0, 1.0);
}
