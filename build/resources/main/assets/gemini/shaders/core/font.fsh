#version 330 core
#line 37 0
uniform sampler2D Sampler0;


#line 39
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 39
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 51
float median_0(float r_0, float g_0, float b_0)
{

#line 52
    return max(min(r_0, g_0), min(max(r_0, g_0), b_0));
}




float pxRange_0()
{

#line 59
    ivec3 _S1 = ivec3(ivec2(0, 0), 0);

#line 59
    vec2 bytes_0 = round((texelFetch((Sampler0), ((_S1)).xy, ((_S1)).z)).xy * 255.0);
    return (bytes_0.x + 256.0 * bytes_0.y) / 256.0;
}


#line 60
in vec2 gemini_varying_1;

float screenPxRange_0()
{

    vec2 _S2 = vec2(pxRange_0());

#line 66
    ivec2 result_0;

#line 66
#line 66
    ((result_0[0]) = textureSize((Sampler0), int((0U))).x), ((result_0[1]) = textureSize((Sampler0), int((0U))).y);


    vec2 _S3 = dFdx(gemini_varying_1);
    vec2 _S4 = dFdy(gemini_varying_1);

    return max(0.5 * dot(_S2 / vec2(result_0), (inversesqrt((_S3 * _S3 + _S4 * _S4)))), 1.0);
}


#line 49
vec4 _S5;


#line 49
out vec4 entryPointParam_main_fragColor_0;


#line 49
in vec4 gemini_varying_0;


#line 77
void main()
{

#line 78
    vec4 _S6 = (texture((Sampler0), (gemini_varying_1)));

    float screenRange_0 = screenPxRange_0();

#line 93
    vec4 color_0 = vec4(gemini_varying_0.xyz, gemini_varying_0.w * clamp(screenRange_0 * (mix(_S6.w, median_0(_S6.x, _S6.y, _S6.z), smoothstep(1.0, 2.0, screenRange_0)) - 0.5) + 0.5, 0.0, 1.0)) * ColorModulator;


    if((color_0.w) < 0.00400000018998981)
    {

#line 97
        discard;

#line 96
    }



    _S5 = color_0;

#line 100
    entryPointParam_main_fragColor_0 = color_0;

#line 100
    return;
}

