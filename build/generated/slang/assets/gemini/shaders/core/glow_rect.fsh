#version 330 core
#line 12 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 12
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


vec4 _S1;


#line 22
out vec4 entryPointParam_main_fragColor_0;


#line 22
in vec2 gemini_varying_1;


#line 22
in vec4 gemini_varying_0;


#line 34
void main()
{

#line 34
    bool inside_0;



    if((gemini_varying_1.x) >= 0.0)
    {

#line 38
        inside_0 = (gemini_varying_1.x) <= 1.0;

#line 38
    }
    else
    {

#line 38
        inside_0 = false;

#line 38
    }
    if(inside_0)
    {

#line 39
        inside_0 = (gemini_varying_1.y) >= 0.0;

#line 39
    }
    else
    {

#line 39
        inside_0 = false;

#line 39
    }

#line 39
    if(inside_0)
    {

#line 39
        inside_0 = (gemini_varying_1.y) <= 1.0;

#line 39
    }
    else
    {

#line 39
        inside_0 = false;

#line 39
    }

#line 39
    float d_0;

    if(inside_0)
    {

#line 41
        d_0 = 0.0;

#line 41
    }
    else
    {

#line 41
        d_0 = length(max(vec2(0.0), max(- gemini_varying_1, gemini_varying_1 - 1.0)));

#line 41
    }

#line 51
    float glow_0 = exp(- d_0 * d_0 / 0.04500000178813934);

    float glow_1 = smoothstep(0.00999999977648258, 0.0, 1.0 - glow_0) * glow_0;

    vec4 color_0 = gemini_varying_0 * ColorModulator;
    vec4 _S2 = vec4(color_0.xyz * glow_1, color_0.w * glow_1);

#line 56
    _S1 = _S2;

    if((_S2.w) < 0.00400000018998981)
    {

#line 59
        discard;

#line 58
    }

#line 58
    entryPointParam_main_fragColor_0 = _S1;

#line 58
    return;
}

