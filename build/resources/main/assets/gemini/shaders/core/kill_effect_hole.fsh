#version 330 core
#line 19 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 19
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 56
float easeOutExpo_0(float t_0)
{

#line 56
    float _S1;

#line 56
    if(t_0 >= 1.0)
    {

#line 56
        _S1 = 1.0;

#line 56
    }
    else
    {

#line 56
        _S1 = 1.0 - pow(2.0, -10.0 * t_0);

#line 56
    }

#line 56
    return _S1;
}


#line 57
float easeInExpo_0(float t_1)
{

#line 57
    float _S2;

#line 57
    if(t_1 <= 0.0)
    {

#line 57
        _S2 = 0.0;

#line 57
    }
    else
    {

#line 57
        _S2 = pow(2.0, 10.0 * (t_1 - 1.0));

#line 57
    }

#line 57
    return _S2;
}


#line 68
vec2 applyLensing_0(vec2 uv_0, float rs_0, float time_0, float stage_0)
{

#line 69
    float d_0 = length(uv_0);
    if(d_0 < 0.00100000004749745)
    {

#line 70
        return uv_0;
    }

    vec2 dir_0 = uv_0 / d_0;


    float alpha_0 = rs_0 * 0.18000000715255737 / (d_0 * d_0 + rs_0 * rs_0 * 0.03999999910593033);

#line 81
    float criticalBoost_0 = 1.0 + exp(- abs(d_0 - rs_0 * 1.5) * 30.0 / rs_0) * 2.5;

#line 81
    bool _S3;



    if(stage_0 > 3.5)
    {

#line 85
        _S3 = stage_0 < 4.5;

#line 85
    }
    else
    {

#line 85
        _S3 = false;

#line 85
    }

#line 85
    float stageBoost_0;

#line 85
    if(_S3)
    {

#line 85
        stageBoost_0 = 1.39999997615814209;

#line 85
    }
    else
    {

#line 85
        stageBoost_0 = 1.0;

#line 85
    }
    if(stage_0 > 4.5)
    {

#line 86
        stageBoost_0 = 1.79999995231628418;

#line 86
    }

#line 91
    return uv_0 + dir_0 * (alpha_0 * (criticalBoost_0 * stageBoost_0));
}


