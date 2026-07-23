#version 330 core
#line 41 0
uniform sampler2D SceneSampler;


#line 115
uniform sampler2D DepthSampler;


#line 44
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

#line 42
uniform sampler2D BloomSampler;


#line 62
float luminance_0(vec3 c_0)
{

#line 62
    return dot(c_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464));
}


#line 58
vec4 _S1;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 119
void main()
{

#line 120
    vec3 color_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;


    float lum_0 = luminance_0(color_0);

#line 128
    float _S2 = lum_0 - Params.w;



    float _S3 = 1.0 / Params.x;

#line 132
    float _S4 = 1.0 / Params.y;

    float _S5 = - _S3;

    float dTR_0 = (texture((DepthSampler), (gemini_varying_0 + vec2(_S3, _S4)))).x;


    float _S6 = - _S4;

#line 139
    float dBL_0 = (texture((DepthSampler), (gemini_varying_0 + vec2(_S5, _S6)))).x;

    float dBR_0 = (texture((DepthSampler), (gemini_varying_0 + vec2(_S3, _S6)))).x;

    float _S7 = - (texture((DepthSampler), (gemini_varying_0 + vec2(_S5, _S4)))).x;

#line 143
    float gx_0 = _S7 - 2.0 * (texture((DepthSampler), (gemini_varying_0 + vec2(_S5, 0.0)))).x - dBL_0 + dTR_0 + 2.0 * (texture((DepthSampler), (gemini_varying_0 + vec2(_S3, 0.0)))).x + dBR_0;
    float gy_0 = _S7 - 2.0 * (texture((DepthSampler), (gemini_varying_0 + vec2(0.0, _S4)))).x - dTR_0 + dBL_0 + 2.0 * (texture((DepthSampler), (gemini_varying_0 + vec2(0.0, _S6)))).x + dBR_0;
    float edge_0 = sqrt(gx_0 * gx_0 + gy_0 * gy_0);

#line 151
    float proximity_0 = exp(- length(gemini_varying_0 * 2.0 - 1.0 - Center1.xy) * 2.79999995231628418);

#line 158
    float depthFade_0 = 1.0 - smoothstep(0.85000002384185791, 1.0, (texture((DepthSampler), (gemini_varying_0))).x);

#line 167
    vec4 _S8 = vec4(mix(color_0, color_0 + vec3(0.40000000596046448, 0.60000002384185791, 1.0) * 0.07999999821186066, edge_0 * proximity_0 * 0.40000000596046448 * depthFade_0) * clamp(clamp((_S2 + 0.07999999821186066) / 0.15999999642372131, 0.0, 1.0) * max(_S2, 0.0) / max(lum_0, 0.00100000004749745) + edge_0 * 3.5 * proximity_0 * 0.25 + edge_0 * 1.5 * proximity_0 * depthFade_0 * 0.10000000149011612, 0.0, 3.0), 1.0);

#line 167
    _S1 = _S8;

#line 167
    entryPointParam_main_fragColor_0 = _S8;

#line 167
    return;
}

