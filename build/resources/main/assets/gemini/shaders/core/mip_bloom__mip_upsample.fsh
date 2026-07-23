#version 330 core
#line 19 0
uniform sampler2D SceneSampler;


#line 20
uniform sampler2D BloomSampler;


#line 22
struct SLANG_ParameterGroup_BloomUniforms_0
{
    vec4 Params;
    vec4 Weights;
};


#line 22
layout(std140) uniform BloomUniforms
{
    vec4 Params;
    vec4 Weights;
};
vec4 _S1;


#line 28
out vec4 entryPointParam_main_fragColor_0;


#line 28
in vec2 gemini_varying_0;


#line 126
void main()
{

#line 132
    vec4 _S2 = vec4((texture((SceneSampler), (gemini_varying_0))).xyz + (texture((BloomSampler), (gemini_varying_0))).xyz, 1.0);

#line 132
    _S1 = _S2;

#line 132
    entryPointParam_main_fragColor_0 = _S2;

#line 132
    return;
}