#line 35
float hash_0(vec2 p_0)
{

#line 36
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise_0(vec2 p_1)
{

#line 40
    vec2 i_0 = floor(p_1);

#line 40
    vec2 _S4 = fract(p_1);
    vec2 f_0 = _S4 * _S4 * (3.0 - 2.0 * _S4);
    float _S5 = f_0.x;

#line 42
    return mix(mix(hash_0(i_0), hash_0(i_0 + vec2(1.0, 0.0)), _S5), mix(hash_0(i_0 + vec2(0.0, 1.0)), hash_0(i_0 + vec2(1.0, 1.0)), _S5), f_0.y);
}


float fbm_0(vec2 p_2)
{

#line 46
    int i_1 = 0;

#line 46
    float a_0 = 0.5;

#line 46
    vec2 _S6 = p_2;

#line 46
    float v_0 = 0.0;

    for(;;)
    {

#line 48
        if(i_1 < 4)
        {
        }
        else
        {

#line 48
            break;
        }

#line 48
        float v_1 = v_0 + a_0 * noise_0(_S6);

#line 48
        vec2 _S7 = _S6 * 2.0;

#line 48
        float a_1 = a_0 * 0.5;

#line 48
        i_1 = i_1 + 1;

#line 48
        a_0 = a_1;

#line 48
        _S6 = _S7;

#line 48
        v_0 = v_1;

#line 48
    }
    return v_0;
}


#line 104
vec2 accretionDisk_0(vec2 uvLensed_0, float rs_1, float angle_0, float time_1, float stage_1)
{

#line 105
    float _S8 = max(rs_1, 0.01200000010430813);
    float shadowRadius_0 = _S8 * 2.59999990463256836;
    float innerRadius_0 = _S8 * 2.75;
    float outerRadius_0 = _S8 * 4.34999990463256836;


    float _S9 = uvLensed_0.x;

#line 111
    float _S10 = uvLensed_0.y;

#line 111
    float _S11 = (_S10 + _S8 * 0.03999999910593033) / 0.23999999463558197;
    float diskR_0 = length(vec2(_S9, _S11));
    float n_0 = diskR_0 / _S8;
    float _S12 = (atan((_S11),(_S9)));


    float diskMask_0 = smoothstep(innerRadius_0 * 0.87999999523162842, innerRadius_0, diskR_0) * (1.0 - smoothstep(outerRadius_0 * 0.87999999523162842, outerRadius_0, diskR_0));



    bool _S13 = stage_1 > 3.5;

#line 121
    bool _S14;

#line 121
    if(_S13)
    {

#line 121
        _S14 = stage_1 < 4.5;

#line 121
    }
    else
    {

#line 121
        _S14 = false;

#line 121
    }

#line 121
    float phase_0;

#line 121
    if(_S14)
    {

#line 121
        phase_0 = 1.0 + time_1 * 1.64999997615814209;

#line 121
    }
    else
    {

#line 121
        phase_0 = time_1;

#line 121
    }
    bool _S15 = stage_1 > 4.5;

#line 122
    if(_S15)
    {

#line 122
        phase_0 = 2.65000009536743164 + time_1 * 2.25;

#line 122
    }

    float rotatingAngle_0 = _S12 - phase_0 * (6.19999980926513672 * pow(max(n_0, 1.0), -1.5));


    float _S16 = log(max(n_0, 1.00999999046325684));

    float turbulence_0 = fbm_0(vec2(rotatingAngle_0 * 2.29999995231628418, n_0 * 3.79999995231628418) + vec2(phase_0 * 0.18000000715255737, 0.0));

#line 139
    float diskFront_0 = diskMask_0 * (exp(- max(n_0 - 2.79999995231628418, 0.0) * 0.72000002861022949) + exp(- abs(n_0 - 3.34999990463256836) * 2.79999995231628418) * 0.5 + exp(- abs(n_0 - 4.15000009536743164) * 3.5) * 0.23999999463558197) * (0.20000000298023224 + smoothstep(0.41999998688697815, 0.77999997138977051, (0.5 + 0.5 * sin(rotatingAngle_0 * 2.0 + _S16 * 11.0)) * 0.55000001192092896 + (0.5 + 0.5 * sin(rotatingAngle_0 * 5.0 - _S16 * 7.0 + 1.70000004768371582)) * 0.20000000298023224 + turbulence_0 * 0.55000001192092896) * 1.10000002384185791 + turbulence_0 * 0.37999999523162842) * mix(0.62000000476837158, 1.0, smoothstep(0.30000001192092896, 0.62000000476837158, fbm_0(vec2(rotatingAngle_0 * 4.0 + 7.0, n_0 * 7.0 - phase_0 * 0.25))));

#line 147
    float farArc_0 = exp(- abs(length(vec2(_S9, _S10 * 0.77999997138977051)) - shadowRadius_0 * 1.34000003337860107) * 34.0 / _S8) * smoothstep(- _S8 * 0.25, _S8 * 0.75, _S10) * (0.2800000011920929 + 0.72000002861022949 * smoothstep(0.0, shadowRadius_0 * 1.5, abs(_S9))) * (0.47999998927116394 + turbulence_0 * 0.40000000596046448);


    if(stage_1 > 2.5)
    {

#line 150
        _S14 = stage_1 < 3.5;

#line 150
    }
    else
    {

#line 150
        _S14 = false;

#line 150
    }

#line 150
    float stageGain_0;

#line 150
    if(_S14)
    {

#line 150
        stageGain_0 = smoothstep(0.10000000149011612, 0.77999997138977051, time_1);

#line 150
    }
    else
    {

#line 150
        stageGain_0 = 1.0;

#line 150
    }
    if(_S13)
    {

#line 151
        _S14 = stage_1 < 4.5;

#line 151
    }
    else
    {

#line 151
        _S14 = false;

#line 151
    }

#line 151
    if(_S14)
    {

#line 151
        stageGain_0 = 1.37999999523162842;

#line 151
    }
    if(_S15)
    {

#line 152
        stageGain_0 = (1.37999999523162842 + time_1 * 1.60000002384185791) * (1.0 - smoothstep(0.77999997138977051, 1.0, time_1));

#line 152
    }


    return vec2((diskFront_0 + farArc_0) * stageGain_0, clamp((diskR_0 - innerRadius_0) / max(outerRadius_0 - innerRadius_0, 0.00100000004749745), 0.0, 1.0));
}


#line 29
vec4 _S17;


#line 29
out vec4 entryPointParam_main_fragColor_0;


#line 29
in vec4 gemini_varying_0;


#line 29
in vec2 gemini_varying_1;


#line 164
void main()
{

#line 165
    float time_2 = gemini_varying_0.x;
    float stage_2 = gemini_varying_0.y * 8.0;
    float brightness_0 = gemini_varying_0.z * 4.0;
    float alpha_1 = gemini_varying_0.w;

#line 179
    bool _S18 = stage_2 > 2.5;

#line 179
    bool _S19;

#line 179
    if(_S18)
    {

#line 179
        _S19 = stage_2 < 3.5;

#line 179
    }
    else
    {

#line 179
        _S19 = false;

#line 179
    }

#line 179
    float shadowRadius_1;

#line 179
    float rs_2;

#line 179
    if(_S19)
    {

        float rs_3 = 0.2199999988079071 * easeOutExpo_0(time_2);

#line 182
        shadowRadius_1 = rs_3 * 2.59999990463256836;

#line 182
        rs_2 = rs_3;

#line 179
    }
    else
    {


        if(stage_2 > 4.5)
        {

            float rs_4 = 0.2199999988079071 * (1.0 - easeInExpo_0(time_2) * 0.85000002384185791);

#line 187
            shadowRadius_1 = rs_4 * 2.59999990463256836;

#line 187
            rs_2 = rs_4;

#line 184
        }
        else
        {

#line 184
            shadowRadius_1 = 0.57199996709823608;

#line 184
            rs_2 = 0.2199999988079071;

#line 184
        }

#line 179
    }

#line 195
    vec2 uv_1 = (gemini_varying_1 - 0.5) * 2.0;
    float _S20 = length(uv_1);

#line 203
    vec2 uvLensed_1 = applyLensing_0(uv_1, rs_2, time_2, stage_2);
    float dLensed_0 = length(uvLensed_1);
    float _S21 = uvLensed_1.x;

#line 223
    vec2 diskResult_0 = accretionDisk_0(uvLensed_1, rs_2, (atan((uvLensed_1.y),(_S21))), time_2, stage_2);

    float diskTemp_0 = diskResult_0.y;

#line 236
    float doppler_0 = _S21 * 0.5 + 0.5;



    float diskBrightness_0 = diskResult_0.x * (0.30000001192092896 + (1.0 + (1.0 - doppler_0) * 2.5) * 0.69999998807907104);

#line 250
    float photonRingRadius_0 = shadowRadius_1 * 1.01999998092651367;


    float photonRing_0 = exp(- abs(dLensed_0 - photonRingRadius_0) * 250.0);


    float photonRing2_0 = exp(- abs(dLensed_0 - photonRingRadius_0 * 0.97000002861022949) * 180.0) * 0.30000001192092896;

#line 266
    const vec3 _S22 = vec3(0.0);

#line 266
    int i_2 = 0;

#line 266
    float stars_0 = 0.0;

#line 266
    vec3 starColor_0 = _S22;



    for(;;)
    {

#line 270
        if(i_2 < 8)
        {
        }
        else
        {

#line 270
            break;
        }

#line 271
        float fi_0 = float(i_2);

        vec2 starOrig_0 = vec2(hash_0(vec2(fi_0, 0.30000001192092896)) * 4.0 - 2.0, hash_0(vec2(fi_0, 0.69999998807907104)) * 4.0 - 2.0);



        float starOrigDist_0 = length(starOrig_0);


        if(starOrigDist_0 < 2.5)
        {

#line 280
            _S19 = starOrigDist_0 > (shadowRadius_1 * 0.5);

#line 280
        }
        else
        {

#line 280
            _S19 = false;

#line 280
        }

#line 280
        if(_S19)
        {
            vec2 starLensed_0 = applyLensing_0(starOrig_0, rs_2, time_2, stage_2);



            float starBright_0 = hash_0(vec2(fi_0, 0.89999997615814209)) * 0.69999998807907104 + 0.30000001192092896;
            float starSize_0 = 0.00800000037997961 + starBright_0 * 0.01499999966472387;


            float stretchFactor_0 = 1.0 / max(dLensed_0 - shadowRadius_1, 0.01999999955296516);

            float tangentialDist_0 = abs((atan((uv_1.y),(uv_1.x))) - (atan((starLensed_0.y),(starLensed_0.x)))) / 6.28318023681640625;
            float radialDist_0 = abs(_S20 - length(starLensed_0));

#line 298
            float arc_0 = exp(- radialDist_0 * radialDist_0 / (starSize_0 * starSize_0 * 0.30000001192092896)) * exp(- tangentialDist_0 * tangentialDist_0 / (stretchFactor_0 * 0.03999999910593033)) * clamp(stretchFactor_0 * 0.02999999932944775, 0.10000000149011612, 1.0);

#line 303
            float starTemp_0 = hash_0(vec2(fi_0, 1.10000002384185791));
            vec3 starColor_1 = starColor_0 + mix(mix(vec3(0.5, 0.69999998807907104, 1.0), vec3(1.0, 1.0, 1.0), starTemp_0), mix(vec3(1.0, 0.89999997615814209, 0.5), vec3(1.0, 0.60000002384185791, 0.30000001192092896), starTemp_0 * 0.5), step(0.5, starTemp_0)) * arc_0 * starBright_0;

#line 304
            stars_0 = stars_0 + arc_0 * starBright_0;

#line 304
            starColor_0 = starColor_1;

#line 280
        }

#line 270
        i_2 = i_2 + 1;

#line 270
    }

#line 321
    float t_2 = clamp(diskTemp_0, 0.0, 1.0);

    vec3 diskColor_0 = mix(mix(vec3(0.69999998807907104, 0.85000002384185791, 1.0), vec3(1.0, 0.64999997615814209, 0.10000000149011612), smoothstep(0.0, 0.40000000596046448, t_2)), vec3(0.80000001192092896, 0.20000000298023224, 0.02999999932944775), smoothstep(0.40000000596046448, 1.0, t_2));

#line 331
    const vec3 photonRingColor_0 = vec3(14.0, 9.0, 5.0);

#line 350
    float photonGlow_0 = exp(- abs(dLensed_0 - shadowRadius_1) * 5.0 / rs_2) * 0.11999999731779099;

#line 355
    vec3 rgb_0 = mix(diskColor_0 * vec3(0.69999998807907104, 0.80000001192092896, 1.29999995231628418), diskColor_0 * vec3(1.29999995231628418, 0.80000001192092896, 0.60000002384185791), doppler_0) * diskBrightness_0 * 1.10000002384185791 + photonRingColor_0 * photonRing_0 * 1.5 + photonRingColor_0 * photonRing2_0 * 0.5 + starColor_0 * stars_0 * 0.60000002384185791 + vec3(2.0, 3.0, 5.0) * photonGlow_0 + vec3(0.30000001192092896, 0.15000000596046448, 0.69999998807907104) * (exp(- abs(uv_1.y)) * exp(- dLensed_0 * 0.80000001192092896) * 0.07999999821186066);


    bool _S23 = stage_2 > 4.5;

#line 358
    vec3 rgb_1;

#line 358
    if(_S23)
    {

#line 358
        rgb_1 = rgb_0 * (1.0 + time_2 * 4.0);

#line 358
    }
    else
    {

#line 358
        rgb_1 = rgb_0;

#line 358
    }

#line 371
    if(_S18)
    {

#line 371
        _S19 = stage_2 < 3.5;

#line 371
    }
    else
    {

#line 371
        _S19 = false;

#line 371
    }

#line 371
    float transitionAlpha_0;

#line 371
    if(_S19)
    {

#line 379
        vec3 rgb_2 = rgb_1 + vec3(1.0, 0.89999997615814209, 0.60000002384185791) * (exp(- time_2 * 4.0) * 2.5) * 0.5;

#line 379
        transitionAlpha_0 = alpha_1 * easeOutExpo_0(time_2 * 1.20000004768371582);

#line 379
        rgb_1 = rgb_2;

#line 371
    }
    else
    {

#line 380
        if(_S23)
        {



            if(time_2 > 0.85000002384185791)
            {

#line 385
                transitionAlpha_0 = alpha_1 * (1.0 - (time_2 - 0.85000002384185791) / 0.15000000596046448);

#line 385
            }
            else
            {

#line 385
                transitionAlpha_0 = alpha_1;

#line 385
            }

#line 380
        }
        else
        {

#line 380
            transitionAlpha_0 = alpha_1;

#line 380
        }

#line 371
    }

#line 411
    vec4 _S24 = vec4(rgb_1 * brightness_0, max(photonRing_0 * 0.94999998807907104 + photonRing2_0 * 0.40000000596046448 + diskBrightness_0 * 0.80000001192092896 + stars_0 * 0.5 + photonGlow_0 * 0.30000001192092896, photonRing_0 * 0.89999997615814209) * transitionAlpha_0) * ColorModulator;

#line 411
    _S17 = _S24;

    if((_S24.w) < 0.00050000002374873)
    {

#line 414
        discard;

#line 413
    }

#line 413
    entryPointParam_main_fragColor_0 = _S17;

#line 413
    return;
}

