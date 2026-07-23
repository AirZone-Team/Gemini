#version 330 core
#line 10 0
struct SLANG_ParameterGroup_SweepUniforms_0
{
    vec4 Params;
    vec4 Geometry;
    vec4 Style;
    vec4 Motion;
    vec4 Primary;
    vec4 Accent;
    vec4 Core;
    vec4 Misc;
};


#line 10
layout(std140) uniform SweepUniforms
{
    vec4 Params;
    vec4 Geometry;
    vec4 Style;
    vec4 Motion;
    vec4 Primary;
    vec4 Accent;
    vec4 Core;
    vec4 Misc;
};

#line 3
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

#line 27
float hash_0(vec2 p_0)
{

#line 28
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise2_0(vec2 p_1)
{

#line 32
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);
    float _S2 = f_0.x;

#line 35
    return mix(mix(hash_0(i_0), hash_0(i_0 + vec2(1.0, 0.0)), _S2), mix(hash_0(i_0 + vec2(0.0, 1.0)), hash_0(i_0 + vec2(1.0)), _S2), f_0.y);
}


#line 54
float arcCenter_0(float u_0, int style_0)
{

#line 55
    if(style_0 == 1)
    {

#line 55
        return 0.57999998331069946 + 0.12999999523162842 * sin(u_0 * 3.14159274101257324);
    }

#line 56
    if(style_0 == 2)
    {

#line 56
        return 0.63999998569488525 + 0.04500000178813934 * sin(u_0 * 6.28318548202514648 * 4.0);
    }

#line 57
    if(style_0 == 3)
    {
        return 0.61000001430511475 + (noise2_0(vec2(u_0 * 14.0 - Params.x * Motion.x, Misc.w)) - 0.5) * 0.18000000715255737 * Style.w;
    }
    if(style_0 == 4)
    {
        return 0.63999998569488525 + (hash_0(vec2(floor(u_0 * 18.0), Misc.w)) - 0.5) * 0.2800000011920929 * max(0.34999999403953552, Style.w);
    }
    return 0.75999999046325684 - 0.20000000298023224 * sin(u_0 * 3.14159274101257324);
}


#line 39
vec3 spectrum_0(float t_0)
{

#line 40
    return 0.56000000238418579 + 0.43999999761581421 * cos(6.28318548202514648 * (t_0 + vec3(0.0, 0.68000000715255737, 0.34999999403953552)));
}

vec3 palette_0(float u_1, float phase_0)
{

#line 44
    int mode_0 = int(Style.y + 0.5);
    if(mode_0 == 1)
    {

#line 45
        return mix(Primary.xyz, Accent.xyz, smoothstep(0.05000000074505806, 0.94999998807907104, u_1));
    }

#line 46
    if(mode_0 == 2)
    {

#line 46
        return spectrum_0(u_1 * 0.77999997138977051 - Params.x * Motion.x * 0.07999999821186066 + phase_0);
    }

#line 47
    if(mode_0 == 3)
    {
        return mix(Primary.xyz, Accent.xyz, 0.5 + 0.5 * sin(Params.x * Motion.x * 4.0 + u_1 * 6.28318548202514648 * 2.0));
    }
    return Primary.xyz;
}


#line 157
in vec2 gemini_varying_0;


#line 157
in vec4 gemini_varying_1;


#line 68
vec4 arcMode_0()
{

#line 69
    float u_2 = gemini_varying_0.x;
    float v_0 = gemini_varying_0.y;
    int style_1 = int(Style.x + 0.5);
    int _S3 = clamp(int(Style.z + 0.5), 1, 5);
    float center_0 = arcCenter_0(u_2, style_1);
    float _S4 = mix(0.01799999922513962, 0.15999999642372131, clamp(Geometry.y / 2.5, 0.0, 1.0));


    const vec3 _S5 = vec3(0.0);

#line 77
    float coreEnergy_0 = 0.0;

#line 77
    int i_1 = 0;

#line 77
    vec3 color_0 = _S5;

#line 77
    float energy_0 = 0.0;

    for(;;)
    {

#line 79
        if(i_1 < 5)
        {
        }
        else
        {

#line 79
            break;
        }

#line 80
        if(i_1 >= _S3)
        {

#line 80
            break;
        }

#line 81
        float fi_0 = float(i_1);


        float band_0 = exp(- pow(abs(v_0 - (center_0 - fi_0 * _S4 * 0.72000002861022949)) / max(_S4 * (0.41999998688697815 + fi_0 * 0.36000001430511475), 0.00100000004749745), 1.45000004768371582 + fi_0 * 0.2199999988079071));
        float layerStrength_0 = 1.0 / (1.0 + fi_0 * 0.62000000476837158);

        vec3 color_1 = color_0 + mix(palette_0(u_2, fi_0 * 0.07999999821186066), Accent.xyz, fi_0 / 7.0) * band_0 * layerStrength_0;
        float energy_1 = energy_0 + band_0 * layerStrength_0;
        if(i_1 == 0)
        {

#line 89
            coreEnergy_0 = band_0;

#line 89
        }

#line 79
        i_1 = i_1 + 1;

#line 79
        color_0 = color_1;

#line 79
        energy_0 = energy_1;

#line 79
    }

#line 92
    if(style_1 == 2)
    {

#line 93
        float cells_0 = step(0.51999998092651367, hash_0(vec2(floor(u_2 * 34.0), floor(v_0 * 15.0) + Misc.w)));
        float runeRail_0 = exp(- abs(v_0 - center_0 + _S4 * 1.64999997615814209) * 80.0);

        vec3 color_2 = color_0 + Accent.xyz * runeRail_0 * cells_0 * 1.39999997615814209;

#line 96
        energy_0 = energy_0 + runeRail_0 * cells_0 * 0.75;

#line 96
        color_0 = color_2;

#line 92
    }
    else
    {


        if(style_1 == 3)
        {

#line 98
            float plasma_0 = noise2_0(vec2(u_2 * 24.0 - Params.x * Motion.x * 2.20000004768371582, v_0 * 11.0 + Misc.w));


            vec3 color_3 = color_0 * (0.72000002861022949 + plasma_0 * 1.14999997615814209);

#line 101
            energy_0 = energy_0 * (0.62000000476837158 + plasma_0 * 0.89999997615814209);

#line 101
            color_0 = color_3;

#line 97
        }
        else
        {


            if(style_1 == 4)
            {

#line 103
                float shardMask_0 = smoothstep(0.30000001192092896, 0.77999997138977051, hash_0(vec2(floor(u_2 * 23.0), floor(v_0 * 13.0) + Misc.w)));


                vec3 color_4 = color_0 + Core.xyz * shardMask_0 * coreEnergy_0 * 0.80000001192092896;

#line 106
                energy_0 = energy_0 * (0.64999997615814209 + shardMask_0);

#line 106
                color_0 = color_4;

#line 102
            }

#line 97
        }

#line 92
    }

#line 114
    float _S6 = smoothstep(0.0, 0.04500000178813934, u_2) * smoothstep(0.0, 0.07999999821186066, 1.0 - u_2) * (0.72000002861022949 + 0.2800000011920929 * sin(u_2 * 34.0 - Params.x * Motion.x * 7.0)) * mix(1.0, 0.57999998331069946 + noise2_0(vec2(u_2 * 19.0 - Params.x * Motion.x * 1.79999995231628418, v_0 * 8.0 + Misc.w)), clamp(Style.w, 0.0, 1.0));

#line 120
    return vec4((color_0 * _S6 + Core.xyz * coreEnergy_0 * coreEnergy_0 * 1.79999995231628418) * (1.0 + Geometry.w * (0.72000002861022949 + coreEnergy_0 * 1.79999995231628418)) * gemini_varying_1.x, clamp(energy_0 * _S6, 0.0, 1.0) * gemini_varying_1.w * Primary.w);
}

