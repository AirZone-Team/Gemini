#version 330 core
#line 5 0
struct SLANG_ParameterGroup_SweepPostUniforms_0
{
    vec4 Params;
    vec4 Strength;
    vec4 Tint;
};


#line 5
layout(std140) uniform SweepPostUniforms
{
    vec4 Params;
    vec4 Strength;
    vec4 Tint;
};

#line 3
uniform sampler2D SceneSampler;


#line 14
float hash_0(vec2 p_0)
{

#line 15
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise2_0(vec2 p_1)
{

#line 19
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);
    float _S2 = f_0.x;

#line 22
    return mix(mix(hash_0(i_0), hash_0(i_0 + vec2(1.0, 0.0)), _S2), mix(hash_0(i_0 + vec2(0.0, 1.0)), hash_0(i_0 + vec2(1.0)), _S2), f_0.y);
}


#line 12
vec4 _S3;


#line 12
out vec4 entryPointParam_main_fragColor_0;


#line 12
in vec2 gemini_varying_0;


#line 28
void main()
{

#line 29
    vec2 centerVector_0 = gemini_varying_0 - 0.5;
    float distanceToCenter_0 = length(centerVector_0);
    vec2 direction_0 = centerVector_0 / max(distanceToCenter_0, 0.00009999999747379);
    float _S4 = - distanceToCenter_0 * distanceToCenter_0;

#line 32
    float lens_0 = exp(_S4 * 5.0);



    const vec2 _S5 = vec2(0.00100000004749745);

#line 36
    const vec2 _S6 = vec2(0.99900001287460327);

#line 36
    vec2 warpedUv_0 = clamp(gemini_varying_0 + direction_0 * (Strength.x * 0.01799999922513962 * lens_0 * (sin(distanceToCenter_0 * 52.0 - Params.z * 13.0) * 0.62000000476837158 + (noise2_0(gemini_varying_0 * vec2(70.0, 42.0) + Params.z * 0.69999998807907104) - 0.5) * 0.37999999523162842)), _S5, _S6);


    vec3 scene_0;
    vec2 _S7 = direction_0 * (Strength.y * 0.00650000013411045 * (0.30000001192092896 + distanceToCenter_0) * lens_0);

#line 40
    scene_0[0] = (texture((SceneSampler), (clamp(warpedUv_0 + _S7, _S5, _S6)))).x;

    scene_0[1] = (texture((SceneSampler), (warpedUv_0))).y;
    scene_0[2] = (texture((SceneSampler), (clamp(warpedUv_0 - _S7, _S5, _S6)))).z;

#line 52
    vec3 _S8 = (scene_0 + Tint.xyz * Strength.z * (exp(_S4 * 9.0) * (0.77999997138977051 + 0.2199999988079071 * sin(Params.z * 17.0))) * 0.34000000357627869) * (1.0 - Strength.w * smoothstep(0.18000000715255737, 0.77999997138977051, distanceToCenter_0) * 0.41999998688697815) + Tint.xyz * Strength.w * lens_0 * 0.03500000014901161;

#line 52
    scene_0 = _S8;
    vec4 _S9 = vec4(_S8, 1.0);

#line 53
    _S3 = _S9;

#line 53
    entryPointParam_main_fragColor_0 = _S9;

#line 53
    return;
}

