#version 330 core
#line 15 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 15
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


vec4 _S1;


#line 25
out vec4 entryPointParam_main_fragColor_0;


#line 25
in vec3 gemini_varying_1;


#line 25
in vec4 gemini_varying_0;


void main()
{

#line 36
    float NdotV_0 = abs(dot(normalize(cross(dFdx(gemini_varying_1), dFdy(gemini_varying_1))), normalize(- gemini_varying_1)));

#line 41
    float fresnel_0 = pow(1.0 - NdotV_0, 3.5);



    float diffuse_0 = NdotV_0 * 0.55000001192092896 + 0.25;

#line 54
    float flicker_0 = 1.0 + sin(gemini_varying_1.y * 67.0 + gemini_varying_1.x * 43.0) * 0.03500000014901161 + cos(gemini_varying_1.x * 89.0 - gemini_varying_1.y * 55.0) * 0.02500000037252903 + sin(gemini_varying_1.x * 113.0 + gemini_varying_1.y * 97.0) * 0.01999999955296516;



    vec3 paperTint_0 = gemini_varying_0.xyz;

#line 69
    vec4 _S2 = vec4(vec3(1.0, 0.69999998807907104, 0.2199999988079071) * paperTint_0 * diffuse_0 * 0.75 * flicker_0 + paperTint_0 * fresnel_0 * 0.55000001192092896 * flicker_0, gemini_varying_0.w * (diffuse_0 * 0.69999998807907104 + fresnel_0 * 0.60000002384185791 + 0.15000000596046448) * flicker_0) * ColorModulator;

#line 69
    _S1 = _S2;

    if((_S2.w) < 0.00100000004749745)
    {

#line 72
        discard;

#line 71
    }

#line 71
    entryPointParam_main_fragColor_0 = _S1;

#line 71
    return;
}

