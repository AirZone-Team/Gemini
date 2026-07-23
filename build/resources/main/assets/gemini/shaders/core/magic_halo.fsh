#version 330 core
#line 10 0
struct SLANG_ParameterGroup_HaloUniforms_0
{
    vec4 HaloMeta;
    vec4 HaloPrimary;
    vec4 HaloSecondary;
    vec4 HaloAccent;
    vec4 HaloGeometry;
    vec4 HaloDetail;
    vec4 HaloMotion;
    vec4 HaloReserved;
};


#line 10
layout(std140) uniform HaloUniforms
{
    vec4 HaloMeta;
    vec4 HaloPrimary;
    vec4 HaloSecondary;
    vec4 HaloAccent;
    vec4 HaloGeometry;
    vec4 HaloDetail;
    vec4 HaloMotion;
    vec4 HaloReserved;
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
float saturate_0(float value_0)
{

#line 28
    return clamp(value_0, 0.0, 1.0);
}


#line 61
vec2 rotate2d_0(vec2 value_1, float angle_0)
{

#line 62
    float sineValue_0 = sin(angle_0);
    float cosineValue_0 = cos(angle_0);
    return (((mat2x2(cosineValue_0, - sineValue_0, sineValue_0, cosineValue_0)) * (value_1)));
}


#line 35
float hash21_0(vec2 value_2)
{

#line 36
    return fract(sin(dot(value_2, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float valueNoise_0(vec2 value_3)
{

#line 40
    vec2 cell_0 = floor(value_3);
    vec2 _S1 = fract(value_3);
    vec2 local_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);

    float _S2 = local_0.x;

#line 43
    return mix(mix(hash21_0(cell_0), hash21_0(cell_0 + vec2(1.0, 0.0)), _S2), mix(hash21_0(cell_0 + vec2(0.0, 1.0)), hash21_0(cell_0 + vec2(1.0, 1.0)), _S2), local_0.y);
}


#line 50
float fbm_0(vec2 value_4)
{

#line 50
    vec2 _S3 = value_4;

#line 50
    int i_0 = 0;

#line 50
    float weight_0 = 0.5;

#line 50
    float result_0 = 0.0;


    for(;;)
    {

#line 53
        if(i_0 < 4)
        {
        }
        else
        {

#line 53
            break;
        }

#line 54
        float result_1 = result_0 + valueNoise_0(_S3) * weight_0;
        vec2 _S4 = _S3 * 2.02999997138977051 + 7.13000011444091797;
        float weight_1 = weight_0 * 0.5;

#line 53
        int _S5 = i_0 + 1;

#line 53
        _S3 = _S4;

#line 53
        i_0 = _S5;

#line 53
        weight_0 = weight_1;

#line 53
        result_0 = result_1;

#line 53
    }

#line 58
    return result_0;
}


#line 67
float band_0(float distanceValue_0, float radius_0, float halfWidth_0, float softness_0)
{

#line 68
    return 1.0 - smoothstep(halfWidth_0, halfWidth_0 + softness_0, abs(distanceValue_0 - radius_0));
}


#line 80
float dashedArc_0(float angle_1, float count_0, float fill_0, float phase_0, float softness_1)
{
    float _S6 = cos(3.14159274101257324 * fill_0);

#line 82
    return smoothstep(_S6 - softness_1, _S6 + softness_1, cos(angle_1 * count_0 + phase_0));
}


#line 31
float hash11_0(float value_5)
{

#line 32
    return fract(sin(value_5 * 127.1699981689453125) * 43758.546875);
}


#line 171
float layeredRings_0(float distanceValue_1, float angle_2, float radius_1, float thickness_0, int layers_0, int styleId_0, float softness_2, float time_0)
{

#line 172
    float result_2 = band_0(distanceValue_1, radius_1, thickness_0, softness_2);

#line 172
    int i_1 = 1;

    for(;;)
    {

#line 174
        if(i_1 < 5)
        {
        }
        else
        {

#line 174
            break;
        }

#line 175
        if(i_1 >= layers_0)
        {

#line 175
            break;
        }

#line 176
        float layer_0 = float(i_1);

        float ring_0 = band_0(distanceValue_1, radius_1 - thickness_0 * (1.89999997615814209 + layer_0 * 1.48000001907348633), thickness_0 * (0.41999998688697815 + 0.07000000029802322 * layer_0), softness_2);

#line 178
        float segmentation_0;

        if(styleId_0 == 1)
        {

#line 180
            segmentation_0 = dashedArc_0(angle_2, 8.0 + layer_0 * 5.0, 0.68999999761581421, time_0 * (0.69999998807907104 + layer_0 * 0.15000000596046448), 0.05999999865889549);

#line 180
        }
        else
        {

#line 182
            if(styleId_0 == 2)
            {

#line 182
                segmentation_0 = dashedArc_0(angle_2, 16.0 + layer_0 * 8.0, 0.37999999523162842, - time_0 * 1.29999995231628418, 0.02999999932944775);

#line 182
            }
            else
            {

#line 184
                if(styleId_0 == 3)
                {

#line 184
                    segmentation_0 = 0.44999998807907104 + 0.55000001192092896 * smoothstep(0.11999999731779099, 0.85000002384185791, hash11_0(floor((angle_2 + 3.14159274101257324) * (5.0 + layer_0))));

#line 184
                }
                else
                {
                    if(styleId_0 == 4)
                    {

#line 187
                        segmentation_0 = 0.68000000715255737 + 0.31999999284744263 * sin(angle_2 * (5.0 + layer_0 * 2.0) - time_0 * 2.0);

#line 187
                    }
                    else
                    {

#line 189
                        if(styleId_0 == 5)
                        {

#line 189
                            segmentation_0 = dashedArc_0(angle_2, 6.0 + layer_0 * 6.0, 0.72000002861022949, layer_0 * 3.14159274101257324 * 0.30000001192092896, 0.05999999865889549);

#line 189
                        }
                        else
                        {

#line 191
                            if(styleId_0 == 6)
                            {

#line 191
                                segmentation_0 = 0.55000001192092896 + 0.44999998807907104 * sin(angle_2 * (3.0 + layer_0 * 2.0) + time_0);

#line 191
                            }
                            else
                            {

#line 191
                                segmentation_0 = 1.0;

#line 191
                            }

#line 189
                        }

#line 187
                    }

#line 184
                }

#line 182
            }

#line 180
        }

#line 194
        float _S7 = max(result_2, ring_0 * saturate_0(segmentation_0));

#line 174
        int _S8 = i_1 + 1;

#line 174
        result_2 = _S7;

#line 174
        i_1 = _S8;

#line 174
    }

#line 196
    return result_2;
}


#line 75
float angularCell_0(float angle_3, float count_1)
{

#line 76
    float width_0 = 6.28318548202514648 / max(count_1, 1.0);
    float _S9 = width_0 * 0.5;

#line 77
    return mod(angle_3 + _S9, width_0) - _S9;
}


#line 141
float radialSpike_0(float distanceValue_2, float angle_4, float baseRadius_0, float lengthValue_0, float count_2, float widthScale_0, float variation_0, float softness_3)
{

    float cellWidth_0 = 6.28318548202514648 / max(count_2, 1.0);

    float cellIndex_0 = floor((angle_4 + 3.14159274101257324) / cellWidth_0);


    float tipRadius_0 = baseRadius_0 + lengthValue_0 * mix(1.0, 0.57999998331069946, mod(cellIndex_0, 2.0)) * mix(1.0, 0.62000000476837158 + 0.51999998092651367 * hash11_0(cellIndex_0 + 11.0), variation_0);

    float halfWidth_1 = cellWidth_0 * widthScale_0 * pow(1.0 - saturate_0((distanceValue_2 - baseRadius_0) / max(tipRadius_0 - baseRadius_0, 0.00100000004749745)), 1.25) + 0.0020000000949949;



    return (1.0 - smoothstep(halfWidth_1, halfWidth_1 + softness_3, abs(angularCell_0(angle_4, count_2)))) * (smoothstep(baseRadius_0 - softness_3, baseRadius_0 + softness_3, distanceValue_2) * (1.0 - smoothstep(tipRadius_0 - softness_3 * 2.0, tipRadius_0 + softness_3, distanceValue_2)));
}


#line 199
float styleOrnament_0(vec2 point_0, float distanceValue_3, float angle_5, int styleId_1, float radius_2, float thickness_1, float spikeLength_0, float spikeCount_0, float softness_4, float time_1)
{
    float ornament_0;


    if(styleId_1 == 0)
    {

#line 204
        ornament_0 = max(radialSpike_0(distanceValue_3, angle_5, radius_2 + thickness_1 * 0.40000000596046448, spikeLength_0, spikeCount_0, 0.25, 0.18000000715255737, softness_4), radialSpike_0(distanceValue_3, angle_5 + 3.14159274101257324 / spikeCount_0, radius_2, spikeLength_0 * 0.57999998331069946, spikeCount_0, 0.11999999731779099, 0.0, softness_4) * 0.77999997138977051);

#line 204
    }
    else
    {



        if(styleId_1 == 1)
        {

#line 210
            ornament_0 = max(radialSpike_0(distanceValue_3, angle_5 + time_1 * 0.07000000029802322, radius_2, spikeLength_0 * 0.72000002861022949, spikeCount_0, 0.18000000715255737, 0.34999999403953552, softness_4), band_0(distanceValue_3, radius_2 + spikeLength_0 * 0.43999999761581421, thickness_1 * 0.11999999731779099, softness_4) * dashedArc_0(angle_5, spikeCount_0 * 0.5, 0.23999999463558197, - time_1, 0.03999999910593033));

#line 210
        }
        else
        {



            if(styleId_1 == 2)
            {

#line 216
                ornament_0 = max(band_0(distanceValue_3, radius_2 + spikeLength_0 * 0.31999999284744263, thickness_1 * 0.25999999046325684, softness_4) * dashedArc_0(angle_5, spikeCount_0, 0.33000001311302185, time_1 * 1.79999995231628418, 0.02500000037252903), radialSpike_0(distanceValue_3, angle_5, radius_2, spikeLength_0 * 0.46000000834465027, spikeCount_0, 0.31999999284744263, 0.0, softness_4));

#line 216
            }
            else
            {

#line 223
                if(styleId_1 == 3)
                {

#line 223
                    ornament_0 = max(radialSpike_0(distanceValue_3, angle_5 + sin(time_1 * 0.30000001192092896) * 0.15000000596046448, radius_2 - thickness_1 * 0.20000000298023224, spikeLength_0 * 1.08000004291534424, spikeCount_0, 0.20000000298023224, 1.0, softness_4), band_0(distanceValue_3, radius_2 + spikeLength_0 * 0.47999998927116394, thickness_1 * 0.10999999940395355, softness_4) * dashedArc_0(angle_5, spikeCount_0 * 0.72000002861022949, 0.20000000298023224, time_1 * 2.29999995231628418, 0.01999999955296516));

#line 223
                }
                else
                {



                    if(styleId_1 == 4)
                    {
                        float flameTip_0 = radius_2 + spikeLength_0 * (0.37999999523162842 + 0.62000000476837158 * pow(sin(angle_5 * spikeCount_0 - time_1 * 3.0) * 0.5 + 0.5, 2.0));

#line 231
                        ornament_0 = smoothstep(radius_2 - softness_4, radius_2 + softness_4, distanceValue_3) * (1.0 - smoothstep(flameTip_0 - thickness_1, flameTip_0 + softness_4, distanceValue_3)) * dashedArc_0(angle_5, spikeCount_0, 0.43000000715255737, - time_1 * 1.60000002384185791, 0.03999999910593033);

#line 229
                    }
                    else
                    {

#line 236
                        if(styleId_1 == 5)
                        {

#line 236
                            ornament_0 = max(radialSpike_0(distanceValue_3, angle_5, radius_2, spikeLength_0, max(6.0, floor(spikeCount_0 / 6.0) * 6.0), 0.18999999761581421, 0.0, softness_4), radialSpike_0(distanceValue_3, angle_5 + 0.52359879016876221, radius_2 + spikeLength_0 * 0.25999999046325684, spikeLength_0 * 0.43000000715255737, max(12.0, floor(spikeCount_0 / 3.0) * 3.0), 0.10999999940395355, 0.0, softness_4) * 0.81999999284744263);

#line 236
                        }
                        else
                        {

#line 236
                            ornament_0 = max(band_0(distanceValue_3, radius_2 + spikeLength_0 * (0.2800000011920929 + 0.72000002861022949 * pow(abs(sin(angle_5 * max(3.0, spikeCount_0 * 0.25) + time_1 * 0.34999999403953552)), 1.79999995231628418)), thickness_1 * 0.30000001192092896, softness_4), radialSpike_0(distanceValue_3, angle_5 - time_1 * 0.11999999731779099, radius_2, spikeLength_0 * 0.55000001192092896, spikeCount_0, 0.14000000059604645, 0.15000000596046448, softness_4) * 0.72000002861022949);

#line 236
                        }

#line 229
                    }

#line 223
                }

#line 216
            }

#line 210
        }

#line 204
    }

#line 251
    return ornament_0;
}


#line 85
bool hasFeature_0(int featureBit_0)
{
    return (int(HaloMeta.w + 0.5) & featureBit_0) != 0;
}


#line 254
float runeField_0(float distanceValue_4, float angle_6, float radius_3, float thickness_2, int runeDetail_0, int styleId_2, float softness_5, float time_2)
{

#line 255
    bool _S10;
    if(!hasFeature_0(2))
    {

#line 256
        _S10 = true;

#line 256
    }
    else
    {

#line 256
        _S10 = runeDetail_0 <= 0;

#line 256
    }

#line 256
    if(_S10)
    {

#line 256
        return 0.0;
    }

#line 257
    float _S11 = float(runeDetail_0);

#line 257
    float runeRadius_0 = radius_3 - thickness_2 * (3.40000009536743164 + _S11 * 0.34999999403953552);
    float count_3 = 10.0 + _S11 * 8.0;

#line 258
    float direction_0;
    if(styleId_2 == 3)
    {

#line 259
        direction_0 = -1.0;

#line 259
    }
    else
    {

#line 259
        direction_0 = 1.0;

#line 259
    }

#line 264
    return max(band_0(distanceValue_4, runeRadius_0, thickness_2 * 0.15000000596046448, softness_5) * dashedArc_0(angle_6, count_3, 0.30000001192092896, time_2 * direction_0 * 1.70000004768371582, 0.03999999910593033), band_0(distanceValue_4, runeRadius_0 - thickness_2 * 0.85000002384185791, thickness_2 * 0.09000000357627869, softness_5) * dashedArc_0(angle_6, count_3 * 0.5, 0.11999999731779099, - time_2 * 1.10000002384185791, 0.02999999932944775));
}


#line 158
float directionalSpike_0(vec2 point_1, float centerAngle_0, float baseRadius_1, float tipRadius_1, float width_1, float softness_6)
{
    float distanceValue_5 = length(point_1);



    float _S12 = mix(width_1, 0.00300000002607703, pow(saturate_0((distanceValue_5 - baseRadius_1) / max(tipRadius_1 - baseRadius_1, 0.00100000004749745)), 0.72000002861022949));



    return (1.0 - smoothstep(_S12, _S12 + softness_6, abs(mod((atan((point_1.y),(point_1.x))) - centerAngle_0 + 3.14159274101257324, 6.28318548202514648) - 3.14159274101257324))) * (smoothstep(baseRadius_1 - softness_6, baseRadius_1 + softness_6, distanceValue_5) * (1.0 - smoothstep(tipRadius_1 - softness_6, tipRadius_1 + softness_6, distanceValue_5)));
}


#line 71
float disc_0(vec2 point_2, float radius_4, float softness_7)
{

#line 72
    return 1.0 - smoothstep(radius_4, radius_4 + softness_7, length(point_2));
}


#line 267
float crownField_0(vec2 point_3, float radius_5, float thickness_3, float spikeLength_1, int styleId_3, float softness_8, float time_3)
{
    if(!hasFeature_0(1))
    {

#line 269
        return 0.0;
    }

#line 269
    float _S13;
    if(styleId_3 == 0)
    {

#line 270
        _S13 = 1.29999995231628418;

#line 270
    }
    else
    {

#line 270
        _S13 = 1.04999995231628418;

#line 270
    }

#line 270
    float crownLength_0 = spikeLength_1 * _S13;
    float base_0 = radius_5 - thickness_3 * 0.25;



    float _S14 = radius_5 + crownLength_0 * 0.74000000953674316;

#line 281
    return max(max(directionalSpike_0(point_3, 1.57079637050628662, base_0, radius_5 + crownLength_0, 0.10499999672174454, softness_8), max(directionalSpike_0(point_3, 1.88079643249511719, base_0, _S14, 0.07599999755620956, softness_8), directionalSpike_0(point_3, 1.26079630851745605, base_0, _S14, 0.07599999755620956, softness_8))), disc_0(point_3 - vec2(0.0, radius_5 + crownLength_0 * (0.41999998688697815 + sin(time_3 * 2.0) * 0.01499999966472387)), thickness_3 * 0.47999998927116394, softness_8));
}

float orbitalField_0(vec2 point_4, float radius_6, float spikeLength_2, int starDensity_0, float softness_9, float time_4)
{
    if(!hasFeature_0(4))
    {

#line 286
        return 0.0;
    }

#line 287
    int _S15 = 2 + starDensity_0 * 2;

#line 287
    int i_2 = 0;

#line 287
    float result_3 = 0.0;

    for(;;)
    {

#line 289
        if(i_2 < 8)
        {
        }
        else
        {

#line 289
            break;
        }

#line 290
        if(i_2 >= _S15)
        {

#line 290
            break;
        }

#line 291
        float seed_0 = float(i_2);
        float orbitAngle_0 = time_4 * (0.57999998331069946 + seed_0 * 0.07500000298023224) + seed_0 * 6.28318548202514648 / float(_S15);
        float orbitRadius_0 = radius_6 + spikeLength_2 * (0.34000000357627869 + 0.09000000357627869 * mod(seed_0, 3.0));

        float size_0 = 0.00999999977648258 + 0.00800000037997961 * hash11_0(seed_0 + 3.0);

        float _S16 = orbitAngle_0 - 0.05000000074505806;

        float result_4 = result_3 + (disc_0(point_4 - vec2(cos(orbitAngle_0), sin(orbitAngle_0)) * orbitRadius_0, size_0, softness_9) + disc_0(point_4 - vec2(cos(_S16), sin(_S16)) * orbitRadius_0, size_0 * 1.70000004768371582, softness_9 * 2.0) * 0.2800000011920929);

#line 289
        i_2 = i_2 + 1;

#line 289
        result_3 = result_4;

#line 289
    }

#line 301
    return saturate_0(result_3);
}

float starField_0(vec2 point_5, float distanceValue_6, float radius_7, int density_0, float softness_10, float time_5)
{

#line 305
    bool _S17;
    if(!hasFeature_0(8))
    {

#line 306
        _S17 = true;

#line 306
    }
    else
    {

#line 306
        _S17 = density_0 <= 0;

#line 306
    }

#line 306
    if(_S17)
    {

#line 306
        return 0.0;
    }

#line 307
    float _S18 = float(density_0);
    vec2 grid_0 = point_5 * (11.0 + _S18 * 5.0);
    vec2 cell_1 = floor(grid_0);


    float randomValue_0 = hash21_0(cell_1 + 7.69999980926513672);

#line 318
    return (1.0 - smoothstep(0.03500000014901161, 0.11500000208616257 + softness_10, length(fract(grid_0) - 0.5 - (vec2(hash21_0(cell_1), hash21_0(cell_1 + 19.29999923706054688)) - 0.5) * 0.55000001192092896))) * step(0.77999997138977051 - _S18 * 0.09000000357627869, randomValue_0) * (smoothstep(radius_7 * 0.55000001192092896, radius_7 * 0.94999998807907104, distanceValue_6) * (1.0 - smoothstep(radius_7 + 0.44999998807907104, radius_7 + 0.62000000476837158, distanceValue_6))) * (0.44999998807907104 + 0.55000001192092896 * sin(time_5 * (3.0 + randomValue_0 * 5.0) + randomValue_0 * 20.0));
}


#line 90
void stylePalette_0(int styleId_4, out vec3 primary_0, out vec3 secondary_0, out vec3 accent_0)
{

#line 91
    if(styleId_4 == 1)
    {

#line 92
        primary_0 = vec3(0.56000000238418579, 0.27000001072883606, 1.0);
        secondary_0 = vec3(0.11999999731779099, 0.87999999523162842, 1.0);
        accent_0 = vec3(1.0, 0.80000001192092896, 0.34000000357627869);

#line 91
    }
    else
    {

        if(styleId_4 == 2)
        {

#line 96
            primary_0 = vec3(0.07999999821186066, 0.92000001668930054, 1.0);
            secondary_0 = vec3(1.0, 0.11999999731779099, 0.63999998569488525);
            accent_0 = vec3(0.87999999523162842, 1.0, 1.0);

#line 95
        }
        else
        {

            if(styleId_4 == 3)
            {

#line 100
                primary_0 = vec3(0.31000000238418579, 0.07999999821186066, 0.75999999046325684);
                secondary_0 = vec3(0.93999999761581421, 0.03999999910593033, 0.30000001192092896);
                accent_0 = vec3(0.81999999284744263, 0.68000000715255737, 1.0);

#line 99
            }
            else
            {

                if(styleId_4 == 4)
                {

#line 104
                    primary_0 = vec3(1.0, 0.2800000011920929, 0.01499999966472387);
                    secondary_0 = vec3(1.0, 0.72000002861022949, 0.05999999865889549);
                    accent_0 = vec3(1.0, 0.95999997854232788, 0.62000000476837158);

#line 103
                }
                else
                {

                    if(styleId_4 == 5)
                    {

#line 108
                        primary_0 = vec3(0.2199999988079071, 0.72000002861022949, 1.0);
                        secondary_0 = vec3(0.56000000238418579, 0.95999997854232788, 1.0);
                        accent_0 = vec3(0.95999997854232788, 1.0, 1.0);

#line 107
                    }
                    else
                    {

                        if(styleId_4 == 6)
                        {

#line 112
                            primary_0 = vec3(1.0, 0.2199999988079071, 0.68000000715255737);
                            secondary_0 = vec3(0.18000000715255737, 0.81999999284744263, 1.0);
                            accent_0 = vec3(1.0, 0.87999999523162842, 0.30000001192092896);

#line 111
                        }
                        else
                        {


                            primary_0 = vec3(1.0, 0.6600000262260437, 0.18000000715255737);
                            secondary_0 = vec3(1.0, 0.2800000011920929, 0.69999998807907104);
                            accent_0 = vec3(1.0, 0.98000001907348633, 0.81999999284744263);

#line 111
                        }

#line 107
                    }

#line 103
                }

#line 99
            }

#line 95
        }

#line 91
    }

#line 120
    return;
}


#line 122
vec3 spectralColor_0(float phase_1)
{

#line 123
    return 0.57999998331069946 + 0.41999998688697815 * cos(6.28318548202514648 * (phase_1 + vec3(0.0, 0.33000001311302185, 0.67000001668930054)));
}

void resolvePalette_0(int styleId_5, int colorMode_0, float angle_7, out vec3 primary_1, out vec3 secondary_1, out vec3 accent_1)
{
    stylePalette_0(styleId_5, primary_1, secondary_1, accent_1);
    if(colorMode_0 == 1)
    {

#line 130
        primary_1 = HaloPrimary.xyz;
        secondary_1 = HaloSecondary.xyz;
        accent_1 = HaloAccent.xyz;

#line 129
    }
    else
    {

        if(colorMode_0 == 2)
        {

#line 134
            float phase_2 = angle_7 / 6.28318548202514648 + HaloMeta.x * HaloMotion.w * 0.15999999642372131;
            primary_1 = spectralColor_0(phase_2);
            secondary_1 = spectralColor_0(phase_2 + 0.18000000715255737);
            accent_1 = spectralColor_0(phase_2 + 0.41999998688697815);

#line 133
        }

#line 129
    }

#line 139
    return;
}


#line 22
vec4 _S19;


#line 22
out vec4 entryPointParam_main_fragColor_0;


#line 22
in vec2 gemini_varying_0;


#line 323
void main()
{

#line 324
    float time_6 = HaloMeta.x;
    int styleId_6 = clamp(int(HaloMeta.y + 0.5), 0, 6);
    int colorMode_1 = clamp(int(HaloMeta.z + 0.5), 0, 2);
    float opacity_0 = HaloPrimary.w;
    float brightness_0 = HaloSecondary.w;
    float glowStrength_0 = HaloAccent.w;

#line 329
    bool _S20;

    if(opacity_0 < 0.00100000004749745)
    {

#line 331
        _S20 = true;

#line 331
    }
    else
    {

#line 331
        _S20 = brightness_0 < 0.00100000004749745;

#line 331
    }

#line 331
    if(_S20)
    {

#line 332
        discard;

#line 332
        entryPointParam_main_fragColor_0 = _S19;

#line 332
        return;
    }


    float radius_8 = HaloGeometry.x;
    float thickness_4 = HaloGeometry.y;
    float spikeLength_3 = HaloGeometry.z;
    float _S21 = max(4.0, floor(HaloGeometry.w + 0.5));
    int layers_1 = clamp(int(HaloDetail.x + 0.5), 1, 5);
    int runeDetail_1 = clamp(int(HaloDetail.y + 0.5), 0, 3);
    int starDensity_1 = clamp(int(HaloDetail.z + 0.5), 0, 3);
    float sharpness_0 = saturate_0(HaloDetail.w);
    float pulseAmount_0 = HaloMotion.y;
    float distortion_0 = HaloMotion.z;


    vec2 point_6 = rotate2d_0((gemini_varying_0 - 0.5) * 2.0, time_6 * HaloMotion.x * 0.2199999988079071);

    float rawDistance_0 = length(point_6);
    float _S22 = (atan((point_6.y),(point_6.x)));

    float warp_0 = (fbm_0(vec2(_S22 * 2.79999995231628418, rawDistance_0 * 6.0 - time_6 * 0.2800000011920929)) - 0.5) * distortion_0 * 0.06499999761581421;

#line 353
    float styleWarp_0;
    if(styleId_6 == 3)
    {

#line 354
        styleWarp_0 = sin(_S22 * 7.0 - time_6 * 1.39999997615814209) * distortion_0 * 0.03500000014901161;

#line 354
    }
    else
    {

#line 354
        styleWarp_0 = 0.0;

#line 354
    }
    float distanceValue_7 = rawDistance_0 + warp_0 + styleWarp_0;
    float angle_8 = _S22 + sin(rawDistance_0 * 11.0 - time_6) * distortion_0 * 0.01600000075995922;

    float pulseWave_0 = sin(time_6 * 3.0) * 0.5 + 0.5;
    float pulsedRadius_0 = radius_8 * (1.0 + (pulseWave_0 - 0.5) * pulseAmount_0 * 0.05499999970197678);
    float _S23 = mix(0.01799999922513962, 0.00249999994412065, sharpness_0);



    float ornaments_0 = styleOrnament_0(point_6, distanceValue_7, angle_8, styleId_6, pulsedRadius_0, thickness_4, spikeLength_3, _S21, _S23, time_6);



    float crown_0 = crownField_0(point_6, pulsedRadius_0, thickness_4, spikeLength_3, styleId_6, _S23, time_6);

    float orbitals_0 = orbitalField_0(point_6, pulsedRadius_0, spikeLength_3, starDensity_1, _S23, time_6);

    float stars_0 = starField_0(point_6, rawDistance_0, pulsedRadius_0, starDensity_1, _S23, time_6);


    float _S24 = max(max(layeredRings_0(distanceValue_7, angle_8, pulsedRadius_0, thickness_4, layers_1, styleId_6, _S23, time_6), ornaments_0), max(runeField_0(distanceValue_7, angle_8, pulsedRadius_0, thickness_4, runeDetail_1, styleId_6, _S23, time_6), crown_0));
    float _S25 = max(orbitals_0, stars_0 * 0.81999999284744263);

    float _S26 = distanceValue_7 - pulsedRadius_0;

#line 378
    float ringDistance_0 = abs(_S26);
    float _S27 = - ringDistance_0;

#line 379
    float tightGlow_0 = exp(_S27 * ringDistance_0 / max(thickness_4 * thickness_4 * 5.0, 0.00019999999494758));

#line 386
    float glow_0 = (tightGlow_0 * 0.34000000357627869 + exp(_S27 * (7.0 - glowStrength_0 * 0.60000002384185791)) * 0.15999999642372131 + exp(- max(_S26, 0.0) * (3.59999990463256836 - glowStrength_0 * 0.44999998807907104)) * smoothstep(pulsedRadius_0 * 0.44999998807907104, pulsedRadius_0, distanceValue_7) * 0.05499999970197678 + (ornaments_0 * 0.34000000357627869 + exp(- abs(distanceValue_7 - (pulsedRadius_0 + spikeLength_3 * 0.31999999284744263)) * 9.0) * dashedArc_0(angle_8, _S21, 0.34000000357627869, time_6, 0.11999999731779099) * 0.15999999642372131)) * glowStrength_0;



    float energy_0 = saturate_0(_S24 * (0.77999997138977051 + 0.2199999988079071 * sin(angle_8 * (6.0 + float(styleId_6)) - time_6 * 2.0) + (fbm_0(point_6 * 8.0 + time_6 * 0.11999999731779099) - 0.5) * distortion_0 * 0.25));
    float alpha_0 = saturate_0(energy_0 * 0.92000001668930054 + _S25 + glow_0 * 0.36000001430511475) * opacity_0;

    vec3 primary_2;
    vec3 secondary_2;
    vec3 accent_2;
    resolvePalette_0(styleId_6, colorMode_1, angle_8, primary_2, secondary_2, accent_2);

    if(styleId_6 == 6)
    {

#line 398
        styleWarp_0 = 3.0;

#line 398
    }
    else
    {

#line 398
        styleWarp_0 = 1.5;

#line 398
    }

    vec3 baseColor_0 = mix(primary_2, secondary_2, saturate_0((sin(angle_8 * styleWarp_0 - time_6 * 0.55000001192092896) * 0.5 + 0.5) * 0.72000002861022949 + distanceValue_7 * 0.41999998688697815));

#line 414
    vec4 _S28 = vec4((baseColor_0 * (energy_0 + glow_0 * 0.47999998927116394) + mix(baseColor_0, accent_2, saturate_0(tightGlow_0 * 0.72000002861022949 + _S25)) * (tightGlow_0 * 0.51999998092651367 + _S25 * 1.45000004768371582) + accent_2 * (crown_0 * 0.31999999284744263 + orbitals_0 * 1.70000004768371582 + stars_0 * 0.69999998807907104) + accent_2 * pow(saturate_0(_S24), mix(2.79999995231628418, 0.81999999284744263, sharpness_0)) * (0.2199999988079071 + glowStrength_0 * 0.10000000149011612)) * (brightness_0 * (0.92000001668930054 + pulseWave_0 * pulseAmount_0 * 0.15999999642372131)) * (1.0 + tightGlow_0 * glowStrength_0 * 0.23999999463558197 + orbitals_0 * 0.5), alpha_0) * ColorModulator;

#line 414
    _S19 = _S28;
    if((_S28.w) < 0.00100000004749745)
    {

#line 415
        discard;

#line 415
    }

#line 415
    entryPointParam_main_fragColor_0 = _S19;

#line 415
    return;
}

