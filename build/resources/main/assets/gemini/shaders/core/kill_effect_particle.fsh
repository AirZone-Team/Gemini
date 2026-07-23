#version 330 core
#line 20 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 20
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


vec4 _S1;


#line 30
out vec4 entryPointParam_main_fragColor_0;


#line 30
in vec2 gemini_varying_1;


#line 30
in vec4 gemini_varying_0;


void main()
{
    vec2 p_0 = (gemini_varying_1 - 0.5) * 2.0;
    float d_0 = length(p_0);

#line 43
    float _S2 = - d_0 * d_0;


    float brightness_0 = exp(_S2 * 6.0) * 0.80000001192092896 + (1.0 - smoothstep(0.69999998807907104, 1.0, d_0)) * 0.40000000596046448;


    float lifeRatio_0 = gemini_varying_0.x;
    float masterAlpha_0 = gemini_varying_0.w;
    bool skyMode_0 = (gemini_varying_0.y) > 0.75;

#line 51
    bool burstMode_0;
    if((gemini_varying_0.y) > 0.23999999463558197)
    {

#line 52
        burstMode_0 = (gemini_varying_0.y) <= 0.75;

#line 52
    }
    else
    {

#line 52
        burstMode_0 = false;

#line 52
    }


    float _S3 = - abs(p_0.x);

#line 55
    float _S4 = - abs(p_0.y);
    float diffraction_0 = (exp(_S3 * 30.0) * exp(_S4 * 3.5) + exp(_S4 * 30.0) * exp(_S3 * 3.5)) * 0.2800000011920929;

#line 56
    float brightness_1;
    if(skyMode_0)
    {

#line 57
        brightness_1 = brightness_0 + diffraction_0;

#line 57
    }
    else
    {

#line 57
        brightness_1 = brightness_0;

#line 57
    }
    if(burstMode_0)
    {

#line 58
        brightness_1 = brightness_1 + exp(_S2 * 1.14999997615814209) * 0.18000000715255737;

#line 58
    }
    else
    {

#line 58
    }

#line 58
    vec3 color_0;


    if(skyMode_0)
    {

        const vec3 hotColor_0 = vec3(1.0, 1.0, 1.0);
        const vec3 midColor_0 = vec3(0.55000001192092896, 0.75, 1.0);
        const vec3 coolColor_0 = vec3(0.15000000596046448, 0.30000001192092896, 0.80000001192092896);
        const vec3 deadColor_0 = vec3(0.01999999955296516, 0.03999999910593033, 0.10000000149011612);


        if(lifeRatio_0 < 0.40000000596046448)
        {

#line 70
            color_0 = mix(hotColor_0, midColor_0, lifeRatio_0 / 0.40000000596046448);

#line 70
        }
        else
        {
            if(lifeRatio_0 < 0.69999998807907104)
            {

#line 73
                color_0 = mix(midColor_0, coolColor_0, (lifeRatio_0 - 0.40000000596046448) / 0.30000001192092896);

#line 73
            }
            else
            {

#line 73
                color_0 = mix(coolColor_0, deadColor_0, (lifeRatio_0 - 0.69999998807907104) / 0.30000001192092896);

#line 73
            }

#line 70
        }

#line 61
    }
    else
    {

#line 80
        if(burstMode_0)
        {

#line 94
            float _S5 = 1.0 - lifeRatio_0;

#line 94
            color_0 = mix(mix(mix(vec3(1.0, 1.0, 0.94999998807907104), vec3(1.0, 0.80000001192092896, 0.34999999403953552), smoothstep(0.0, 0.30000001192092896, lifeRatio_0)), vec3(1.0, 0.41999998688697815, 0.07999999821186066), smoothstep(0.30000001192092896, 0.64999997615814209, lifeRatio_0)), vec3(0.30000001192092896, 0.05000000074505806, 0.01999999955296516), smoothstep(0.64999997615814209, 1.0, lifeRatio_0)) * (2.59999990463256836 * _S5 * _S5 + 0.69999998807907104);

#line 80
        }
        else
        {

#line 99
            const vec3 hotColor_1 = vec3(1.0, 0.94999998807907104, 0.69999998807907104);
            const vec3 midColor_1 = vec3(1.0, 0.55000001192092896, 0.07999999821186066);
            const vec3 coolColor_1 = vec3(0.60000002384185791, 0.07999999821186066, 0.01999999955296516);
            const vec3 deadColor_1 = vec3(0.15000000596046448, 0.01999999955296516, 0.0);


            if(lifeRatio_0 < 0.40000000596046448)
            {

#line 105
                color_0 = mix(hotColor_1, midColor_1, lifeRatio_0 / 0.40000000596046448);

#line 105
            }
            else
            {
                if(lifeRatio_0 < 0.69999998807907104)
                {

#line 108
                    color_0 = mix(midColor_1, coolColor_1, (lifeRatio_0 - 0.40000000596046448) / 0.30000001192092896);

#line 108
                }
                else
                {

#line 108
                    color_0 = mix(coolColor_1, deadColor_1, (lifeRatio_0 - 0.69999998807907104) / 0.30000001192092896);

#line 108
                }

#line 105
            }

#line 80
        }

#line 61
    }

#line 61
    float flicker_0;

#line 119
    if(!skyMode_0)
    {

#line 119
        flicker_0 = 1.0 + sin(lifeRatio_0 * 30.0 + gemini_varying_1.x * 10.0) * 0.20000000298023224;

#line 119
    }
    else
    {

#line 119
        flicker_0 = 1.0;

#line 119
    }


    vec3 color_1 = color_0 * flicker_0;

#line 134
    vec4 _S6 = vec4(color_1 * brightness_1 + color_1 * exp(_S2 * 1.5) * 0.15000000596046448, brightness_1 * masterAlpha_0 * min(smoothstep(0.0, 0.10000000149011612, lifeRatio_0), 1.0 - smoothstep(0.69999998807907104, 1.0, lifeRatio_0))) * ColorModulator;

#line 134
    _S1 = _S6;

    if((_S6.w) < 0.0020000000949949)
    {

#line 137
        discard;

#line 136
    }

#line 136
    entryPointParam_main_fragColor_0 = _S1;

#line 136
    return;
}

