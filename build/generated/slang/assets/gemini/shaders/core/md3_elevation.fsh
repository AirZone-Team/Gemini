#version 330 core
#line 22 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 22
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 44
float roundedRectSDF_0(vec2 p_0, vec2 halfSize_0, float r_0)
{

#line 45
    vec2 d_0 = abs(p_0) - halfSize_0 + vec2(r_0);
    return min(max(d_0.x, d_0.y), 0.0) + length(max(d_0, 0.0)) - r_0;
}


#line 32
vec4 _S1;


#line 32
out vec4 entryPointParam_main_fragColor_0;


#line 32
in vec4 gemini_varying_0;


#line 32
in vec2 gemini_varying_1;


#line 51
void main()
{

#line 52
    float w_0 = gemini_varying_0.x * 512.0;
    float h_0 = gemini_varying_0.y * 512.0;

    float strength_0 = gemini_varying_0.w;

    vec2 halfSize_1 = vec2(w_0, h_0) * 0.5;
    float dist_0 = roundedRectSDF_0(gemini_varying_1 - halfSize_1, halfSize_1, min(gemini_varying_0.z * 64.0, min(w_0, h_0) * 0.5));

#line 58
    float penumbra_0;

#line 63
    if(dist_0 <= 0.0)
    {

#line 63
        penumbra_0 = 1.0;

#line 63
    }
    else
    {

#line 63
        penumbra_0 = exp(- dist_0 * dist_0 / 50.0);

#line 63
    }

    float a_0 = strength_0 * penumbra_0;
    if(a_0 < 0.00400000018998981)
    {

#line 66
        discard;

#line 66
    }


    vec4 _S2 = vec4(vec3(0.0), a_0) * ColorModulator;

#line 69
    _S1 = _S2;

#line 69
    entryPointParam_main_fragColor_0 = _S2;

#line 69
    return;
}

