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


#line 80
void main()
{

#line 81
    vec2 _S2 = vec2(0.0, 1.0 / Params.y);


    const vec3 _S3 = vec3(0.0);

#line 84
    int i_0 = -8;

#line 84
    vec3 color_0 = _S3;

#line 84
    float wSum_0 = 0.0;


    for(;;)
    {

#line 87
        if(i_0 <= 8)
        {
        }
        else
        {

#line 87
            break;
        }

#line 88
        float w_0 = exp(- float(i_0 * i_0) / 8.0);
        vec2 _S4 = gemini_varying_0 + _S2 * float(i_0);

#line 89
        vec3 color_1 = color_0 + (texture((SceneSampler), (_S4))).xyz * w_0;
        float wSum_1 = wSum_0 + w_0;

#line 87
        i_0 = i_0 + 1;

#line 87
        color_0 = color_1;

#line 87
        wSum_0 = wSum_1;

#line 87
    }

#line 92
    vec4 _S5 = vec4(color_0 / wSum_0, 1.0);

#line 92
    _S1 = _S5;

#line 92
    entryPointParam_main_fragColor_0 = _S5;

#line 92
    return;
}

