#version 330 core
#line 22 0
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

#line 19
uniform sampler2D SceneSampler;


#line 20
uniform sampler2D BloomSampler;


#line 28
vec4 _S1;


#line 28
out vec4 entryPointParam_main_fragColor_0;


#line 28
in vec2 gemini_varying_0;


#line 103
void main()
{


    vec2 halfStep_0 = 1.0 / Params.xy * 0.5;

    float _S2 = halfStep_0.x;

#line 109
    float _S3 = - _S2;

#line 109
    float _S4 = halfStep_0.y;

#line 109
    float _S5 = - _S4;

#line 114
    vec4 _S6 = vec4(((texture((SceneSampler), (gemini_varying_0 + vec2(_S3, _S5)))).xyz + (texture((SceneSampler), (gemini_varying_0 + vec2(_S2, _S5)))).xyz + (texture((SceneSampler), (gemini_varying_0 + vec2(_S3, _S4)))).xyz + (texture((SceneSampler), (gemini_varying_0 + vec2(_S2, _S4)))).xyz) * 0.25, 1.0);

#line 114
    _S1 = _S6;

#line 114
    entryPointParam_main_fragColor_0 = _S6;

#line 114
    return;
}

