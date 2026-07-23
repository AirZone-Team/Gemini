#version 330 core
#line 42 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 42
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 54
vec4 _S1;


#line 54
out vec4 entryPointParam_main_fragColor_0;


#line 54
in vec2 gemini_varying_2;


#line 54
in vec2 gemini_varying_3;


#line 54
in vec2 gemini_varying_1;


#line 54
in vec4 gemini_varying_0;


#line 65
void main()
{

#line 66
    float midR_0 = gemini_varying_2.x;

#line 71
    float _S2 = gemini_varying_2.y * 0.5;
    vec2 p_0 = gemini_varying_1 - vec2(midR_0 + _S2 + 1.20000004768371582);

#line 77
    float theta_0 = (atan((p_0.y),(p_0.x))) - -1.57079637050628662;

#line 82
    float tC_0 = clamp(theta_0 - 6.28318548202514648 * floor(theta_0 / 6.28318548202514648), 0.0, clamp(gemini_varying_3.x / 10000.0, 0.0, 1.0) * 6.28318548202514648);

#line 87
    float _S3 = tC_0 + -1.57079637050628662;


    float d_0 = length(p_0 - (midR_0 + 1.20000004768371582 * cos(10.0 * tC_0 - gemini_varying_3.y / 32767.0 * 6.28318548202514648)) * vec2(cos(_S3), sin(_S3))) - _S2;



    vec4 color_0 = gemini_varying_0 * ColorModulator;
    vec4 _S4 = vec4(color_0.xyz, color_0.w * (1.0 - smoothstep(0.0, max((fwidth((d_0))), 0.00009999999747379), d_0)));

#line 95
    _S1 = _S4;

    if((_S4.w) < 0.00400000018998981)
    {

#line 98
        discard;

#line 97
    }

#line 97
    entryPointParam_main_fragColor_0 = _S1;

#line 97
    return;
}

