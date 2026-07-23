#version 330 core
#line 8 0
uniform sampler2D InSampler;


#line 10
struct SLANG_ParameterGroup_SamplerInfo_0
{
    vec2 OutSize;
    vec2 InSize;
};


#line 10
layout(std140) uniform SamplerInfo
{
    vec2 OutSize;
    vec2 InSize;
};

vec4 _S1;


#line 17
out vec4 entryPointParam_main_fragColor_0;


#line 17
in vec2 gemini_varying_0;


#line 24
void main()
{

#line 25
    vec4 _S2 = (texture((InSampler), (gemini_varying_0)));
    vec3 _S3 = _S2.xyz;

#line 26
    float lum_0 = dot(_S3, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464));



    if(lum_0 < 0.5)
    {

#line 31
        _S1 = vec4(0.0);

#line 30
    }
    else
    {


        float _S4 = min(1.0, (lum_0 - 0.5) / max(0.00999999977648258, 0.5));
        _S1 = vec4(_S3 * _S4, _S4 * _S2.w);

#line 30
    }

#line 30
    entryPointParam_main_fragColor_0 = _S1;

#line 30
    return;
}

