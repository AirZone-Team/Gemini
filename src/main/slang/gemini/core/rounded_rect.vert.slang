// Slang source of truth. Generated Minecraft GLSL is written to build/generated.
// GLSL-compatible spellings below are provided by Slang's compatibility module.
in uint geminiVertexId : SV_VertexID;


// Full-screen triangle for SDF rounded-rectangle rendering.
// The actual rectangle bounds are passed via RoundedRectUniforms,
// and the render pass scissor is set to the rectangle's bounding box
// so only relevant fragments are shaded.

out vec2 uvCoord;

[shader("vertex")]

void main() {
    vec2 pos;
    if (geminiVertexId == 0) pos = vec2(-1.0, -1.0);
    else if (geminiVertexId == 1) pos = vec2( 3.0, -1.0);
    else                    pos = vec2(-1.0,  3.0);

    gl_Position = vec4(pos, 0.0, 1.0);
    uvCoord = pos * 0.5 + 0.5;
}
