#version 330 core
#line 6 0
uniform sampler2D InSampler;


#line 8
struct SLANG_ParameterGroup_SamplerInfo_0
{
    vec2 OutSize;
    vec2 InSize;
};


#line 8
layout(std140) uniform SamplerInfo
{
    vec2 OutSize;
    vec2 InSize;
};

vec4 _S1;


#line 15
out vec4 entryPointParam_main_fragColor_0;


#line 15
in vec2 gemini_varying_0;


void main()
{

#line 29
    vec4 _S2 = (texture((InSampler), (gemini_varying_0)));

#line 29
    _S1 = _S2;

#line 29
    entryPointParam_main_fragColor_0 = _S2;

#line 29
    return;
}

