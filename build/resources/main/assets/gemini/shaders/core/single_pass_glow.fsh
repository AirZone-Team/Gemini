#version 330 core
#line 12 0
uniform sampler2D InSampler;


#line 25
struct SLANG_ParameterGroup_GlowConfig_0
{
    float GlowRadius;
    float GlowIntensity;
    float GlowThreshold;
    float _pad;
};


#line 25
layout(std140) uniform GlowConfig
{
    float GlowRadius;
    float GlowIntensity;
    float GlowThreshold;
    float _pad;
};

#line 14
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 14
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 21
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 21
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 38
float luminance_0(vec3 c_0)
{

#line 39
    return dot(c_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464));
}


#line 35
vec4 _S1;


#line 35
out vec4 entryPointParam_main_fragColor_0;


#line 35
in vec2 gemini_varying_1;


#line 35
in vec4 gemini_varying_0;


#line 44
void main()
{

#line 45
    ivec2 result_0;

#line 45
#line 45
    ((result_0[0]) = textureSize((InSampler), int((0U))).x), ((result_0[1]) = textureSize((InSampler), int((0U))).y);

#line 45
    vec2 _S2 = 1.0 / vec2(result_0);


    float _S3 = max(1.0, GlowRadius);
    float intensity_0 = clamp(GlowIntensity, 0.0, 1.0);
    float _S4 = clamp(GlowThreshold, 0.0, 1.0);
    int iRadius_0 = int(ceil(_S3));

    const vec4 _S5 = vec4(0.0);

    float _S6 = _S3 * 0.40000000596046448;

    int _S7 = - iRadius_0;

#line 57
    int dy_0 = _S7;

#line 57
    vec4 blurred_0 = _S5;

#line 57
    float weightSum_0 = 0.0;

#line 57
    for(;;)
    {

#line 57
        if(dy_0 <= iRadius_0)
        {
        }
        else
        {

#line 57
            break;
        }

#line 57
        int dx_0 = _S7;
        for(;;)
        {

#line 58
            if(dx_0 <= iRadius_0)
            {
            }
            else
            {

#line 58
                break;
            }

#line 59
            vec2 sampleCoord_0 = gemini_varying_1 + vec2(float(dx_0), float(dy_0)) * _S2;
            vec4 _S8 = (texture((InSampler), (sampleCoord_0)));

#line 68
            float weight_0 = exp(- float(dx_0 * dx_0 + dy_0 * dy_0) / (2.0 * _S6 * _S6)) * smoothstep(_S4 - 0.05000000074505806, _S4 + 0.05000000074505806, luminance_0(_S8.xyz));

            vec4 blurred_1 = blurred_0 + _S8 * weight_0;
            float weightSum_1 = weightSum_0 + weight_0;

#line 58
            dx_0 = dx_0 + 1;

#line 58
            blurred_0 = blurred_1;

#line 58
            weightSum_0 = weightSum_1;

#line 58
        }

#line 57
        dy_0 = dy_0 + 1;

#line 57
    }

#line 75
    vec4 glow_0 = blurred_0 / max(0.00009999999747379, weightSum_0);


    vec4 _S9 = vec4(glow_0.xyz * gemini_varying_0.xyz * intensity_0, glow_0.w * gemini_varying_0.w * intensity_0);

#line 78
    _S1 = _S9;

#line 78
    entryPointParam_main_fragColor_0 = _S9;

#line 78
    return;
}

