#version 330 core
#line 3 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 3
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

vec4 _S1;


#line 12
out vec4 entryPointParam_main_fragColor_0;


#line 12
in vec2 gemini_varying_1;


#line 12
in vec4 gemini_varying_0;


void main()
{

#line 26
    vec4 _S2 = vec4(gemini_varying_0.xyz, gemini_varying_0.w * pow(smoothstep(1.0, 0.0, length(gemini_varying_1)), 2.20000004768371582)) * ColorModulator;

#line 26
    _S1 = _S2;

    if((_S2.w) < 0.00100000004749745)
    {

#line 29
        discard;

#line 28
    }

#line 28
    entryPointParam_main_fragColor_0 = _S1;

#line 28
    return;
}

