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


#line 13
out vec4 entryPointParam_main_fragColor_0;


#line 13
in vec2 gemini_varying_1;


#line 13
in vec4 gemini_varying_0;


void main()
{

#line 18
    vec2 point_0 = gemini_varying_1 - 0.5;
    point_0[0] = fract(gemini_varying_1.x) - 0.5;
    float radius_0 = length(point_0) * 2.0;

    float _S2 = - radius_0;
    float core_0 = exp(_S2 * 13.0) * 1.79999995231628418;


    float _S3 = exp(_S2 * 3.0);

#line 31
    float energy_0 = exp(_S2 * 3.79999995231628418) * 0.57999998331069946 + core_0 + (exp(- abs(point_0.x) * 42.0) * exp(- abs(point_0.y) * 3.5) + exp(- abs(point_0.y) * 42.0) * exp(- abs(point_0.x) * 3.5)) * 0.72000002861022949 + (exp(- abs(point_0.x + point_0.y) * 35.0) * _S3 + exp(- abs(point_0.x - point_0.y) * 35.0) * _S3) * 0.36000001430511475 + exp(- abs(radius_0 - 0.4699999988079071) * 42.0) * 0.72000002861022949;
    float alpha_0 = clamp(energy_0, 0.0, 1.0) * gemini_varying_0.w;

#line 32
    bool _S4;
    if(alpha_0 < 0.00400000018998981)
    {

#line 33
        _S4 = true;

#line 33
    }
    else
    {

#line 33
        _S4 = radius_0 > 1.41999995708465576;

#line 33
    }

#line 33
    if(_S4)
    {

#line 33
        discard;

#line 33
    }



    vec4 _S5 = vec4(gemini_varying_0.xyz * energy_0 + vec3(core_0) * 0.64999997615814209, alpha_0) * ColorModulator;

#line 37
    _S1 = _S5;

#line 37
    entryPointParam_main_fragColor_0 = _S5;

#line 37
    return;
}

