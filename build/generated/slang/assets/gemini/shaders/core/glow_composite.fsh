#version 330 core
#line 7 0
uniform sampler2D BaseSampler;


#line 6
uniform sampler2D InSampler;

struct SLANG_ParameterGroup_SamplerInfo_0
{
    vec2 OutSize;
    vec2 InSize;
};


#line 9
layout(std140) uniform SamplerInfo
{
    vec2 OutSize;
    vec2 InSize;
};

vec4 _S1;


#line 16
out vec4 entryPointParam_main_fragColor_0;


#line 16
in vec2 gemini_varying_0;


void main()
{

#line 21
    vec4 _S2 = (texture((BaseSampler), (gemini_varying_0)));



    _S1.xyz = _S2.xyz + (texture((InSampler), (gemini_varying_0))).xyz;
    _S1[3] = _S2.w;

#line 26
    entryPointParam_main_fragColor_0 = _S1;

#line 26
    return;
}

