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


vec4 _S1;


#line 21
out vec4 entryPointParam_main_fragColor_0;


#line 21
in vec2 gemini_varying_1;


#line 21
in vec4 gemini_varying_0;


#line 33
void main()
{

#line 33
    bool inside_0;


    if((gemini_varying_1.x) >= 0.0)
    {

#line 36
        inside_0 = (gemini_varying_1.x) <= 1.0;

#line 36
    }
    else
    {

#line 36
        inside_0 = false;

#line 36
    }
    if(inside_0)
    {

#line 37
        inside_0 = (gemini_varying_1.y) >= 0.0;

#line 37
    }
    else
    {

#line 37
        inside_0 = false;

#line 37
    }

#line 37
    if(inside_0)
    {

#line 37
        inside_0 = (gemini_varying_1.y) <= 1.0;

#line 37
    }
    else
    {

#line 37
        inside_0 = false;

#line 37
    }

#line 37
    float d_0;

    if(inside_0)
    {

#line 39
        d_0 = 0.0;

#line 39
    }
    else
    {

#line 39
        d_0 = length(max(vec2(0.0), max(- gemini_varying_1, gemini_varying_1 - 1.0)));

#line 39
    }

#line 48
    float shadow_0 = exp(- d_0 * d_0 / 0.02879999950528145);
    float shadow_1 = smoothstep(0.00999999977648258, 0.0, 1.0 - shadow_0) * shadow_0;

    vec4 color_0 = gemini_varying_0 * ColorModulator;
    vec4 _S2 = vec4(color_0.xyz * shadow_1, color_0.w * shadow_1);

#line 52
    _S1 = _S2;

    if((_S2.w) < 0.00400000018998981)
    {

#line 55
        discard;

#line 54
    }

#line 54
    entryPointParam_main_fragColor_0 = _S1;

#line 54
    return;
}

