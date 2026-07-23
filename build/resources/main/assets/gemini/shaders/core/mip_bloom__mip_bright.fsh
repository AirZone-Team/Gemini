#version 330 core
#line 19 0
uniform sampler2D SceneSampler;

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

#line 20
uniform sampler2D BloomSampler;


#line 31
float lum_0(vec3 c_0)
{

#line 31
    return dot(c_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464));
}


#line 28
vec4 _S1;


#line 28
out vec4 entryPointParam_main_fragColor_0;


#line 28
in vec2 gemini_varying_0;


#line 39
void main()
{

#line 40
    vec3 color_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;
    float l_0 = lum_0(color_0);



    float _S2 = l_0 - Params.z;


    vec4 _S3 = vec4(color_0 * (clamp((_S2 + 0.07999999821186066) / 0.15999999642372131, 0.0, 1.0) * max(_S2, 0.0) / max(l_0, 0.00100000004749745)), 1.0);

#line 48
    _S1 = _S3;

#line 48
    entryPointParam_main_fragColor_0 = _S3;

#line 48
    return;
}

