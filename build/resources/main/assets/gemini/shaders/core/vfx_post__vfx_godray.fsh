#version 330 core
#line 25 0
struct SLANG_ParameterGroup_VFXUniforms_0
{
    vec4 Params;
    vec4 DistortPack;
    vec4 GodRayPack;
    vec4 ChromPack;
    vec4 BloomPack;
    vec4 LensPack;
};


#line 25
layout(std140) uniform VFXUniforms
{
    vec4 Params;
    vec4 DistortPack;
    vec4 GodRayPack;
    vec4 ChromPack;
    vec4 BloomPack;
    vec4 LensPack;
};

#line 22
uniform sampler2D SceneSampler;


#line 23
uniform sampler2D BloomSampler;


#line 35
vec4 _S1;


#line 35
out vec4 entryPointParam_main_fragColor_0;


#line 35
in vec2 gemini_varying_0;


#line 85
void main()
{

#line 86
    float strength_0 = GodRayPack.x;


    vec2 toCenter_0 = vec2(0.5) - gemini_varying_0;
    float dist_0 = length(toCenter_0);
    vec2 _S2 = toCenter_0 / max(dist_0, 0.00100000004749745);

    const vec3 _S3 = vec3(0.0);

#line 93
    int i_0 = 0;

#line 93
    vec3 rays_0 = _S3;

#line 93
    float wSum_0 = 0.0;



    for(;;)
    {

#line 97
        if(i_0 < 32)
        {
        }
        else
        {

#line 97
            break;
        }

#line 98
        float t_0 = (float(i_0) + 0.5) / 32.0;
        vec2 suv_0 = gemini_varying_0 + _S2 * t_0 * dist_0;
        float w_0 = exp(- t_0 * 3.0);
        vec3 rays_1 = rays_0 + (texture((SceneSampler), (suv_0))).xyz * w_0;
        float wSum_1 = wSum_0 + w_0;

#line 97
        i_0 = i_0 + 1;

#line 97
        rays_0 = rays_1;

#line 97
        wSum_0 = wSum_1;

#line 97
    }

#line 107
    vec3 rays_2 = rays_0 / max(wSum_0, 0.00100000004749745) * (exp(- dist_0 * 1.5) * strength_0);


    vec4 _S4 = vec4((texture((SceneSampler), (gemini_varying_0))).xyz + rays_2, 1.0);

#line 110
    _S1 = _S4;

#line 110
    entryPointParam_main_fragColor_0 = _S4;

#line 110
    return;
}

