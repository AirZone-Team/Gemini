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

#line 19
uint unpackWords_0(ivec2 words_0)
{

    return uint((words_0.x) & 65535) | (uint((words_0.y) & 65535) << 16U);
}


#line 59
float easeMotion_0(float t_0, int mode_0)
{

#line 60
    if(mode_0 == 0)
    {

#line 60
        return t_0;
    }

#line 61
    if(mode_0 == 2)
    {

#line 61
        bool _S1;
        if(t_0 <= 0.0)
        {

#line 62
            _S1 = true;

#line 62
        }
        else
        {

#line 62
            _S1 = t_0 >= 1.0;

#line 62
        }

#line 62
        if(_S1)
        {

#line 62
            return t_0;
        }

#line 63
        return clamp(pow(2.0, -10.0 * t_0) * sin((t_0 * 10.0 - 0.75) * 6.28318548202514648 / 3.0) + 1.0, 0.0, 1.0);
    }
    float _S2 = 1.0 - t_0;

#line 65
    float outCubic_0 = 1.0 - pow(_S2, 3.0);

#line 65
    float outCubic_1;
    if(mode_0 == 3)
    {

#line 66
        outCubic_1 = outCubic_0 + sin(t_0 * 3.14159274101257324 * 3.0) * _S2 * 0.07999999821186066;

#line 66
    }
    else
    {

#line 66
        outCubic_1 = outCubic_0;

#line 66
    }


    return clamp(outCubic_1, 0.0, 1.0);
}


