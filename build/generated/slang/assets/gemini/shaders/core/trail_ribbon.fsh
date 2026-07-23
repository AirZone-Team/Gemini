#version 330 core
#line 7 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 7
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

float hash21_0(vec2 p_0)
{

#line 17
    vec2 _S1 = fract(p_0 * vec2(123.339996337890625, 456.209991455078125));
    vec2 _S2 = _S1 + dot(_S1, _S1 + 45.31999969482421875);
    return fract(_S2.x * _S2.y);
}

float noise21_0(vec2 p_1)
{

#line 23
    vec2 i_0 = floor(p_1);
    vec2 _S3 = fract(p_1);
    vec2 f_0 = _S3 * _S3 * (3.0 - 2.0 * _S3);
    float _S4 = f_0.x;

#line 26
    return mix(mix(hash21_0(i_0), hash21_0(i_0 + vec2(1.0, 0.0)), _S4), mix(hash21_0(i_0 + vec2(0.0, 1.0)), hash21_0(i_0 + vec2(1.0)), _S4), f_0.y);
}


float fbm_0(vec2 p_2, int octaves_0)
{

#line 30
    vec2 _S5 = p_2;

#line 30
    int i_1 = 0;

#line 30
    float amplitude_0 = 0.51999998092651367;

#line 30
    float result_0 = 0.0;


    for(;;)
    {

#line 33
        if(i_1 < 5)
        {
        }
        else
        {

#line 33
            break;
        }

#line 34
        if(i_1 >= octaves_0)
        {

#line 34
            break;
        }

#line 35
        float result_1 = result_0 + noise21_0(_S5) * amplitude_0;
        vec2 _S6 = (((mat2x2(1.60000002384185791, -1.20000004768371582, 1.20000004768371582, 1.60000002384185791)) * (_S5))) + 0.17000000178813934;
        float amplitude_1 = amplitude_0 * 0.47999998927116394;

#line 33
        int _S7 = i_1 + 1;

#line 33
        _S5 = _S6;

#line 33
        i_1 = _S7;

#line 33
        amplitude_0 = amplitude_1;

#line 33
        result_0 = result_1;

#line 33
    }

#line 39
    return result_0;
}


#line 46
float lineMask_0(float value_0, float width_0)
{

#line 47
    return 1.0 - smoothstep(width_0, width_0 + 0.05499999970197678, abs(value_0));
}


#line 42
vec3 spectrum_0(float hue_0)
{

#line 43
    return 0.51999998092651367 + 0.47999998927116394 * cos(6.28318548202514648 * (hue_0 + vec3(0.0, 0.33000001311302185, 0.67000001668930054)));
}


#line 5
vec4 _S8;


#line 5
out vec4 entryPointParam_main_fragColor_0;


#line 5
in vec4 gemini_varying_1;


#line 5
in vec2 gemini_varying_0;


