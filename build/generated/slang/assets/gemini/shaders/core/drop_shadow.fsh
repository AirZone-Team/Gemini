#version 330 core
#line 16 0
struct SLANG_ParameterGroup_SamplerInfo_0
{
    vec2 OutSize;
    vec2 InSize;
};


#line 16
layout(std140) uniform SamplerInfo
{
    vec2 OutSize;
    vec2 InSize;
};

#line 9
struct SLANG_ParameterGroup_ShadowConfig_0
{
    vec2 ShadowOffset;
    vec4 ShadowColor;
    float ShadowRadius;
    float _pad;
};


#line 9
layout(std140) uniform ShadowConfig
{
    vec2 ShadowOffset;
    vec4 ShadowColor;
    float ShadowRadius;
    float _pad;
};

#line 7
uniform sampler2D MaskSampler;


#line 23
vec4 _S1;


#line 23
out vec4 entryPointParam_main_fragColor_0;


#line 23
in vec2 gemini_varying_0;


void main()
{

#line 28
    vec2 oneTexel_0 = 1.0 / InSize;


    vec2 offsetCoord_0 = gemini_varying_0 - ShadowOffset * oneTexel_0;
    float maskAlpha_0 = (texture((MaskSampler), (offsetCoord_0))).w;

#line 39
    float _S2 = max(1.0, round(ShadowRadius));

    int iRadius_0 = int(_S2);

    int _S3 = - iRadius_0;

#line 43
    int dy_0 = _S3;

#line 43
    float shadow_0 = 0.0;

#line 43
    float weightSum_0 = 0.0;

#line 43
    for(;;)
    {

#line 43
        if(dy_0 <= iRadius_0)
        {
        }
        else
        {

#line 43
            break;
        }

#line 43
        int dx_0 = _S3;

#line 43
        float shadow_1 = shadow_0;

#line 43
        float weightSum_1 = weightSum_0;
        for(;;)
        {

#line 44
            if(dx_0 <= iRadius_0)
            {
            }
            else
            {

#line 44
                break;
            }

#line 45
            float dist_0 = sqrt(float(dx_0 * dx_0 + dy_0 * dy_0));
            if(dist_0 <= _S2)
            {

#line 47
                float w_0 = exp(-0.5 * (dist_0 * dist_0) / (_S2 * _S2 * 0.25));


                float weightSum_2 = weightSum_1 + w_0;

#line 50
                shadow_1 = shadow_1 + (texture((MaskSampler), (offsetCoord_0 + vec2(float(dx_0), float(dy_0)) * oneTexel_0))).w * w_0;

#line 50
                weightSum_1 = weightSum_2;

#line 46
            }

#line 44
            dx_0 = dx_0 + 1;

#line 44
        }

#line 43
        dy_0 = dy_0 + 1;

#line 43
        shadow_0 = shadow_1;

#line 43
        weightSum_0 = weightSum_1;

#line 43
    }

#line 58
    vec4 _S4 = vec4(ShadowColor.xyz, shadow_0 / max(0.00100000004749745, weightSum_0) * maskAlpha_0 * ShadowColor.w);

#line 58
    _S1 = _S4;

#line 58
    entryPointParam_main_fragColor_0 = _S4;

#line 58
    return;
}

