#version 330 core
#line 10 0
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

#line 15
struct SLANG_ParameterGroup_BlurConfig_0
{
    vec2 BlurDir;
    float Radius;
};


#line 15
layout(std140) uniform BlurConfig
{
    vec2 BlurDir;
    float Radius;
};

#line 8
uniform sampler2D InSampler;


#line 22
vec4 _S1;


#line 22
out vec4 entryPointParam_main_fragColor_0;


#line 22
in vec2 gemini_varying_0;


void main()
{
    vec2 sampleStep_0 = 1.0 / InSize * BlurDir;

    const vec4 _S2 = vec4(0.0);
    float _S3 = max(1.0, round(Radius));

#line 31
    float a_0 = - _S3 + 0.5;

#line 31
    vec4 blurred_0 = _S2;



    for(;;)
    {

#line 35
        if(a_0 <= _S3)
        {
        }
        else
        {

#line 35
            break;
        }

#line 36
        vec2 _S4 = gemini_varying_0 + sampleStep_0 * a_0;

#line 36
        vec4 blurred_1 = blurred_0 + (texture((InSampler), (_S4)));

#line 35
        a_0 = a_0 + 2.0;

#line 35
        blurred_0 = blurred_1;

#line 35
    }



    vec2 _S5 = gemini_varying_0 + sampleStep_0 * _S3;

    vec4 _S6 = (blurred_0 + (texture((InSampler), (_S5))) / 2.0) / (_S3 + 0.5);

#line 41
    _S1 = _S6;

#line 41
    entryPointParam_main_fragColor_0 = _S6;

#line 41
    return;
}