#line 52
void main()
{

#line 53
    float time_0 = ModelOffset.x;
    int quality_0 = int(ModelOffset.y + 0.5);
    vec3 primary_0 = ColorModulator.xyz;
    vec3 secondary_0 = TextureMat[0].xyz;
    float glowAmount_0 = TextureMat[0].w;
    vec3 accent_0 = TextureMat[1].xyz;
    float coreWidth_0 = TextureMat[1].w;
    int style_0 = int(TextureMat[2].x + 0.5);
    int colorMode_0 = int(TextureMat[2].y + 0.5);
    float flowSpeed_0 = TextureMat[2].z;
    float detailScale_0 = TextureMat[2].w;
    float distortion_0 = TextureMat[3].x;
    float sparkleDensity_0 = TextureMat[3].y;
    float edgeGlowAmount_0 = TextureMat[3].z;
    float pulseStrength_0 = TextureMat[3].w;

    float age_0 = gemini_varying_1.x;
    float motion_0 = gemini_varying_1.y;
    float brightness_0 = gemini_varying_1.z * 3.0;
    float opacity_0 = gemini_varying_1.w;
    if(opacity_0 < 0.0020000000949949)
    {

#line 73
        discard;

#line 73
    }

    float u_0 = gemini_varying_0.x;
    float v_0 = gemini_varying_0.y;
    float av_0 = abs(v_0);
    float flowTime_0 = time_0 * flowSpeed_0;

#line 78
    int octaves_1;
    if(quality_0 <= 0)
    {

#line 79
        octaves_1 = 2;

#line 79
    }
    else
    {

#line 79
        if(quality_0 == 1)
        {

#line 79
            octaves_1 = 3;

#line 79
        }
        else
        {

#line 79
            if(quality_0 == 2)
            {

#line 79
                octaves_1 = 4;

#line 79
            }
            else
            {

#line 79
                octaves_1 = 5;

#line 79
            }

#line 79
        }

#line 79
    }

    vec2 flowUv_0 = vec2(u_0 * 7.0 * detailScale_0 - flowTime_0 * 1.79999995231628418, v_0 * 2.79999995231628418 * detailScale_0);

    float broadNoise_0 = fbm_0(flowUv_0, octaves_1);
    float warp_0 = (broadNoise_0 - 0.5) * distortion_0;

#line 84
    float pattern_0;

#line 84
    float auraPattern_0;



    if(style_0 == 1)
    {

#line 88
        pattern_0 = 0.25 + pow(max(0.0, sin(u_0 * 17.0 - flowTime_0 * 4.0 + v_0 * 5.5 + warp_0 * 5.0) * sin(u_0 * 13.0 - flowTime_0 * 3.0 - v_0 * 6.5 - warp_0 * 4.0)), 2.0) * 0.85000002384185791 + lineMask_0(fract(u_0 * 14.0 - flowTime_0) - 0.5, 0.07999999821186066) * step(0.72000002861022949, hash21_0(vec2(floor(u_0 * 28.0 - flowTime_0 * 2.0), floor(v_0 * 4.0)))) * 1.20000004768371582;

#line 88
        auraPattern_0 = broadNoise_0;

#line 88
    }
    else
    {

#line 97
        if(style_0 == 2)
        {
            float _S9 = u_0 * 32.0 - flowTime_0 * 5.0;

#line 99
            float _S10 = v_0 * 5.0;

#line 99
            vec2 grid_0 = abs(fract(vec2(_S9, _S10)) - 0.5);

            float blocks_0 = (1.0 - smoothstep(0.31999999284744263, 0.47999998927116394, max(grid_0.x, grid_0.y))) * step(0.27000001072883606, hash21_0(vec2(floor(_S9), floor(_S10))));

#line 101
            pattern_0 = 0.18000000715255737 + blocks_0 * 1.20000004768371582 + pow(0.5 + 0.5 * sin(u_0 * 80.0 - flowTime_0 * 12.0), 10.0) * 0.80000001192092896;

#line 101
            auraPattern_0 = blocks_0;

#line 97
        }
        else
        {

#line 105
            if(style_0 == 3)
            {
                float flame_0 = fbm_0(vec2(u_0 * 5.5 - flowTime_0 * 3.0, v_0 * 4.0 + broadNoise_0 * 2.0), octaves_1);

#line 107
                pattern_0 = 0.20000000298023224 + smoothstep(0.25999999046325684 + av_0 * 0.23999999463558197, 0.89999997615814209, flame_0 + (1.0 - av_0) * 0.34000000357627869) * 1.45000004768371582 + pow(hash21_0(floor(vec2(u_0 * 90.0 - flowTime_0 * 13.0, v_0 * 14.0))), 18.0);

#line 107
                auraPattern_0 = flame_0;

#line 105
            }
            else
            {

#line 112
                if(style_0 == 4)
                {
                    float voidCloud_0 = fbm_0(flowUv_0 * 0.55000001192092896 + broadNoise_0, octaves_1);
                    float stars_0 = pow(hash21_0(floor(vec2(u_0 * 105.0 - flowTime_0 * 2.0, v_0 * 17.0))), 24.0);

                    float _S11 = stars_0 + voidCloud_0 * 0.25;

#line 117
                    pattern_0 = 0.07999999821186066 + voidCloud_0 * 0.2800000011920929 + stars_0 * 2.20000004768371582;

#line 117
                    auraPattern_0 = _S11;

#line 112
                }
                else
                {

#line 112
                    pattern_0 = 0.2199999988079071 + pow(0.5 + 0.5 * sin(u_0 * 18.0 - flowTime_0 * 3.0 + v_0 * 5.0 + warp_0 * 8.0), 2.20000004768371582) * 0.77999997138977051 + pow(0.5 + 0.5 * sin(u_0 * 31.0 - flowTime_0 * 5.0 - v_0 * 3.0), 5.0) * 0.44999998807907104;

#line 112
                    auraPattern_0 = broadNoise_0;

#line 112
                }

#line 105
            }

#line 97
        }

#line 88
    }

#line 126
    float pulse_0 = 0.72000002861022949 + 0.2800000011920929 * sin(flowTime_0 * 4.0 - u_0 * 12.0);

#line 126
    vec3 color_0;

    if(colorMode_0 == 1)
    {

#line 128
        color_0 = spectrum_0(time_0 * 0.09000000357627869 * flowSpeed_0 + u_0 * 0.94999998807907104 + broadNoise_0 * 0.15999999642372131);

#line 128
    }
    else
    {

#line 130
        if(colorMode_0 == 2)
        {

#line 130
            color_0 = mix(primary_0, secondary_0, 0.5 + 0.5 * sin(flowTime_0 * 4.5 - u_0 * 10.0));

#line 130
        }
        else
        {
            if(colorMode_0 == 3)
            {

#line 133
                color_0 = primary_0;

#line 133
            }
            else
            {

#line 133
                color_0 = mix(primary_0, secondary_0, smoothstep(0.05000000074505806, 0.94999998807907104, u_0 + warp_0 * 0.18000000715255737));

#line 133
            }

#line 130
        }

#line 128
    }

#line 139
    float _S12 = - av_0;

#line 139
    float core_0 = exp(_S12 / max(0.02500000037252903, coreWidth_0)) * pattern_0;
    float softAura_0 = exp(_S12 * mix(1.39999997615814209, 4.5, clamp(glowAmount_0 / 2.5, 0.0, 1.0)));
    float edge_0 = exp(- abs(av_0 - 0.81999999284744263) * 13.0) * edgeGlowAmount_0;

    float sparkle_0 = pow(hash21_0(floor(vec2(u_0 * 120.0 - flowTime_0 * 9.0, v_0 * 18.0))), 28.0) * sparkleDensity_0 * (0.40000000596046448 + 0.60000002384185791 * motion_0);

#line 148
    float fade_0 = (1.0 - smoothstep(0.37999999523162842, 1.0, age_0)) * (1.0 - smoothstep(0.77999997138977051, 1.0, u_0)) * smoothstep(0.0, 0.02500000037252903, u_0 + 0.01200000010430813);

    float _S13 = mix(1.0, max(0.05000000074505806, pulse_0), clamp(pulseStrength_0, 0.0, 1.5));
    vec3 body_0 = color_0 * (core_0 * 1.35000002384185791 + softAura_0 * (0.11999999731779099 + auraPattern_0 * 0.18000000715255737) * glowAmount_0);
    vec3 ornament_0 = accent_0 * (edge_0 * 0.75 + sparkle_0 * 1.79999995231628418);

#line 152
    vec3 body_1;
    if(style_0 == 4)
    {

#line 153
        body_1 = body_0 * 0.41999998688697815;

#line 153
    }
    else
    {

#line 153
        body_1 = body_0;

#line 153
    }

#line 160
    vec4 _S14 = vec4((body_1 * _S13 + ornament_0) * brightness_0 * (1.0 + glowAmount_0 * 0.34999999403953552 + motion_0 * 0.44999998807907104) * fade_0, opacity_0 * clamp((core_0 + softAura_0 * 0.41999998688697815 + edge_0 * 0.40000000596046448 + sparkle_0) * fade_0, 0.0, 1.0));

#line 160
    _S8 = _S14;

#line 160
    entryPointParam_main_fragColor_0 = _S14;

#line 160
    return;
}

