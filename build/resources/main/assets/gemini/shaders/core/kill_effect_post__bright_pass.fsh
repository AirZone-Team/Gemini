#version 330 core
#line 41 0
uniform sampler2D SceneSampler;

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

#line 42
uniform sampler2D BloomSampler;


#line 62
float luminance_0(vec3 c_0)
{

#line 62
    return dot(c_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464));
}


#line 58
vec4 _S1;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 87
void main()
{

#line 88
    vec3 color_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;
    float lum_0 = luminance_0(color_0);

#line 94
    float _S2 = lum_0 - Params.w;


    vec4 _S3 = vec4(color_0 * (clamp((_S2 + 0.07999999821186066) / 0.15999999642372131, 0.0, 1.0) * max(_S2, 0.0) / max(lum_0, 0.00100000004749745)), 1.0);

#line 97
    _S1 = _S3;

#line 97
    entryPointParam_main_fragColor_0 = _S3;

#line 97
    return;
}

