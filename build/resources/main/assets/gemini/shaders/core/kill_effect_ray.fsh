#version 330 core
#line 21 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 21
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 39
float hash2_0(vec2 p_0)
{

#line 40
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}


#line 35
float hash_0(float n_0)
{

#line 36
    return fract(sin(n_0) * 43758.546875);
}


#line 31
vec4 _S1;


#line 31
out vec4 entryPointParam_main_fragColor_0;


#line 31
in vec4 gemini_varying_0;


#line 31
in vec2 gemini_varying_1;


#line 49
void main()
{

#line 50
    float distAlong_0 = gemini_varying_0.x;
    float rayIndex_0 = gemini_varying_0.y;
    float intensity_0 = gemini_varying_0.z * 4.0;
    float alpha_0 = gemini_varying_0.w;

#line 53
    bool _S2;

    if(alpha_0 < 0.00100000004749745)
    {

#line 55
        _S2 = true;

#line 55
    }
    else
    {

#line 55
        _S2 = intensity_0 < 0.00100000004749745;

#line 55
    }

#line 55
    if(_S2)
    {

#line 55
        discard;

#line 55
        entryPointParam_main_fragColor_0 = _S1;

#line 55
        return;
    }

    float u_0 = gemini_varying_1.x;

#line 63
    float widthAtU_0 = 0.02999999932944775 + u_0 * 0.10000000149011612;
    float vCentered_0 = abs(gemini_varying_1.y - 0.5) * 2.0;
    float crossSection_0 = exp(- vCentered_0 * vCentered_0 / (widthAtU_0 * widthAtU_0));

#line 84
    float brightness_0 = (crossSection_0 * (1.0 / (1.0 + u_0 * u_0 * 8.0)) + exp(- u_0 * 6.0) * 0.69999998807907104 * crossSection_0 * 2.0) * (hash2_0(vec2(u_0 * 20.0 + rayIndex_0, rayIndex_0 * 7.13000011444091797)) * 0.30000001192092896 + 0.69999998807907104) * (1.0 + 0.15000000596046448 * sin(u_0 * 40.0 + rayIndex_0 * 13.0 + distAlong_0 * 30.0)) * intensity_0;

#line 96
    vec3 col_0 = mix(mix(vec3(1.0, 1.0, 1.0), vec3(0.55000001192092896, 0.80000001192092896, 1.0), smoothstep(0.0, 0.40000000596046448, u_0)), vec3(0.20000000298023224, 0.15000000596046448, 0.80000001192092896), smoothstep(0.40000000596046448, 1.0, u_0));


    float hueShift_0 = hash_0(rayIndex_0 * 17.0) * 0.15000000596046448;

#line 107
    vec4 _S3 = vec4(mix(col_0, col_0 * vec3(1.0 + hueShift_0, 1.0, 1.0 - hueShift_0), 0.30000001192092896) * (brightness_0 * 3.0), brightness_0 * alpha_0) * ColorModulator;

#line 107
    _S1 = _S3;

    if((_S3.w) < 0.00050000002374873)
    {

#line 109
        discard;

#line 109
    }

#line 109
    entryPointParam_main_fragColor_0 = _S1;

#line 109
    return;
}

