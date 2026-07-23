#version 330 core
#line 14 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 14
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 1321 1
in vec4 gemini_varying_0;


#line 27 0
float elemWidth_0()
{

#line 27
    return gemini_varying_0.x * 512.0;
}


#line 28
float elemHeight_0()
{

#line 28
    return gemini_varying_0.y * 512.0;
}


#line 29
float cornerRadius_0()
{

#line 29
    return gemini_varying_0.z * 64.0;
}


#line 30
float masterAlpha_0()
{

#line 30
    return gemini_varying_0.w;
}




float roundedRectSDF_0(vec2 p_0, vec2 halfSize_0, float r_0)
{

#line 37
    vec2 d_0 = abs(p_0) - halfSize_0 + vec2(r_0);
    return min(max(d_0.x, d_0.y), 0.0) + length(max(d_0, 0.0)) - r_0;
}


#line 24
vec4 _S1;


#line 24
out vec4 entryPointParam_main_fragColor_0;


#line 24
in vec2 gemini_varying_1;


#line 43
void main()
{

#line 44
    float w_0 = elemWidth_0();
    float h_0 = elemHeight_0();

#line 50
    vec2 halfSize_1 = vec2(w_0, h_0) * 0.5;



    float dist_0 = roundedRectSDF_0((gemini_varying_1 * 2.0 - 1.0) * halfSize_1, halfSize_1, min(cornerRadius_0(), min(w_0, h_0) * 0.5));


    float shape_0 = 1.0 - smoothstep(0.0, 1.5, dist_0);

#line 64
    vec4 color_0 = vec4(vec3(0.07999999821186066, 0.07999999821186066, 0.11999999731779099) * mix(0.81999999284744263, 0.72000002861022949, gemini_varying_1.y), 0.77999997138977051 * shape_0 * masterAlpha_0());


    float accentH_0 = 2.0 / h_0;

#line 67
    bool _S2;
    if((gemini_varying_1.y) < accentH_0)
    {

#line 68
        _S2 = shape_0 > 0.0;

#line 68
    }
    else
    {

#line 68
        _S2 = false;

#line 68
    }

#line 68
    if(_S2)
    {

        color_0.xyz = mix(color_0.xyz, vec3(0.34999999403953552, 0.55000001192092896, 1.0), (1.0 - smoothstep(0.0, accentH_0, gemini_varying_1.y)) * 0.60000002384185791);

#line 68
    }

#line 78
    vec4 _S3 = mix(color_0, vec4(0.25, 0.30000001192092896, 0.44999998807907104, 0.40000000596046448), (1.0 - smoothstep(0.0, 1.0, abs(dist_0) - 1.0)) * (1.0 - step(dist_0, 0.0)));

#line 78
    color_0 = _S3;

    if((_S3.w) < 0.00400000018998981)
    {

#line 80
        discard;

#line 80
    }
    vec4 _S4 = color_0 * ColorModulator;

#line 81
    _S1 = _S4;

#line 81
    entryPointParam_main_fragColor_0 = _S4;

#line 81
    return;
}

