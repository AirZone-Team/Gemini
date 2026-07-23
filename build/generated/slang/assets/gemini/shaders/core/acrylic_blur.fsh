#version 330 core
#line 13 0
uniform sampler2D Sampler0;


#line 14
uniform sampler2D Sampler1;


#line 16
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 16
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 36
const vec2  POISSON_0[13] = vec2[](vec2(0.0, 0.0), vec2(0.52389997243881226, 0.85180002450942993), vec2(0.85180002450942993, -0.52389997243881226), vec2(-0.52389997243881226, -0.85180002450942993), vec2(-0.85180002450942993, 0.52389997243881226), vec2(0.31169998645782471, -0.95020002126693726), vec2(0.95020002126693726, 0.31169998645782471), vec2(-0.31169998645782471, 0.95020002126693726), vec2(-0.95020002126693726, -0.31169998645782471), vec2(-0.1476999968290329, 0.19509999454021454), vec2(0.19509999454021454, 0.1476999968290329), vec2(0.1476999968290329, -0.19509999454021454), vec2(-0.19509999454021454, -0.14710000157356262));

#line 26
vec4 _S1;


#line 26
out vec4 entryPointParam_main_fragColor_0;


#line 26
in vec2 gemini_varying_1;


#line 26
in vec4 gemini_varying_0;


#line 54
void main()
{

#line 55
    ivec2 result_0;

#line 55
#line 55
    ((result_0[0]) = textureSize((Sampler0), int((0U))).x), ((result_0[1]) = textureSize((Sampler0), int((0U))).y);
    vec2 _S2 = 1.0 / vec2(result_0);


    float _S3 = clamp(8.0, 1.0, 32.0);


    const vec4 _S4 = vec4(0.0);

#line 62
    int i_0 = 0;

#line 62
    vec4 blurred_0 = _S4;

#line 62
    float weightSum_0 = 0.0;

    for(;;)
    {

#line 64
        if(i_0 < 13)
        {
        }
        else
        {

#line 64
            break;
        }

#line 65
        vec2 sampleCoord_0 = gemini_varying_1 + POISSON_0[i_0] * _S3 * _S2;
        float w_0 = 1.0 / (1.0 + length(POISSON_0[i_0]));
        vec4 blurred_1 = blurred_0 + (texture((Sampler0), (sampleCoord_0))) * w_0;
        float weightSum_1 = weightSum_0 + w_0;

#line 64
        i_0 = i_0 + 1;

#line 64
        blurred_0 = blurred_1;

#line 64
        weightSum_0 = weightSum_1;

#line 64
    }

#line 70
    vec4 blurred_2 = blurred_0 / weightSum_0;


    vec2 _S5 = gemini_varying_1 * 4.0;

#line 84
    vec4 _S6 = vec4(clamp(mix(blurred_2.xyz, gemini_varying_0.xyz, gemini_varying_0.w) + ((texture((Sampler1), (_S5))).x - 0.5) * 0.05000000074505806, 0.0, 1.0) * ColorModulator.xyz, blurred_2.w * ColorModulator.w);

#line 84
    _S1 = _S6;

    if((_S6.w) < 0.00400000018998981)
    {

#line 87
        discard;

#line 86
    }

#line 86
    entryPointParam_main_fragColor_0 = _S1;

#line 86
    return;
}