vec4 ringMode_0()
{

#line 124
    vec2 p_2 = (gemini_varying_0 - 0.5) * 2.0;
    float distanceToCenter_0 = length(p_2);

    float _S7 = - abs(distanceToCenter_0 - 0.73500001430511475);

#line 127
    float ring_0 = exp(_S7 / max(mix(0.01799999922513962, 0.15000000596046448, clamp(Motion.z, 0.0, 1.0)), 0.00400000018998981));

    float _S8 = (atan((p_2.y),(p_2.x)));


    float _S9 = ring_0 + exp(_S7 * 7.0) * Geometry.w * 0.2199999988079071;

#line 132
    float energy_2 = _S9 + pow(max(0.0, sin(_S8 * 12.0 - Params.x * Motion.x * 2.0)), 18.0) * exp(- abs(distanceToCenter_0 - 0.57999998331069946) * 8.0) * 0.34999999403953552;


    return vec4((palette_0(_S8 / 6.28318548202514648 + 0.5, gemini_varying_1.x) * energy_2 + Core.xyz * ring_0 * ring_0 * 1.60000002384185791) * (1.0 + Geometry.w * _S9), clamp(energy_2, 0.0, 1.0) * gemini_varying_1.w * Primary.w);
}

vec4 burstMode_0()
{

#line 139
    vec2 p_3 = (gemini_varying_0 - 0.5) * 2.0;
    float distanceToCenter_1 = length(p_3);
    float _S10 = (atan((p_3.y),(p_3.x)));


    float _S11 = - distanceToCenter_1;
    float core_0 = exp(_S11 * distanceToCenter_1 * 18.0);

    float energy_3 = ((pow(abs(cos(_S10 * 4.0)), 28.0) + pow(abs(cos(_S10 * 7.0 + 0.55000001192092896)), 42.0) * 0.64999997615814209) * exp(_S11 * 2.59999990463256836) + core_0 + exp(- abs(distanceToCenter_1 - 0.31999999284744263) * 13.0) * 0.31999999284744263) * (1.0 - smoothstep(0.81999999284744263, 1.0, distanceToCenter_1));


    return vec4(mix(Accent.xyz, Core.xyz, clamp(core_0 * 1.60000002384185791, 0.0, 1.0)) * (energy_3 * (1.39999997615814209 + Geometry.w * 1.79999995231628418)), clamp(energy_3, 0.0, 1.0) * gemini_varying_1.w * Primary.w);
}


#line 23
vec4 _S12;


#line 23
out vec4 entryPointParam_main_fragColor_0;


#line 155
void main()
{

#line 155
    vec4 result_0;

    if((gemini_varying_1.y) < 0.5)
    {

#line 157
        result_0 = arcMode_0();

#line 157
    }
    else
    {

#line 158
        if((gemini_varying_1.z) < 0.5)
        {

#line 158
            result_0 = ringMode_0();

#line 158
        }
        else
        {

#line 158
            result_0 = burstMode_0();

#line 158
        }

#line 157
    }


    vec4 _S13 = result_0 * ColorModulator;

#line 160
    _S12 = _S13;
    if((_S13.w) < 0.0020000000949949)
    {

#line 161
        discard;

#line 161
    }

#line 161
    entryPointParam_main_fragColor_0 = _S12;

#line 161
    return;
}

