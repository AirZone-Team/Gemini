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


#line 141
void main()
{



    vec4 _S2 = vec4((texture((SceneSampler), (gemini_varying_0))).xyz + (texture((BloomSampler), (gemini_varying_0))).xyz * BloomPack.y, 1.0);

#line 146
    _S1 = _S2;

#line 146
    entryPointParam_main_fragColor_0 = _S2;

#line 146
    return;
}

