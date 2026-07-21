// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
in uint geminiVertexId : SV_VertexID;


// Full-screen triangle vertex shader for sweep attack post-processing.
// Covers clip space with a single triangle (no vertex buffer needed).

out vec2 vUv;

[shader("vertex")]

void main() {
    vec2 pos;
    if (geminiVertexId == 0)      pos = vec2(-1.0, -1.0);
    else if (geminiVertexId == 1) pos = vec2( 3.0, -1.0);
    else                       pos = vec2(-1.0,  3.0);
    vUv = pos * 0.5 + 0.5;
    gl_Position = vec4(pos, 0.0, 1.0);
}
