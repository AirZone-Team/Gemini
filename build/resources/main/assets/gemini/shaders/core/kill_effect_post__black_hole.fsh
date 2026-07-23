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


#line 58
vec4 _S1;


#line 990
vec2 bhGravLens_0(vec2 uv_0, vec2 center_0, float rs_0, vec2 aspect_0)
{

#line 991
    vec2 delta_0 = uv_0 - center_0;

    float d_0 = length(delta_0 * aspect_0);
    if(d_0 < 0.00100000004749745)
    {

#line 994
        return uv_0;
    }

#line 1010
    return uv_0 + delta_0 / max(d_0, 0.00100000004749745) * min(rs_0 * 0.15000000596046448 / (d_0 * d_0 + rs_0 * rs_0 * 0.07999999821186066) * (1.0 + exp(- abs(d_0 - rs_0 * 1.29999995231628418) * 25.0 / max(rs_0, 0.00999999977648258)) * 6.0), 0.55000001192092896) / aspect_0;
}


#line 958
float bhHash_0(vec2 p_0)
{

#line 959
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float bhNoise_0(vec2 p_1)
{

#line 963
    vec2 i_0 = floor(p_1);
    vec2 _S2 = fract(p_1);
    vec2 f_0 = _S2 * _S2 * (3.0 - 2.0 * _S2);
    float _S3 = f_0.x;

#line 966
    return mix(mix(bhHash_0(i_0), bhHash_0(i_0 + vec2(1.0, 0.0)), _S3), mix(bhHash_0(i_0 + vec2(0.0, 1.0)), bhHash_0(i_0 + vec2(1.0, 1.0)), _S3), f_0.y);
}


float bhFbm_0(vec2 p_2)
{

#line 970
    int i_1 = 0;

#line 970
    float a_0 = 0.5;

#line 970
    vec2 _S4 = p_2;

#line 970
    float v_0 = 0.0;


    for(;;)
    {

#line 973
        if(i_1 < 3)
        {
        }
        else
        {

#line 973
            break;
        }

#line 974
        float v_1 = v_0 + a_0 * bhNoise_0(_S4);
        vec2 _S5 = _S4 * 2.09999990463256836;
        float a_1 = a_0 * 0.5;

#line 973
        i_1 = i_1 + 1;

#line 973
        a_0 = a_1;

#line 973
        _S4 = _S5;

#line 973
        v_0 = v_1;

#line 973
    }

#line 978
    return v_0;
}


#line 1024
vec3 bhAccretionDisk_0(vec2 delta_1, float time_0, float rs_1, float rotSpeed_0)
{

#line 1025
    float _S6 = max(rs_1, 0.00100000004749745);


    float _S7 = delta_1.x;

#line 1028
    float _S8 = delta_1.y;

#line 1028
    float _S9 = (_S8 + _S6 * 0.03500000014901161) / 0.23499999940395355;
    float diskR_0 = length(vec2(_S7, _S9));
    float n_0 = diskR_0 / _S6;
    float screenN_0 = length(delta_1) / _S6;

#line 1031
    bool _S10;
    if(n_0 > 8.39999961853027344)
    {

#line 1032
        _S10 = screenN_0 > 3.79999995231628418;

#line 1032
    }
    else
    {

#line 1032
        _S10 = false;

#line 1032
    }

#line 1032
    if(_S10)
    {

#line 1032
        return vec3(0.0);
    }


    float rotAng_0 = (atan((_S9),(_S7))) - time_0 * (5.40000009536743164 * pow(max(n_0, 1.0), -1.5) * rotSpeed_0);

#line 1046
    float _S11 = log(max(n_0, 1.00999999046325684));
    float _S12 = rotAng_0 * 5.0;
    float turb_0 = bhFbm_0(vec2(rotAng_0 * 2.20000004768371582, n_0 * 3.29999995231628418) + vec2(time_0 * 0.10000000149011612, 0.0));

#line 1058
    float approaching_0 = clamp(- _S7 / max(diskR_0, 0.00009999999747379), -1.0, 1.0);


    float temp_0 = clamp((n_0 - 1.54999995231628418) / 6.30000019073486328, 0.0, 1.0);

#line 1066
    float _S13 = approaching_0 * 0.5 + 0.5;

#line 1076
    return mix(mix(vec3(0.77999997138977051, 0.89999997615814209, 1.35000002384185791), vec3(1.35000002384185791, 0.57999998331069946, 0.07999999821186066), smoothstep(0.01999999955296516, 0.43000000715255737, temp_0)), vec3(0.47999998927116394, 0.03500000014901161, 0.00800000037997961), smoothstep(0.43000000715255737, 1.0, temp_0)) * mix(vec3(1.3200000524520874, 0.76999998092651367, 0.51999998092651367), vec3(0.68000000715255737, 0.86000001430511475, 1.37999999523162842), _S13) * (smoothstep(1.48000001907348633, 1.72000002861022949, n_0) * (1.0 - smoothstep(7.19999980926513672, 8.35000038146972656, n_0)) * (exp(- max(n_0 - 1.70000004768371582, 0.0) * 0.69999998807907104) + 0.51999998092651367 * exp(- abs(n_0 - 2.65000009536743164) * 1.89999997615814209) + 0.2800000011920929 * exp(- abs(n_0 - 4.15000009536743164) * 1.39999997615814209) + 0.12999999523162842 * exp(- abs(n_0 - 6.34999990463256836) * 1.10000002384185791)) * (0.15999999642372131 + smoothstep(0.34000000357627869, 0.77999997138977051, (0.5 + 0.5 * sin(rotAng_0 * 2.0 + _S11 * 10.5)) * 0.47999998927116394 + (0.5 + 0.5 * sin(_S12 - _S11 * 6.5 + 1.39999997615814209)) * 0.20000000298023224 + turb_0 * 0.56000000238418579) * 1.20000004768371582 + turb_0 * 0.34000000357627869) * mix(0.51999998092651367, 1.0, smoothstep(0.2800000011920929, 0.6600000262260437, bhFbm_0(vec2(rotAng_0 * 4.09999990463256836 + 8.0, n_0 * 6.80000019073486328 - time_0 * 0.15999999642372131))))) * pow(max(0.37999999523162842, 1.0 + approaching_0 * 0.72000002861022949), 2.34999990463256836) * 2.75 + mix(vec3(1.45000004768371582, 0.44999998807907104, 0.05999999865889549), vec3(1.0, 0.92000001668930054, 0.72000002861022949), clamp(_S13, 0.0, 1.0)) * (exp(- abs(screenN_0 - 1.58000004291534424) * 18.0) * smoothstep(-0.18000000715255737 * _S6, 0.72000002861022949 * _S6, _S8) * (0.31999999284744263 + 0.68000000715255737 * smoothstep(0.0, 2.59999990463256836 * _S6, abs(_S7)))) * (0.62000000476837158 + 0.37999999523162842 * bhNoise_0(vec2(_S12, time_0 * 0.34999999403953552))) * 1.79999995231628418;
}


#line 1083
float bhCorona_0(vec2 delta_2, float rs_2)
{
    float _S14 = - abs(delta_2.x);

#line 1085
    float _S15 = max(rs_2, 0.00999999977648258);
    float _S16 = delta_2.y;

#line 1086
    float _S17 = rs_2 * 0.30000001192092896;


    return (exp(_S14 * 8.0 / _S15) * exp(- abs(_S16 - _S17) * 4.0 / _S15) + exp(_S14 * 6.0 / _S15) * exp(- abs(_S16 + _S17) * 4.0 / _S15) * 0.5) * 0.15000000596046448;
}


#line 1089
out vec4 entryPointParam_main_fragColor_0;


#line 1089
in vec2 gemini_varying_0;


#line 1098
void main()
{

#line 1099
    vec2 centerUV_0 = Center1.xy * 0.5 + 0.5;
    vec2 aspect_1 = vec2(1.0, Params.y / Params.x);

    vec2 delta_3 = (gemini_varying_0 - centerUV_0) * aspect_1;
    float r_0 = length(delta_3);


    float bhRadius_0 = BHParams.x;
    float stage_0 = BHParams.y;
    float progress_0 = BHParams.z;
    float intensity_0 = clamp(BHParams.w, 0.0, 5.0);
    float time_1 = TimePack.x;

#line 1116
    bool _S18 = stage_0 > 2.5;

#line 1116
    bool _S19;

#line 1116
    if(_S18)
    {

#line 1116
        _S19 = stage_0 < 3.5;

#line 1116
    }
    else
    {

#line 1116
        _S19 = false;

#line 1116
    }

#line 1116
    float finalRs_0;

#line 1116
    if(_S19)
    {

#line 1116
        finalRs_0 = bhRadius_0 * (1.0 - exp(- progress_0 * 4.0));

#line 1116
    }
    else
    {


        if(stage_0 > 4.5)
        {

#line 1121
            finalRs_0 = bhRadius_0 * max(1.0 - progress_0 * progress_0 * 0.85000002384185791, 0.00999999977648258);

#line 1121
        }
        else
        {

#line 1121
            finalRs_0 = bhRadius_0;

#line 1121
        }

#line 1116
    }

#line 1127
    float n_1 = r_0 / max(finalRs_0, 0.00100000004749745);
    if(finalRs_0 < 0.00100000004749745)
    {

#line 1128
        _S19 = true;

#line 1128
    }
    else
    {

#line 1128
        _S19 = n_1 > 9.0;

#line 1128
    }

#line 1128
    if(_S19)
    {

#line 1129
        vec4 _S20 = (texture((SceneSampler), (gemini_varying_0)));

#line 1129
        _S1 = _S20;

#line 1129
        entryPointParam_main_fragColor_0 = _S20;

#line 1129
        return;
    }

#line 1135
    if(_S18)
    {

#line 1135
        _S19 = stage_0 < 3.5;

#line 1135
    }
    else
    {

#line 1135
        _S19 = false;

#line 1135
    }

#line 1135
    float stageRamp_0;

#line 1135
    if(_S19)
    {

#line 1135
        stageRamp_0 = smoothstep(0.0, 0.10000000149011612, progress_0);

#line 1135
    }
    else
    {

#line 1137
        if(stage_0 > 4.5)
        {

#line 1137
            stageRamp_0 = 1.0 - smoothstep(0.60000002384185791, 0.92000001668930054, progress_0);

#line 1137
        }
        else
        {

#line 1137
            stageRamp_0 = 1.0;

#line 1137
        }

#line 1135
    }

#line 1152
    vec2 _S21 = mix(gemini_varying_0, bhGravLens_0(gemini_varying_0, centerUV_0, finalRs_0, aspect_1), stageRamp_0);

#line 1152
    vec3 sceneLensed_0 = (texture((SceneSampler), (_S21))).xyz;
    vec3 sceneOrig_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;

#line 1160
    float eh_0 = 1.0 - smoothstep(0.97000002861022949, 1.00999999046325684, n_1);



    float rim_0 = exp(- abs(n_1 - 1.01999998092651367) * 55.0);


    float pr_0 = exp(- abs(n_1 - 1.3200000524520874) * 42.0);
    float pr2_0 = exp(- abs(n_1 - 1.22000002861022949) * 28.0) * 0.30000001192092896;

#line 1168
    float rotSpeed_1;


    if(stage_0 > 4.5)
    {

#line 1171
        rotSpeed_1 = 2.20000004768371582;

#line 1171
    }
    else
    {

#line 1171
        rotSpeed_1 = 1.0;

#line 1171
    }

#line 1195
    const vec3 _S22 = vec3(18.0, 13.0, 7.0);

#line 1216
    vec4 _S23 = vec4(mix(sceneOrig_0, mix(sceneLensed_0, vec3(0.0), eh_0) + bhAccretionDisk_0(delta_3, time_1, finalRs_0, rotSpeed_1) * intensity_0 * stageRamp_0 + _S22 * pr_0 * intensity_0 * stageRamp_0 + _S22 * pr2_0 * 0.40000000596046448 * intensity_0 * stageRamp_0 + vec3(1.60000002384185791, 1.20000004768371582, 0.44999998807907104) * rim_0 * intensity_0 * stageRamp_0 + vec3(2.20000004768371582, 1.5, 0.5) * (exp(- abs(n_1 - 1.29999995231628418) * 7.0) * 0.5) * intensity_0 * stageRamp_0 + vec3(1.20000004768371582, 0.80000001192092896, 0.25) * (exp(- abs(n_1 - 1.60000002384185791) * 3.0) * 0.20000000298023224) * intensity_0 * stageRamp_0 + vec3(0.60000002384185791, 0.30000001192092896, 0.89999997615814209) * bhCorona_0(delta_3, finalRs_0) * intensity_0 * stageRamp_0, max(1.0 - smoothstep(finalRs_0 * 1.39999997615814209, finalRs_0 * 8.69999980926513672, r_0), eh_0)), 1.0);

#line 1216
    _S1 = _S23;

#line 1216
    entryPointParam_main_fragColor_0 = _S23;

#line 1216
    return;
}

