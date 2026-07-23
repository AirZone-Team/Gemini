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


#line 306
void main()
{

#line 307
    float strength_0 = PassParams.y;

    vec2 center2_0 = Center2.xy * 0.5 + 0.5;

    const vec3 _S2 = vec3(0.0);

#line 316
    vec2 toCenter1_0 = Center1.xy * 0.5 + 0.5 - gemini_varying_0;
    float dist1_0 = length(toCenter1_0);
    vec2 _S3 = toCenter1_0 / max(dist1_0, 0.00100000004749745);
    float decay1_0 = exp(- dist1_0 * 1.5);

#line 319
    int i_0 = 0;

#line 319
    vec3 rays_0 = _S2;

#line 319
    float weightSum_0 = 0.0;

    for(;;)
    {

#line 321
        if(i_0 < 32)
        {
        }
        else
        {

#line 321
            break;
        }

#line 322
        float t_0 = (float(i_0) + 0.5) / 32.0;
        vec2 suv_0 = gemini_varying_0 + _S3 * t_0 * dist1_0;
        float w_0 = exp(- t_0 * 3.0);
        vec3 rays_1 = rays_0 + (texture((SceneSampler), (suv_0))).xyz * w_0;
        float weightSum_1 = weightSum_0 + w_0;

#line 321
        i_0 = i_0 + 1;

#line 321
        rays_0 = rays_1;

#line 321
        weightSum_0 = weightSum_1;

#line 321
    }

#line 329
    vec3 rays_2 = rays_0 / max(weightSum_0, 0.00100000004749745) * (decay1_0 * strength_0);


    vec2 toCenter2_0 = center2_0 - gemini_varying_0;
    float dist2_0 = length(toCenter2_0);

#line 333
    bool _S4;
    if(dist2_0 < 2.0)
    {

#line 334
        _S4 = strength_0 > 0.00999999977648258;

#line 334
    }
    else
    {

#line 334
        _S4 = false;

#line 334
    }

#line 334
    if(_S4)
    {

#line 335
        vec2 _S5 = toCenter2_0 / max(dist2_0, 0.00100000004749745);
        float decay2_0 = exp(- dist2_0 * 2.0);

#line 336
        i_0 = 0;

#line 336
        vec3 rays2_0 = _S2;

#line 336
        float ws2_0 = 0.0;


        for(;;)
        {

#line 339
            if(i_0 < 16)
            {
            }
            else
            {

#line 339
                break;
            }

#line 340
            float t_1 = (float(i_0) + 0.5) / 16.0;
            vec2 suv_1 = gemini_varying_0 + _S5 * t_1 * dist2_0;
            float w_1 = exp(- t_1 * 3.0);
            vec3 rays2_1 = rays2_0 + (texture((SceneSampler), (suv_1))).xyz * w_1;
            float ws2_1 = ws2_0 + w_1;

#line 339
            i_0 = i_0 + 1;

#line 339
            rays2_0 = rays2_1;

#line 339
            ws2_0 = ws2_1;

#line 339
        }

#line 339
        rays_0 = rays_2 + rays2_0 / max(ws2_0, 0.00100000004749745) * decay2_0 * strength_0 * 0.60000002384185791;

#line 334
    }
    else
    {

#line 334
        rays_0 = rays_2;

#line 334
    }

#line 351
    vec4 _S6 = vec4((texture((SceneSampler), (gemini_varying_0))).xyz + rays_0, 1.0);

#line 351
    _S1 = _S6;

#line 351
    entryPointParam_main_fragColor_0 = _S6;

#line 351
    return;
}

