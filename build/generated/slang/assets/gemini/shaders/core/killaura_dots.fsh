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

#line 27
float fillMask_0(float signedDistance_0)
{
    return 1.0 - smoothstep(0.0, max((fwidth((signedDistance_0))), 0.00600000005215406), signedDistance_0);
}


#line 22
float lineMask_0(float value_0, float width_0)
{
    return 1.0 - smoothstep(width_0, width_0 + max((fwidth((value_0))), 0.00600000005215406), abs(value_0));
}


#line 32
float polygonRadius_0(vec2 point_0, float sides_0)
{

#line 33
    float _S1 = (atan((point_0.y),(point_0.x)));
    float sector_0 = 6.28318548202514648 / sides_0;
    return cos(floor(0.5 + _S1 / sector_0) * sector_0 - _S1) * length(point_0);
}


#line 17
vec4 _S2;


#line 17
out vec4 entryPointParam_main_fragColor_0;


#line 17
in vec2 gemini_varying_1;


#line 17
in vec4 gemini_varying_0;


#line 40
void main()
{

#line 41
    int material_0 = int(floor(gemini_varying_1.x * 0.5 + 0.00009999999747379));

    vec2 p_0 = (vec2(mod(gemini_varying_1.x, 2.0), gemini_varying_1.y) - 0.5) * 2.0;
    float radius_0 = length(p_0);
    float _S3 = p_0.y;

#line 45
    float _S4 = p_0.x;

#line 45
    float _S5 = (atan((_S3),(_S4)));

#line 45
    float core_0;

#line 45
    float glow_0;

#line 45
    float whiteHot_0;

#line 50
    if(material_0 == 0)
    {
        if(radius_0 > 1.0)
        {

#line 52
            discard;

#line 52
        }
        float z_0 = sqrt(max(0.0, 1.0 - radius_0 * radius_0));



        float _S6 = exp(- radius_0 * 3.20000004768371582) * 0.46000000834465027;
        float _S7 = pow(max(z_0, 0.0), 9.0) * 0.37999999523162842;

#line 58
        core_0 = (1.0 - smoothstep(0.69999998807907104, 0.89999997615814209, radius_0)) * (0.72000002861022949 + max(dot(normalize(vec3(p_0, z_0)), normalize(vec3(-0.37999999523162842, 0.51999998092651367, 0.75999999046325684))), 0.0) * 0.51999998092651367);

#line 58
        glow_0 = _S6;

#line 58
        whiteHot_0 = _S7;

#line 50
    }
    else
    {

#line 59
        if(material_0 == 1)
        {
            float _S8 = abs(_S4);

#line 61
            float _S9 = - _S8;

#line 61
            float _S10 = abs(_S3);

#line 61
            float _S11 = - _S10;

            float diamond_0 = fillMask_0(_S8 + _S10 - 0.25);
            float _S12 = max(exp(_S9 * 25.0) * exp(_S11 * 2.20000004768371582), exp(_S11 * 31.0) * exp(_S9 * 4.19999980926513672));
            float _S13 = exp(- radius_0 * 3.79999995231628418) * 0.41999998688697815 + _S12 * 0.2199999988079071;
            float _S14 = diamond_0 * 0.72000002861022949;

#line 66
            core_0 = max(diamond_0, _S12 * 0.81999999284744263);

#line 66
            glow_0 = _S13;

#line 66
            whiteHot_0 = _S14;

#line 59
        }
        else
        {

#line 67
            if(material_0 == 2)
            {
                float _S15 = abs(_S4);

#line 69
                float _S16 = abs(_S3);

#line 69
                float _S17 = _S15 * 0.81999999284744263 + _S16;

#line 69
                float diamondDistance_0 = _S17 - 0.6600000262260437;
                float shell_0 = lineMask_0(diamondDistance_0, 0.05499999970197678);

#line 75
                float _S18 = exp(- abs(diamondDistance_0) * 7.5) * 0.41999998688697815;
                float _S19 = shell_0 * 0.46000000834465027;

#line 76
                core_0 = max(shell_0, max(lineMask_0(_S17 - 0.34000000357627869, 0.02500000037252903) * 0.77999997138977051, lineMask_0(_S15 - _S16 * 0.6600000262260437, 0.01799999922513962) * fillMask_0(diamondDistance_0) * 0.56000000238418579));

#line 76
                glow_0 = _S18;

#line 76
                whiteHot_0 = _S19;

#line 67
            }
            else
            {

#line 77
                if(material_0 == 3)
                {

#line 86
                    float center_0 = fillMask_0(radius_0 - 0.10499999672174454);


                    float _S20 = exp(- abs(radius_0 - 0.55000001192092896) * 6.0) * 0.25 + exp(- radius_0 * 5.5) * 0.30000001192092896;
                    float _S21 = center_0 * 0.64999997615814209;

#line 90
                    core_0 = max(max(lineMask_0(radius_0 - 0.67000001668930054, 0.03999999910593033) * step(0.15999999642372131, abs(sin(_S5 * 4.0 + 0.55000001192092896))), lineMask_0(radius_0 - 0.37999999523162842, 0.02800000086426735) * step(0.34999999403953552, abs(cos(_S5 * 6.0)))), max(pow(max(cos(_S5 * 3.0), 0.0), 28.0) * smoothstep(0.18000000715255737, 0.2800000011920929, radius_0) * (1.0 - smoothstep(0.47999998927116394, 0.62000000476837158, radius_0)), center_0));

#line 90
                    glow_0 = _S20;

#line 90
                    whiteHot_0 = _S21;

#line 77
                }
                else
                {

#line 91
                    if(material_0 == 4)
                    {
                        float _S22 = radius_0 - 0.73000001907348633;

                        float _S23 = 1.0 - fillMask_0(length(p_0 - vec2(0.28999999165534973, 0.03999999910593033)) - 0.62999999523162842);

#line 95
                        float crescent_0 = fillMask_0(_S22) * _S23;
                        float edge_0 = lineMask_0(_S22, 0.03500000014901161) * _S23;

                        float _S24 = exp(- abs(radius_0 - 0.63999998569488525) * 5.5) * crescent_0 * 0.55000001192092896;
                        float _S25 = edge_0 * 0.41999998688697815;

#line 99
                        core_0 = max(crescent_0 * 0.87999999523162842, edge_0);

#line 99
                        glow_0 = _S24;

#line 99
                        whiteHot_0 = _S25;

#line 91
                    }
                    else
                    {

#line 100
                        if(material_0 == 5)
                        {
                            float _S26 = max(lineMask_0(radius_0 - 0.82999998331069946, 0.01999999955296516), max(lineMask_0(radius_0 - 0.70999997854232788, 0.01200000010430813), lineMask_0(radius_0 - 0.44999998807907104, 0.01400000043213367)));

#line 111
                            float _S27 = max(lineMask_0(abs(_S4) + abs(_S3) - 0.18000000715255737, 0.01799999922513962), fillMask_0(radius_0 - 0.03500000014901161));



                            float _S28 = exp(- abs(radius_0 - 0.77999997138977051) * 12.0) * 0.2800000011920929 + exp(- abs(radius_0 - 0.47999998927116394) * 9.0) * 0.18000000715255737;
                            float _S29 = _S27 * 0.81999999284744263 + _S26 * 0.15999999642372131;

#line 116
                            core_0 = max(max(_S26, lineMask_0(polygonRadius_0(p_0, 6.0) - 0.55000001192092896, 0.01499999966472387) * 0.86000001430511475), max(pow(abs(cos(_S5 * 6.0)), 45.0) * smoothstep(0.18000000715255737, 0.25999999046325684, radius_0) * (1.0 - smoothstep(0.68000000715255737, 0.75999999046325684, radius_0)) * 0.77999997138977051, max(smoothstep(0.56000000238418579, 0.61000001430511475, radius_0) * (1.0 - smoothstep(0.64999997615814209, 0.69999998807907104, radius_0)) * step(0.57999998331069946, abs(sin(_S5 * 12.0))), _S27)));

#line 116
                            glow_0 = _S28;

#line 116
                            whiteHot_0 = _S29;

#line 100
                        }
                        else
                        {

#line 119
                            float lensDistance_0 = abs(_S3) - (0.37999999523162842 - _S4 * _S4 * 0.34000000357627869);


                            float pupil_0 = fillMask_0(radius_0 - 0.09000000357627869);

#line 128
                            float _S30 = exp(- abs(lensDistance_0) * 8.0) * 0.25 + exp(- radius_0 * 4.0) * 0.25;
                            float _S31 = pupil_0 * 0.86000001430511475;

#line 129
                            core_0 = max(lineMask_0(lensDistance_0, 0.03200000151991844) * step(abs(_S4), 0.81999999284744263), max(lineMask_0(radius_0 - 0.25, 0.03799999877810478), max(pupil_0, pow(abs(cos(_S5 * 5.0)), 42.0) * smoothstep(0.41999998688697815, 0.56000000238418579, radius_0) * (1.0 - smoothstep(0.69999998807907104, 0.81999999284744263, radius_0)) * 0.72000002861022949)));

#line 129
                            glow_0 = _S30;

#line 129
                            whiteHot_0 = _S31;

#line 100
                        }

#line 91
                    }

#line 77
                }

#line 67
            }

#line 59
        }

#line 50
    }

#line 132
    float alpha_0 = clamp(core_0 + glow_0, 0.0, 1.0) * gemini_varying_0.w;
    if(alpha_0 < 0.00400000018998981)
    {

#line 133
        discard;

#line 133
    }



    vec4 _S32 = vec4(gemini_varying_0.xyz * (core_0 * 1.36000001430511475 + glow_0 * 0.77999997138977051) + vec3(whiteHot_0 + core_0 * core_0 * 0.2199999988079071), alpha_0) * ColorModulator;

#line 137
    _S2 = _S32;

#line 137
    entryPointParam_main_fragColor_0 = _S32;

#line 137
    return;
}

