#version 330 core
#line 7 0
uniform sampler2D BaseSampler;


#line 6
uniform sampler2D InSampler;


#line 14
struct SLANG_ParameterGroup_BlendConfig_0
{
    float Intensity;
    float _pad0;
    float _pad1;
    float _pad2;
};


#line 14
layout(std140) uniform BlendConfig
{
    float Intensity;
    float _pad0;
    float _pad1;
    float _pad2;
};

#line 9
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

#line 23
vec4 _S1;


#line 23
out vec4 entryPointParam_main_fragColor_0;


#line 23
in vec2 gemini_varying_0;


void main()
{

#line 28
    vec4 _S2 = (texture((BaseSampler), (gemini_varying_0)));
    vec4 _S3 = (texture((InSampler), (gemini_varying_0)));

    float fi_0 = clamp(Intensity, 0.0, 1.0);

#line 37
    vec4 _S4 = vec4(1.0 - (1.0 - _S2.xyz) * (1.0 - _S3.xyz * fi_0), min(1.0, _S2.w + _S3.w * fi_0));

#line 37
    _S1 = _S4;

#line 37
    entryPointParam_main_fragColor_0 = _S4;

#line 37
    return;
}

