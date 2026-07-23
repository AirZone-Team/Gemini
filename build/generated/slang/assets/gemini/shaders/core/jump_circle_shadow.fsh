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

#line 16
float hash21_0(vec2 p_0)
{

#line 17
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise_0(vec2 p_1)
{

#line 21
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);

    float _S2 = f_0.x;

#line 24
    return mix(mix(hash21_0(i_0), hash21_0(i_0 + vec2(1.0, 0.0)), _S2), mix(hash21_0(i_0 + vec2(0.0, 1.0)), hash21_0(i_0 + vec2(1.0, 1.0)), _S2), f_0.y);
}


#line 14
vec4 _S3;


#line 14
out vec4 entryPointParam_main_fragColor_0;


#line 34
flat in ivec2 gemini_varying_2;


#line 34
in vec2 gemini_varying_1;


#line 34
in vec4 gemini_varying_0;


#line 33
void main()
{
    vec2 p_2 = gemini_varying_1 * 2.0 - 1.0;
    float distanceValue_0 = length(p_2);
    float life_0 = clamp(gemini_varying_0.w, 0.0, 1.0);


    float _S4 = mix(0.11999999731779099, 0.81999999284744263, 1.0 - pow(1.0 - life_0, 3.0));

#line 47
    vec4 _S5 = vec4(gemini_varying_0.xyz, ((1.0 - smoothstep(_S4 * 0.20000000298023224, _S4 + 0.20000000298023224, distanceValue_0)) * 0.54000002145767212 + (1.0 - smoothstep(0.05999999865889549, 0.18999999761581421, abs(distanceValue_0 - _S4 * 0.75999999046325684))) * 0.46000000834465027) * (0.77999997138977051 + noise_0(p_2 * 7.0 + life_0 * 2.40000009536743164) * 0.2199999988079071) * (smoothstep(0.0, 0.03999999910593033, life_0) * (1.0 - smoothstep(0.51999998092651367, 1.0, life_0))) * clamp(float((gemini_varying_2.x) & 65535) / 255.0, 0.0, 1.0)) * ColorModulator;

#line 47
    _S3 = _S5;
    if((_S5.w) < 0.00100000004749745)
    {

#line 48
        discard;

#line 48
    }

#line 48
    entryPointParam_main_fragColor_0 = _S3;

#line 48
    return;
}

