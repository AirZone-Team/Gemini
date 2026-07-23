#version 330 core
#line 25 0
struct SLANG_ParameterGroup_VFXUniforms_0
{
    vec4 Params;
    vec4 DistortPack;
    vec4 GodRayPack;
    vec4 ChromPack;
    vec4 BloomPack;
    vec4 LensPack;
};


#line 25
layout(std140) uniform VFXUniforms
{
    vec4 Params;
    vec4 DistortPack;
    vec4 GodRayPack;
    vec4 ChromPack;
    vec4 BloomPack;
    vec4 LensPack;
};

#line 22
uniform sampler2D SceneSampler;


#line 23
uniform sampler2D BloomSampler;


#line 39
float vfxHash_0(vec2 p_0)
{

#line 40
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float vfxNoise_0(vec2 p_1)
{

#line 44
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);
    float _S2 = f_0.x;

#line 47
    return mix(mix(vfxHash_0(i_0), vfxHash_0(i_0 + vec2(1.0, 0.0)), _S2), mix(vfxHash_0(i_0 + vec2(0.0, 1.0)), vfxHash_0(i_0 + vec2(1.0, 1.0)), _S2), f_0.y);
}


#line 35
vec4 _S3;


#line 35
out vec4 entryPointParam_main_fragColor_0;


#line 35
in vec2 gemini_varying_0;


#line 57
void main()
{


    vec2 dir_0 = gemini_varying_0 - vec2(0.5);

#line 75
    vec4 _S4 = vec4(mix((texture((SceneSampler), (gemini_varying_0))).xyz, (texture((SceneSampler), (gemini_varying_0 + normalize(dir_0 + 0.00100000004749745) * min(DistortPack.x * 0.03999999910593033 / (length(dir_0) + 0.05000000074505806) * (0.69999998807907104 + vfxNoise_0(gemini_varying_0 * 80.0 + Params.z * 0.5) * 0.60000002384185791), 0.07999999821186066)))).xyz, 1.0 - smoothstep(0.75, 1.0, length(gemini_varying_0 - 0.5) * 2.0)), 1.0);

#line 75
    _S3 = _S4;

#line 75
    entryPointParam_main_fragColor_0 = _S4;

#line 75
    return;
}

