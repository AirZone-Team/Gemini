#version 330 core
#line 6 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 6
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


vec4 _S1;


#line 16
out vec4 entryPointParam_main_fragColor_0;


#line 16
in vec4 gemini_varying_0;


void main()
{

#line 21
    vec4 color_0 = gemini_varying_0 * ColorModulator;
    if((color_0.w) < 0.00400000018998981)
    {

#line 22
        discard;

#line 22
    }
    _S1 = color_0;

#line 23
    entryPointParam_main_fragColor_0 = color_0;

#line 23
    return;
}

