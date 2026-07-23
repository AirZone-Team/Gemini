#version 330 core
#line 44 0
struct SLANG_ParameterGroup_PostUniforms_0
{
    vec4 Params;
    vec4 TimePack;
    vec4 Center1;
    vec4 Center2;
    vec4 PassParams;
    vec4 BHParams;
    vec4 CameraParams;
    vec4 LightViewPos;
    vec4 LightColor;
    vec4 MiscParams;
};


#line 44
layout(std140) uniform PostUniforms
{
    vec4 Params;
    vec4 TimePack;
    vec4 Center1;
    vec4 Center2;
    vec4 PassParams;
    vec4 BHParams;
    vec4 CameraParams;
    vec4 LightViewPos;
    vec4 LightColor;
    vec4 MiscParams;
};

#line 41
uniform sampler2D SceneSampler;


#line 42
uniform sampler2D BloomSampler;


#line 58
vec4 _S1;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 508
void main()
{
    const vec2 center_0 = vec2(0.5);

#line 515
    vec2 _S2 = normalize(gemini_varying_0 - center_0 + 0.00100000004749745) * (PassParams.z * 0.00800000037997961 * length(gemini_varying_0 - center_0));



    vec4 _S3 = vec4((texture((SceneSampler), (gemini_varying_0 + _S2))).x, (texture((SceneSampler), (gemini_varying_0))).y, (texture((SceneSampler), (gemini_varying_0 - _S2))).z, 1.0);

#line 519
    _S1 = _S3;

#line 519
    entryPointParam_main_fragColor_0 = _S3;

#line 519
    return;
}

