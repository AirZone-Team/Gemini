#version 330 core
#line 25 0
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

#line 22
uniform sampler2D SceneSampler;


#line 23
uniform sampler2D BloomSampler;


#line 35
vec4 _S1;


#line 35
out vec4 entryPointParam_main_fragColor_0;


#line 35
in vec2 gemini_varying_0;


#line 120
void main()
{
    const vec2 center_0 = vec2(0.5);

#line 127
    vec2 _S2 = normalize(gemini_varying_0 - center_0 + 0.00100000004749745) * (ChromPack.x * 0.00800000037997961 * length(gemini_varying_0 - center_0));



    vec4 _S3 = vec4((texture((SceneSampler), (gemini_varying_0 + _S2))).x, (texture((SceneSampler), (gemini_varying_0))).y, (texture((SceneSampler), (gemini_varying_0 - _S2))).z, 1.0);

#line 131
    _S1 = _S3;

#line 131
    entryPointParam_main_fragColor_0 = _S3;

#line 131
    return;
}

