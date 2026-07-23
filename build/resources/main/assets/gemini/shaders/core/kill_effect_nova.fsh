#version 330 core
#line 18 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 18
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 146
float edgeMask_0(float d_0)
{


    return 1.0 - smoothstep(0.68000000715255737, 0.98000001907348633, d_0);
}


#line 28
vec4 _S1;



float hash_0(vec2 p_0)
{

#line 33
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise_0(vec2 p_1)
{

#line 37
    vec2 i_0 = floor(p_1);
    vec2 _S2 = fract(p_1);
    vec2 f_0 = _S2 * _S2 * (3.0 - 2.0 * _S2);

    float _S3 = f_0.x;

#line 40
    return mix(mix(hash_0(i_0), hash_0(i_0 + vec2(1.0, 0.0)), _S3), mix(hash_0(i_0 + vec2(0.0, 1.0)), hash_0(i_0 + vec2(1.0, 1.0)), _S3), f_0.y);
}


#line 48
float fbm_0(vec2 p_2)
{

#line 48
    int i_1 = 0;

#line 48
    float a_0 = 0.5;

#line 48
    vec2 _S4 = p_2;

#line 48
    float v_0 = 0.0;


    for(;;)
    {

#line 51
        if(i_1 < 3)
        {
        }
        else
        {

#line 51
            break;
        }

#line 52
        float v_1 = v_0 + a_0 * noise_0(_S4);
        vec2 _S5 = _S4 * 2.09999990463256836;
        float a_1 = a_0 * 0.5;

#line 51
        i_1 = i_1 + 1;

#line 51
        a_0 = a_1;

#line 51
        _S4 = _S5;

#line 51
        v_0 = v_1;

#line 51
    }

#line 56
    return v_0;
}


#line 77
vec3 fireColor_0(float t_0)
{

#line 90
    return mix(mix(mix(mix(vec3(1.0, 1.0, 0.94999998807907104), vec3(1.0, 0.81999999284744263, 0.30000001192092896), smoothstep(0.0, 0.25, t_0)), vec3(1.0, 0.47999998927116394, 0.05999999865889549), smoothstep(0.25, 0.5, t_0)), vec3(0.80000001192092896, 0.11999999731779099, 0.00999999977648258), smoothstep(0.5, 0.75, t_0)), vec3(0.20000000298023224, 0.01999999955296516, 0.0), smoothstep(0.75, 1.0, t_0));
}


#line 59
float voronoi_0(vec2 p_3)
{

#line 60
    vec2 _S6 = floor(p_3);
    vec2 _S7 = fract(p_3);

#line 61
    float minDist_0 = 1.0;

#line 61
    int y_0 = -1;

    for(;;)
    {

#line 63
        if(y_0 <= 1)
        {
        }
        else
        {

#line 63
            break;
        }

#line 63
        int x_0 = -1;
        for(;;)
        {

#line 64
            if(x_0 <= 1)
            {
            }
            else
            {

#line 64
                break;
            }

#line 65
            vec2 neighbor_0 = vec2(float(x_0), float(y_0));
            vec2 _S8 = _S6 + neighbor_0;
            vec2 diff_0 = neighbor_0 + vec2(hash_0(_S8), hash_0(_S8 + vec2(31.0))) - _S7;

            float _S9 = min(minDist_0, dot(diff_0, diff_0));

#line 64
            int _S10 = x_0 + 1;

#line 64
            minDist_0 = _S9;

#line 64
            x_0 = _S10;

#line 64
        }

#line 63
        y_0 = y_0 + 1;

#line 63
    }

#line 72
    return sqrt(minDist_0);
}


#line 95
vec3 nebulaColor_0(vec2 uv_0, float time_0)
{



    float nv_0 = fbm_0(uv_0 * 7.0 + 0.5) * 0.5 + 0.5;


    return mix(mix(mix(vec3(0.20000000298023224, 0.05999999865889549, 0.60000002384185791), vec3(0.69999998807907104, 0.15000000596046448, 0.40000000596046448), nv_0), vec3(0.05999999865889549, 0.20000000298023224, 0.80000001192092896), fbm_0(uv_0 * 3.0 + 2.0) * 0.5 + 0.5), vec3(0.85000002384185791, 0.60000002384185791, 0.10000000149011612), nv_0 * 0.15000000596046448);
}


#line 110
float drawStars_0(vec2 uv_1, float time_1)
{

#line 110
    int i_2 = 0;

#line 110
    float stars_0 = 0.0;

    for(;;)
    {

#line 112
        if(i_2 < 4)
        {
        }
        else
        {

#line 112
            break;
        }

#line 113
        float fi_0 = float(i_2);


        float sd_0 = length(uv_1 - vec2(hash_0(vec2(fi_0, 0.0)) * 2.0 - 1.0, hash_0(vec2(fi_0, 1.0)) * 2.0 - 1.0));
        float sb_0 = hash_0(vec2(fi_0, 2.0));
        float ss_0 = 0.00499999988824129 + sb_0 * 0.02199999988079071;
        float _S11 = - sd_0 * sd_0;

#line 119
        float _S12 = ss_0 * ss_0;
        float stars_1 = stars_0 + exp(_S11 / _S12) * sb_0 + exp(_S11 * 0.25 / _S12) * sb_0 * 0.20000000298023224;

#line 112
        i_2 = i_2 + 1;

#line 112
        stars_0 = stars_1;

#line 112
    }

#line 123
    return stars_0 * smoothstep(0.07999999821186066, 0.40000000596046448, time_1) * 0.64999997615814209;
}



float drawWisps_0(vec2 uv_2, float time_2)
{

#line 129
    float _S13 = (atan((uv_2.y),(uv_2.x)));
    float _S14 = length(uv_2);

#line 130
    int j_0 = 0;

#line 130
    float wisp_0 = 0.0;

    for(;;)
    {

#line 132
        if(j_0 < 2)
        {
        }
        else
        {

#line 132
            break;
        }

#line 133
        float fj_0 = float(j_0);



        float wisp_1 = wisp_0 + exp(- abs(fbm_0(vec2(_S14 * (2.0 + fj_0 * 1.5), (_S13 + time_2 * (1.10000002384185791 + fj_0 * 0.5) + fj_0 * 2.0) * 2.0)) - 0.5) * 9.0) * exp(- _S14 * (1.0 + fj_0 * 0.60000002384185791));

#line 132
        j_0 = j_0 + 1;

#line 132
        wisp_0 = wisp_1;

#line 132
    }

#line 139
    return wisp_0 * smoothstep(0.15000000596046448, 0.40000000596046448, time_2);
}


#line 139
out vec4 entryPointParam_main_fragColor_0;


#line 139
in vec4 gemini_varying_0;


#line 139
in vec2 gemini_varying_1;


#line 157
void main()
{

#line 158
    float time_3 = gemini_varying_0.x;
    float modeFlag_0 = gemini_varying_0.y;
    float intensity_0 = gemini_varying_0.z * 4.0;
    float alpha_0 = gemini_varying_0.w;


    if(alpha_0 < 0.0020000000949949)
    {

#line 164
        discard;

#line 164
        entryPointParam_main_fragColor_0 = _S1;

#line 164
        return;
    }
    vec2 uv_3 = (gemini_varying_1 - 0.5) * 2.0;
    float d_1 = length(uv_3);
    float _S15 = uv_3.y;

#line 168
    float _S16 = uv_3.x;

#line 168
    float _S17 = (atan((_S15),(_S16)));

#line 173
    if(modeFlag_0 < 0.25)
    {
        float em_0 = edgeMask_0(d_1);

#line 175
        float env_0;

#line 183
        if(time_3 < 0.34999999403953552)
        {

#line 183
            float _S18 = time_3 - 0.34999999403953552;

#line 183
            env_0 = exp(- (_S18 * _S18) / 0.05999999865889549);

#line 183
        }
        else
        {

#line 184
            float _S19 = time_3 - 0.34999999403953552;

#line 184
            env_0 = exp(- (_S19 * _S19) / 0.18000000715255737);

#line 183
        }


        if(time_3 < 0.00999999977648258)
        {

#line 186
            env_0 = time_3 / 0.00999999977648258 * env_0;

#line 186
        }


        float _S20 = - d_1;

#line 197
        float ringR_0 = time_3 * 0.80000001192092896;

#line 209
        float _S21 = 1.0 - time_3;

#line 216
        vec3 flashRgb_0 = (vec3(exp(_S20 * d_1 * 50.0) * env_0 * 200.0) + vec3(1.0, 0.94999998807907104, 0.89999997615814209) * (exp(_S20 * 8.0) * env_0 * 30.0) + mix(mix(vec3(1.0, 1.0, 1.0), vec3(0.40000000596046448, 0.60000002384185791, 1.0), smoothstep(0.0, 0.5, time_3)), vec3(0.60000002384185791, 0.20000000298023224, 1.0), smoothstep(0.5, 1.0, time_3)) * (exp(- abs(d_1 - ringR_0) * 18.0) * env_0 * 8.0 + exp(- abs(d_1 - ringR_0 * 0.5) * 16.0) * env_0 * 3.0 + exp(- abs(d_1 - ringR_0 * 1.5) * 12.0) * env_0 * 1.5) + vec3(0.40000000596046448, 0.11999999731779099, 0.85000002384185791) * (_S21 * _S21 * exp(_S20 * 3.5) * 3.0) + vec3(0.20000000298023224, 0.15000000596046448, 0.60000002384185791) * env_0 * 0.5) * (intensity_0 * 2.0 * em_0);



        vec4 _S22 = vec4(flashRgb_0, dot(flashRgb_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464)) * alpha_0) * ColorModulator;

#line 220
        _S1 = _S22;
        if((_S22.w) < 0.00100000004749745)
        {

#line 221
            discard;

#line 221
        }

#line 221
        entryPointParam_main_fragColor_0 = _S1;

#line 221
        return;
    }

#line 232
    float et_0 = 1.0 - pow(1.0 - time_3, 3.0);


    float shockR_0 = et_0 * 1.79999995231628418;



    float _S23 = - d_1;

#line 239
    float _S24 = _S23 * d_1;

#line 245
    float echoP_0 = clamp((time_3 - 0.23999999463558197) / 0.75999999046325684, 0.0, 1.0);

    float echoR_0 = (1.0 - pow(1.0 - echoP_0, 3.0)) * 1.48000001907348633;

#line 252
    float lateP_0 = clamp((time_3 - 0.51999998092651367) / 0.47999998927116394, 0.0, 1.0);

#line 257
    float shock_0 = (exp(- abs(d_1 - shockR_0) * 50.0) + exp(- abs(d_1 - shockR_0 * 0.60000002384185791) * 35.0) * 0.55000001192092896 + exp(- abs(d_1 - shockR_0 * 1.5) * 20.0) * 0.20000000298023224 + exp(_S24 / (shockR_0 * shockR_0 + 0.01999999955296516)) * 0.34999999403953552) * (smoothstep(0.0, 0.07999999821186066, time_3) * (1.0 - smoothstep(0.80000001192092896, 1.0, time_3))) + ((exp(- abs(d_1 - echoR_0) * 42.0) + exp(- abs(d_1 - echoR_0 * 0.6600000262260437) * 28.0) * 0.41999998688697815) * (smoothstep(0.0, 0.10000000149011612, echoP_0) * (1.0 - smoothstep(0.86000001430511475, 1.0, echoP_0))) * 0.81999999284744263 + exp(- abs(d_1 - (1.0 - pow(1.0 - lateP_0, 2.5)) * 1.24000000953674316) * 34.0) * smoothstep(0.0, 0.11999999731779099, lateP_0) * (1.0 - smoothstep(0.81999999284744263, 1.0, lateP_0)) * 0.57999998331069946);

#line 263
    float fireR_0 = et_0 * 3.0;

#line 269
    float fireFade_0 = smoothstep(0.01999999955296516, 0.15000000596046448, time_3);
    float fireball_0 = exp(_S24 / (fireR_0 * fireR_0 + 0.05999999865889549)) * (0.40000000596046448 + fbm_0(uv_3 * 6.0 + time_3 * 1.60000002384185791) * 0.34999999403953552 + fbm_0(uv_3 * 13.0 - time_3 * 2.0 + 2.5) * 0.34999999403953552 + fbm_0(uv_3 * 3.0 + time_3 * 0.5 + 4.0) * 0.25) * fireFade_0;

#line 279
    float _S25 = _S17 * 13.0;

#line 279
    float boltNoise_0 = fbm_0(vec2(_S25 / 6.28000020980834961, time_3 * 1.29999995231628418)) * 0.44999998807907104;

    float boltThick_0 = 0.01999999955296516 + fbm_0(vec2(_S17 * 3.5, d_1 * 7.0)) * 0.03999999910593033;

#line 288
    float bolt_0 = (1.0 - smoothstep(0.0, boltThick_0, abs(sin(_S25 + boltNoise_0 * 6.28000020980834961))) + (1.0 - smoothstep(0.0, boltThick_0 * 0.55000001192092896, abs(sin(_S25 * 1.79999995231628418 + boltNoise_0 * 3.1400001049041748 + 1.20000004768371582)))) * 0.34999999403953552) * (smoothstep(fireR_0 * 1.39999997615814209, fireR_0 * 0.30000001192092896, d_1) * fireFade_0) * ((0.44999998807907104 + hash_0(vec2(floor(_S25 / 6.28000020980834961), 2.0)) * 1.79999995231628418) * (0.60000002384185791 + 0.40000000596046448 * pow(abs(sin(time_3 * 38.0 + _S17 * 2.0)), 6.0)));

#line 298
    float nebula_0 = (fbm_0(uv_3 * 4.5 + time_3 * 0.30000001192092896) * 0.5 + (1.0 - voronoi_0(uv_3 * 5.5 + time_3 * 0.20000000298023224)) * 0.5) * (exp(_S23 / (0.5 + et_0 * 4.0)) * 0.64999997615814209) * smoothstep(0.10000000149011612, 0.34999999403953552, time_3);

#line 303
    float starsTotal_0 = drawStars_0(uv_3, time_3);
    const vec3 _S26 = vec3(0.60000002384185791, 0.75, 1.0);

#line 310
    float glow_0 = exp(_S23 * 0.89999997615814209 / (1.0 + et_0 * 3.5)) * 0.34999999403953552 * (1.0 + 0.20000000298023224 * sin(time_3 * 5.0 + d_1 * 2.0)) * smoothstep(0.0, 0.20000000298023224, time_3);

#line 315
    float wisp_2 = drawWisps_0(uv_3, time_3);

#line 321
    float jetY_0 = abs(_S15);
    float _S27 = - abs(_S16);

#line 322
    float _S28 = 1.0 + et_0 * 2.5;

    float jet_0 = (exp(_S27 * 10.0 / _S28) * exp(- abs(jetY_0 - et_0 * 0.89999997615814209) * 5.0) + exp(_S27 * 6.0 / _S28) * exp(- abs(jetY_0 - et_0 * 1.5) * 2.5) * 0.40000000596046448) * smoothstep(0.20000000298023224, 0.55000001192092896, time_3);

#line 333
    float novaEdge_0 = edgeMask_0(d_1);

#line 341
    vec4 _S29 = vec4((mix(vec3(0.44999998807907104, 0.69999998807907104, 1.0), vec3(1.0, 0.89999997615814209, 0.75), et_0 * 0.15000000596046448) * shock_0 * 2.15000009536743164 * intensity_0 + fireColor_0(d_1 / max(fireR_0, 0.00999999977648258)) * fireball_0 * 1.45000004768371582 * intensity_0 * (0.86000001430511475 + 0.14000000059604645 * abs(sin(time_3 * 22.0))) + mix(vec3(0.34999999403953552, 0.55000001192092896, 1.0), vec3(1.0, 0.89999997615814209, 1.0), hash_0(vec2(_S25, 0.0))) * bolt_0 * 1.20000004768371582 * intensity_0 + nebulaColor_0(uv_3, time_3) * nebula_0 * 0.72000002861022949 * intensity_0 + mix(_S26, vec3(1.0, 0.87999999523162842, 0.60000002384185791), fbm_0(uv_3 * 15.0)) * starsTotal_0 * intensity_0 + mix(vec3(1.0, 0.5, 0.15000000596046448), vec3(0.34999999403953552, 0.11999999731779099, 0.80000001192092896), et_0 * 0.40000000596046448) * glow_0 * intensity_0 * 1.35000002384185791 + mix(vec3(0.5, 0.69999998807907104, 1.0), vec3(0.89999997615814209, 0.34999999403953552, 0.80000001192092896), fbm_0(uv_3 * 5.0 + time_3)) * wisp_2 * 0.46000000834465027 * intensity_0 + mix(_S26, vec3(0.40000000596046448, 0.20000000298023224, 0.94999998807907104), jetY_0 * 0.40000000596046448) * jet_0 * 0.31999999284744263 * intensity_0) * (0.40000000596046448 + exp(_S23 * 0.5) * 0.60000002384185791) * novaEdge_0, (shock_0 * 0.81999999284744263 + fireball_0 * 0.62000000476837158 + bolt_0 * 0.72000002861022949 + nebula_0 * 0.41999998688697815 + glow_0 * 0.62000000476837158 + wisp_2 * 0.40000000596046448 + starsTotal_0 * 0.75 + jet_0 * 0.25) * alpha_0 * novaEdge_0) * ColorModulator;

#line 341
    _S1 = _S29;
    if((_S29.w) < 0.00050000002374873)
    {

#line 342
        discard;

#line 342
    }

#line 342
    entryPointParam_main_fragColor_0 = _S1;

#line 342
    return;
}