#line 29
float hash21_0(vec2 p_0)
{

#line 30
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float valueNoise_0(vec2 p_1)
{

#line 34
    vec2 i_0 = floor(p_1);
    vec2 _S3 = fract(p_1);
    vec2 f_0 = _S3 * _S3 * (3.0 - 2.0 * _S3);

    float _S4 = f_0.x;

#line 37
    return mix(mix(hash21_0(i_0), hash21_0(i_0 + vec2(1.0, 0.0)), _S4), mix(hash21_0(i_0 + vec2(0.0, 1.0)), hash21_0(i_0 + vec2(1.0, 1.0)), _S4), f_0.y);
}


#line 44
float fbm_0(vec2 p_2)
{

#line 44
    vec2 _S5 = p_2;

#line 44
    int i_1 = 0;

#line 44
    float amplitude_0 = 0.5;

#line 44
    float sum_0 = 0.0;


    for(;;)
    {

#line 47
        if(i_1 < 4)
        {
        }
        else
        {

#line 47
            break;
        }

#line 48
        float sum_1 = sum_0 + valueNoise_0(_S5) * amplitude_0;
        vec2 _S6 = _S5 * 2.06999993324279785 + 3.17000007629394531;
        float amplitude_1 = amplitude_0 * 0.5;

#line 47
        int _S7 = i_1 + 1;

#line 47
        _S5 = _S6;

#line 47
        i_1 = _S7;

#line 47
        amplitude_0 = amplitude_1;

#line 47
        sum_0 = sum_1;

#line 47
    }

#line 52
    return sum_0;
}


#line 25
float hash11_0(float p_3)
{

#line 26
    return fract(sin(p_3 * 127.09999847412109375) * 43758.546875);
}


#line 55
float ringMask_0(float distanceValue_0, float center_0, float halfWidth_0, float softness_0)
{

#line 56
    return 1.0 - smoothstep(halfWidth_0, halfWidth_0 + softness_0, abs(distanceValue_0 - center_0));
}


#line 72
vec3 flowColor_0(vec3 base_0, int mode_1, float theta_0, float distanceValue_1, float life_0)
{

#line 73
    if(mode_1 == 1)
    {
        return mix(base_0, mix(base_0.zxy, vec3(1.0), 0.11999999731779099), 0.2199999988079071 + 0.2800000011920929 * sin(theta_0 * 2.0 + life_0 * 4.0));
    }
    if(mode_1 == 2)
    {
        return 0.57999998331069946 + 0.41999998688697815 * cos(6.28318548202514648 * (theta_0 / 6.28318548202514648 + life_0 * 0.23999999463558197 + distanceValue_1 * 0.11999999731779099 + vec3(0.0, 0.33000001311302185, 0.67000001668930054)));
    }
    if(mode_1 == 3)
    {
        return mix(base_0 * 0.77999997138977051, mix(base_0, vec3(1.0), 0.51999998092651367), 0.5 + 0.5 * sin(life_0 * 19.0 - distanceValue_1 * 14.0));
    }
    return base_0;
}


#line 14
vec4 _S8;


#line 14
out vec4 entryPointParam_main_fragColor_0;


#line 14
flat in ivec2 gemini_varying_2;


#line 14
flat in ivec2 gemini_varying_3;


#line 14
in vec2 gemini_varying_1;


#line 14
in vec4 gemini_varying_0;


#line 90
void main()
{

#line 90
    float ornaments_0;
    uint styleBits_0 = unpackWords_0(gemini_varying_2);
    uint materialBits_0 = unpackWords_0(gemini_varying_3);
    vec2 p_4 = gemini_varying_1 * 2.0 - 1.0;
    float distanceValue_2 = length(p_4);
    float _S9 = (atan((p_4.y),(p_4.x)));


    int style_0 = int(styleBits_0 & 7U);
    int colorMode_0 = int((styleBits_0 >> 3U) & 3U);
    int _S10 = int((styleBits_0 >> 5U) & 3U) + 1;
    int _S11 = int((styleBits_0 >> 7U) & 3U) + 1;
    int quality_0 = int((styleBits_0 >> 9U) & 3U);
    int runeDetail_0 = int((styleBits_0 >> 11U) & 3U);
    int particleDensity_0 = int((styleBits_0 >> 13U) & 3U);
    bool spikesEnabled_0 = ((styleBits_0 >> 15U) & 1U) != 0U;
    int eventType_0 = int((styleBits_0 >> 16U) & 3U);
    bool accentLayer_0 = ((styleBits_0 >> 18U) & 1U) != 0U;
    bool shockwaveEnabled_0 = ((styleBits_0 >> 19U) & 1U) != 0U;



    float _S12 = mix(0.0, 2.5, float((materialBits_0 >> 4U) & 15U) / 15.0);
    float _S13 = mix(0.0, 1.5, float((materialBits_0 >> 8U) & 15U) / 15.0);
    float _S14 = mix(-3.0, 3.0, float((materialBits_0 >> 12U) & 15U) / 15.0);
    float _S15 = mix(0.05000000074505806, 1.0, float((materialBits_0 >> 16U) & 7U) / 7.0);
    float _S16 = mix(0.25, 2.5, float((materialBits_0 >> 19U) & 7U) / 7.0);

    float life_1 = clamp(gemini_varying_0.w, 0.0, 1.0);

    float fade_0 = smoothstep(0.0, 0.02500000037252903, life_1) * (1.0 - smoothstep(0.57999998331069946, 1.0, life_1));
    float impactFlash_0 = 1.0 - smoothstep(0.0, 0.12999999523162842, life_1);
    float _S17 = mix(0.11999999731779099, 0.79000002145767212, easeMotion_0(life_1, int((styleBits_0 >> 20U) & 3U)));
    float _S18 = 0.01799999922513962 + mix(0.15000000596046448, 2.5, float(materialBits_0 & 15U) / 15.0) * 0.03500000014901161;

#line 123
    float styleGate_0;

#line 123
    if(accentLayer_0)
    {

#line 123
        styleGate_0 = 0.47999998927116394;

#line 123
    }
    else
    {

#line 123
        styleGate_0 = 1.0;

#line 123
    }

#line 123
    float width_0 = _S18 * styleGate_0;
    float _S19 = mix(0.00600000005215406, 0.01799999922513962, 1.0 - float(quality_0) / 3.0);

    float _S20 = life_1 * _S14;

#line 126
    float noiseField_0 = fbm_0(p_4 * mix(3.0, 8.0, _S13 / 1.5) + vec2(_S20 * 2.0));
    float warpedDistance_0 = distanceValue_2 + (noiseField_0 - 0.5) * _S13 * 0.05499999970197678 * (0.40000000596046448 + life_1);
    float rotation_0 = _S9 + _S20 * 6.28318548202514648 * 0.41999998688697815;
    float _S21 = fract(rotation_0 / 6.28318548202514648 + 1.0);


    if(style_0 == 0)
    {

#line 133
        float _S22 = fract(_S21 * 12.0);

#line 133
        styleGate_0 = 0.55000001192092896 + 0.44999998807907104 * smoothstep(0.05000000074505806, 0.15999999642372131, _S22) * (1.0 - smoothstep(0.81999999284744263, 0.95999997854232788, _S22));

#line 132
    }
    else
    {
        if(style_0 == 1)
        {

#line 136
            float _S23 = fract(_S21 * 24.0 + floor(distanceValue_2 * 24.0) * 0.17000000178813934);

#line 136
            styleGate_0 = step(0.18000000715255737, _S23) * (1.0 - step(0.82999998331069946, _S23));

#line 135
        }
        else
        {
            if(style_0 == 2)
            {

#line 138
                styleGate_0 = 0.77999997138977051 + 0.2199999988079071 * sin(rotation_0 * 5.0 + life_1 * 7.0);

#line 138
            }
            else
            {

#line 140
                if(style_0 == 3)
                {

#line 140
                    styleGate_0 = 0.55000001192092896 + 0.69999998807907104 * noiseField_0;

#line 140
                }
                else
                {

#line 140
                    styleGate_0 = smoothstep(0.25999999046325684, 0.62000000476837158, hash11_0(floor(_S21 * 30.0) + floor(life_1 * 9.0) * 0.07000000029802322)) * (0.72000002861022949 + noiseField_0 * 0.40000000596046448);

#line 140
                }

#line 138
            }

#line 135
        }

#line 132
    }

#line 132
    int layer_0 = 0;

#line 132
    float core_0 = 0.0;

#line 132
    float halo_0 = 0.0;

#line 149
    for(;;)
    {

#line 149
        if(layer_0 < 4)
        {
        }
        else
        {

#line 149
            break;
        }

#line 150
        if(layer_0 >= _S10)
        {

#line 150
            break;
        }

#line 151
        float layerF_0 = float(layer_0);
        float trailCenter_0 = _S17 - layerF_0 * (0.03500000014901161 + life_1 * 0.01799999922513962);
        float layerWidth_0 = width_0 * mix(1.0, 0.41999998688697815, layerF_0 / 3.0);
        float layerAlpha_0 = 1.0 - layerF_0 / (float(_S10) + 0.64999997615814209);

        float core_1 = core_0 + ringMask_0(warpedDistance_0, trailCenter_0, layerWidth_0, _S19) * layerAlpha_0 * styleGate_0;
        float halo_1 = halo_0 + ringMask_0(warpedDistance_0, trailCenter_0, layerWidth_0 * (2.20000004768371582 + _S12), 0.03999999910593033) * layerAlpha_0;

#line 149
        layer_0 = layer_0 + 1;

#line 149
        core_0 = core_1;

#line 149
        halo_0 = halo_1;

#line 149
    }

#line 149
    int ringIndex_0 = 1;

#line 160
    for(;;)
    {

#line 160
        if(ringIndex_0 < 4)
        {
        }
        else
        {

#line 160
            break;
        }

#line 161
        if(ringIndex_0 >= _S11)
        {

#line 161
            break;
        }

#line 162
        float indexF_0 = float(ringIndex_0);

        float satellite_0 = ringMask_0(warpedDistance_0, max(0.07999999821186066, _S17 * (1.0 - indexF_0 * 0.17000000178813934)), width_0 * 0.47999998927116394, _S19);
        if(style_0 == 2)
        {

#line 165
            ornaments_0 = 1.0;

#line 165
        }
        else
        {

#line 165
            ornaments_0 = step(0.20000000298023224, fract(_S21 * (8.0 + indexF_0 * 5.0)));

#line 165
        }
        float core_2 = core_0 + satellite_0 * ornaments_0 * (0.51999998092651367 / indexF_0);
        float halo_2 = halo_0 + satellite_0 * 0.25;

#line 160
        ringIndex_0 = ringIndex_0 + 1;

#line 160
        core_0 = core_2;

#line 160
        halo_0 = halo_2;

#line 160
    }

#line 171
    if(runeDetail_0 > 0)
    {

#line 172
        float _S24 = float(runeDetail_0);
        float _S25 = _S21 * (12.0 + _S24 * 8.0);

#line 173
        float _S26 = fract(_S25 + _S20 * 0.17000000178813934);

#line 178
        float _S27 = ringMask_0(distanceValue_2, max(0.15999999642372131, _S17 - 0.10499999672174454), 0.01799999922513962 + _S24 * 0.00400000018998981, 0.00800000037997961) * (step(0.14000000059604645, _S26) * (1.0 - step(0.31999999284744263 + hash11_0(floor(_S25)) * 0.41999998688697815, _S26))) + ringMask_0(distanceValue_2, max(0.11999999731779099, _S17 - 0.14499999582767487), 0.00800000037997961, 0.00600000005215406) * step(0.46000000834465027, _S26) * (1.0 - step(0.69999998807907104, _S26));

#line 178
        if(accentLayer_0)
        {

#line 178
            styleGate_0 = 1.35000002384185791;

#line 178
        }
        else
        {

#line 178
            styleGate_0 = 0.72000002861022949;

#line 178
        }

#line 178
        ornaments_0 = _S27 * styleGate_0;

#line 171
    }
    else
    {

#line 171
        ornaments_0 = 0.0;

#line 171
    }

#line 171
    float particles_0;

#line 181
    if(spikesEnabled_0)
    {

#line 182
        if(style_0 == 1)
        {

#line 182
            particles_0 = 20.0;

#line 182
        }
        else
        {

#line 182
            if(style_0 == 3)
            {

#line 182
                styleGate_0 = 28.0;

#line 182
            }
            else
            {

#line 182
                styleGate_0 = 16.0;

#line 182
            }

#line 182
            particles_0 = styleGate_0;

#line 182
        }


        float outer_0 = _S17 + pow(1.0 - abs(fract(_S21 * particles_0) - 0.5) * 2.0, 3.0) * (0.07999999821186066 + 0.10000000149011612 * impactFlash_0);

        float spikeBody_0 = smoothstep(_S17 - width_0, _S17, warpedDistance_0) * (1.0 - smoothstep(outer_0, outer_0 + 0.01200000010430813, warpedDistance_0));
        if(accentLayer_0)
        {

#line 188
            styleGate_0 = 1.0;

#line 188
        }
        else
        {

#line 188
            styleGate_0 = 0.47999998927116394;

#line 188
        }

#line 188
        ornaments_0 = ornaments_0 + spikeBody_0 * styleGate_0;

#line 181
    }
    else
    {

#line 181
    }

#line 192
    int _S28 = min(12, particleDensity_0 * 3 + quality_0);

#line 192
    int i_2 = 0;

#line 192
    particles_0 = 0.0;
    for(;;)
    {

#line 193
        if(i_2 < 12)
        {
        }
        else
        {

#line 193
            break;
        }

#line 194
        if(i_2 >= _S28)
        {

#line 194
            break;
        }

#line 195
        float fi_0 = float(i_2);

        float particleAngle_0 = hash11_0(fi_0 * 7.13000011444091797 + float(eventType_0) * 13.69999980926513672) * 6.28318548202514648 + _S20 * (0.55000001192092896 + hash11_0(fi_0 + 8.0));


        float size_0 = mix(0.00899999961256981, 0.0260000005364418, hash11_0(fi_0 * 4.71000003814697266)) * (1.14999997615814209 - life_1 * 0.34999999403953552);

        float particles_1 = particles_0 + (1.0 - smoothstep(size_0, size_0 * 2.70000004768371582, length(p_4 - vec2(cos(particleAngle_0), sin(particleAngle_0)) * (mix(0.15999999642372131, 0.92000001668930054, hash11_0(fi_0 * 19.39999961853027344 + 2.0)) * _S17 + life_1 * 0.09000000357627869)))) * (0.55000001192092896 + 0.44999998807907104 * sin(life_1 * 24.0 + fi_0 * 2.09999990463256836));

#line 193
        i_2 = i_2 + 1;

#line 193
        particles_0 = particles_1;

#line 193
    }

#line 193
    float shock_0;

#line 206
    if(shockwaveEnabled_0)
    {

#line 207
        float shock_1 = ringMask_0(distanceValue_2, min(0.98000001907348633, _S17 * 1.1799999475479126 + 0.03999999910593033), 0.00999999977648258 + impactFlash_0 * 0.01400000043213367, 0.02199999988079071);
        float _S29 = 0.31999999284744263 + impactFlash_0 * 0.77999997138977051;

#line 208
        if(accentLayer_0)
        {

#line 208
            styleGate_0 = 1.20000004768371582;

#line 208
        }
        else
        {

#line 208
            styleGate_0 = 0.69999998807907104;

#line 208
        }

#line 208
        shock_0 = shock_1 * (_S29 * styleGate_0);

#line 206
    }
    else
    {

#line 206
        shock_0 = 0.0;

#line 206
    }

#line 212
    float _S30 = (1.0 - smoothstep(0.0, 0.34000000357627869 + life_1 * 0.15999999642372131, distanceValue_2)) * impactFlash_0;

#line 212
    bool _S31 = eventType_0 == 2;

#line 212
    if(_S31)
    {

#line 212
        styleGate_0 = 0.60000002384185791;

#line 212
    }
    else
    {

#line 212
        styleGate_0 = 0.34000000357627869;

#line 212
    }
    float intensity_0 = core_0 + ornaments_0 + particles_0 + shock_0 + _S30 * styleGate_0;
    float glowField_0 = halo_0 * _S12 * 0.23999999463558197 + particles_0 * _S12 * 0.31999999284744263;
    float alpha_0 = clamp((intensity_0 + glowField_0) * fade_0 * _S15, 0.0, 1.0);

    vec3 color_0 = flowColor_0(gemini_varying_0.xyz, colorMode_0, _S9, distanceValue_2, life_1);
    if(_S31)
    {

#line 218
        styleGate_0 = 0.72000002861022949;

#line 218
    }
    else
    {

#line 218
        styleGate_0 = 0.44999998807907104;

#line 218
    }
    vec3 color_1 = mix(color_0, vec3(1.0), impactFlash_0 * styleGate_0);
    vec3 rgb_0 = color_1 * (intensity_0 * _S16 + glowField_0 * (0.75 + _S16 * 0.34999999403953552));

#line 220
    bool _S32;

    if(style_0 == 4)
    {

#line 222
        _S32 = !accentLayer_0;

#line 222
    }
    else
    {

#line 222
        _S32 = false;

#line 222
    }

#line 222
    vec3 rgb_1;

#line 222
    if(_S32)
    {

#line 222
        rgb_1 = rgb_0 * 0.6600000262260437 + color_1 * glowField_0 * 0.34999999403953552;

#line 222
    }
    else
    {

#line 222
        rgb_1 = rgb_0;

#line 222
    }

#line 227
    vec4 _S33 = vec4(rgb_1, alpha_0) * ColorModulator;

#line 227
    _S8 = _S33;
    if((_S33.w) < 0.00100000004749745)
    {

#line 228
        discard;

#line 228
    }

#line 228
    entryPointParam_main_fragColor_0 = _S8;

#line 228
    return;
}

