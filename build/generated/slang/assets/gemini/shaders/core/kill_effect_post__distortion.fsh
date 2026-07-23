#version 330 core
#line 44 0
struct SLANG_ParameterGroup_PostUniforms_0
{
    vec4 Params;
    vec4 TimePack;
    vec4 Center1;
    vec4 Center2;
    vec4 PassParams;
    vec4 BHParams;
    vec4 CameraParams;
    vec4 LightViewPos;
    vec4 LightColor;
    vec4 MiscParams;
};


#line 44
layout(std140) uniform PostUniforms
{
    vec4 Params;
    vec4 TimePack;
    vec4 Center1;
    vec4 Center2;
    vec4 PassParams;
    vec4 BHParams;
    vec4 CameraParams;
    vec4 LightViewPos;
    vec4 LightColor;
    vec4 MiscParams;
};

#line 41
uniform sampler2D SceneSampler;


#line 42
uniform sampler2D BloomSampler;


#line 248
float hashDistort_0(vec2 p_0)
{

#line 249
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noiseDistort_0(vec2 p_1)
{

#line 253
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);
    float _S2 = f_0.x;

#line 256
    return mix(mix(hashDistort_0(i_0), hashDistort_0(i_0 + vec2(1.0, 0.0)), _S2), mix(hashDistort_0(i_0 + vec2(0.0, 1.0)), hashDistort_0(i_0 + vec2(1.0, 1.0)), _S2), f_0.y);
}


#line 58
vec4 _S3;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 262
void main()
{



    vec2 dir_0 = gemini_varying_0 - (Center1.xy * 0.5 + 0.5);

#line 288
    vec4 _S4 = vec4(mix((texture((SceneSampler), (gemini_varying_0))).xyz, (texture((SceneSampler), (gemini_varying_0 + normalize(dir_0 + 0.00100000004749745) * min(PassParams.x * 0.03999999910593033 / (length(dir_0) + 0.05000000074505806) * (0.69999998807907104 + noiseDistort_0(gemini_varying_0 * 80.0 + TimePack.x * 0.5) * 0.60000002384185791), 0.07999999821186066)))).xyz, 1.0 - smoothstep(0.75, 1.0, length(gemini_varying_0 - 0.5) * 2.0)), 1.0);

#line 288
    _S3 = _S4;

#line 288
    entryPointParam_main_fragColor_0 = _S4;

#line 288
    return;
}

