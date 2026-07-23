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


#line 58
void main()
{

#line 59
    vec2 _S2 = vec2(1.0 / Params.x, 0.0);


    const vec3 _S3 = vec3(0.0);

#line 62
    int i_0 = -8;

#line 62
    vec3 color_0 = _S3;

#line 62
    float wSum_0 = 0.0;


    for(;;)
    {

#line 65
        if(i_0 <= 8)
        {
        }
        else
        {

#line 65
            break;
        }

#line 66
        float w_0 = exp(- float(i_0 * i_0) / 8.0);
        vec2 _S4 = gemini_varying_0 + _S2 * float(i_0);

#line 67
        vec3 color_1 = color_0 + (texture((SceneSampler), (_S4))).xyz * w_0;
        float wSum_1 = wSum_0 + w_0;

#line 65
        i_0 = i_0 + 1;

#line 65
        color_0 = color_1;

#line 65
        wSum_0 = wSum_1;

#line 65
    }

#line 70
    vec4 _S5 = vec4(color_0 / wSum_0, 1.0);

#line 70
    _S1 = _S5;

#line 70
    entryPointParam_main_fragColor_0 = _S5;

#line 70
    return;
}

