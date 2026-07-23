#version 330 core
#line 22 0
uniform sampler2D SceneSampler;


#line 23
uniform sampler2D BloomSampler;


#line 25
struct SLANG_ParameterGroup_VFXUniforms_0
{
    vec4 Params;
    vec4 DistortPack;
    vec4 GodRayPack;
    vec4 ChromPack;
    vec4 BloomPack;
    vec4 LensPack;
};


#line 25
layout(std140) uniform VFXUniforms
{
    vec4 Params;
    vec4 DistortPack;
    vec4 GodRayPack;
    vec4 ChromPack;
    vec4 BloomPack;
    vec4 LensPack;
};
vec4 _S1;


#line 35
out vec4 entryPointParam_main_fragColor_0;


#line 35
in vec2 gemini_varying_0;


#line 156
void main()
{

#line 157
    vec3 color_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;

#line 165
    vec2 uvC_0 = gemini_varying_0 - 0.5;


    vec4 _S2 = vec4(clamp(color_0 * (color_0 * 2.50999999046325684 + 0.02999999932944775) / (color_0 * (color_0 * 2.43000006675720215 + 0.5899999737739563) + 0.14000000059604645), 0.0, 1.0) * (1.0 - dot(uvC_0, uvC_0) * 0.40000000596046448), 1.0);

#line 168
    _S1 = _S2;

#line 168
    entryPointParam_main_fragColor_0 = _S2;

#line 168
    return;
}

