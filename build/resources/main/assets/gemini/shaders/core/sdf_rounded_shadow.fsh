#version 330 core
#line 14 0
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

#line 28
float roundedRectSDF_0(vec2 p_0, vec2 halfSize_0, float r_0)
{

#line 29
    vec2 d_0 = abs(p_0) - halfSize_0 + vec2(r_0);
    return min(max(d_0.x, d_0.y), 0.0) + length(max(d_0, 0.0)) - r_0;
}


#line 26
vec4 _S1;


#line 26
out vec4 entryPointParam_main_fragColor_0;


#line 26
in vec2 gemini_varying_2;


#line 26
in vec2 gemini_varying_3;


#line 26
in vec2 gemini_varying_1;


#line 26
in vec4 gemini_varying_0;


#line 35
void main()
{

#line 36
    float w_0 = gemini_varying_2.x;
    float h_0 = gemini_varying_2.y;

    float _S2 = max(gemini_varying_3.y, 0.00100000004749745);

    vec2 halfSize_1 = vec2(w_0, h_0) * 0.5;
    float dist_0 = roundedRectSDF_0(gemini_varying_1 - halfSize_1, halfSize_1, min(gemini_varying_3.x, min(w_0, h_0) * 0.5));

#line 42
    float penumbra_0;

    if(dist_0 <= 0.0)
    {

#line 44
        penumbra_0 = 1.0;

#line 44
    }
    else
    {

#line 44
        penumbra_0 = exp(- dist_0 * dist_0 / (2.0 * _S2 * _S2));

#line 44
    }



    vec4 color_0 = gemini_varying_0 * ColorModulator;
    vec4 _S3 = vec4(color_0.xyz, color_0.w * (penumbra_0 * (1.0 - smoothstep(2.70000004768371582 * _S2, 3.0 * _S2, dist_0))));

#line 49
    _S1 = _S3;

    if((_S3.w) < 0.00400000018998981)
    {

#line 52
        discard;

#line 51
    }

#line 51
    entryPointParam_main_fragColor_0 = _S1;

#line 51
    return;
}

