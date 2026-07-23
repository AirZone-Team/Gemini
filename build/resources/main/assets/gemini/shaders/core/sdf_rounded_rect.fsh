#version 330 core
#line 11 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 11
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 27
float roundedRectSDF_0(vec2 p_0, vec2 halfSize_0, float r_0)
{

#line 28
    vec2 d_0 = abs(p_0) - halfSize_0 + vec2(r_0);
    return min(max(d_0.x, d_0.y), 0.0) + length(max(d_0, 0.0)) - r_0;
}


#line 23
vec4 _S1;


#line 23
out vec4 entryPointParam_main_fragColor_0;


#line 23
in vec2 gemini_varying_2;


#line 23
in vec2 gemini_varying_3;


#line 23
in vec2 gemini_varying_1;


#line 23
in vec4 gemini_varying_0;


#line 34
void main()
{

#line 35
    float w_0 = gemini_varying_2.x;
    float h_0 = gemini_varying_2.y;

    float thickness_0 = gemini_varying_3.y;

    vec2 halfSize_1 = vec2(w_0, h_0) * 0.5;
    float dist_0 = roundedRectSDF_0(gemini_varying_1 - halfSize_1, halfSize_1, min(gemini_varying_3.x, min(w_0, h_0) * 0.5));


    float _S2 = max((fwidth((dist_0))), 0.00009999999747379);

#line 44
    float alpha_0;


    if(thickness_0 > 0.0)
    {

        float _S3 = - thickness_0;

#line 50
        alpha_0 = (1.0 - smoothstep(0.0, _S2, dist_0)) * smoothstep(_S3 - _S2, _S3, dist_0);

#line 47
    }
    else
    {

#line 47
        alpha_0 = 1.0 - smoothstep(0.0, _S2, dist_0);

#line 47
    }

#line 56
    vec4 color_0 = gemini_varying_0 * ColorModulator;
    vec4 _S4 = vec4(color_0.xyz, color_0.w * alpha_0);

#line 57
    _S1 = _S4;

    if((_S4.w) < 0.00400000018998981)
    {

#line 60
        discard;

#line 59
    }

#line 59
    entryPointParam_main_fragColor_0 = _S1;

#line 59
    return;
}

