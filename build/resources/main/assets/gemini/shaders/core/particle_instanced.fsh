#version 330 core
#line 22 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 22
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 36
float runeShape_0(vec2 p_0)
{

#line 37
    float _S1 = abs(p_0.x);

#line 37
    float _S2 = p_0.y;



    return max(max((1.0 - smoothstep(0.55000001192092896, 0.75, _S1 + abs(_S2))) * 0.69999998807907104, (1.0 - smoothstep(0.07999999821186066, 0.11999999731779099, _S1)) * 0.89999997615814209), (1.0 - smoothstep(0.05999999865889549, 0.10000000149011612, abs(_S2 - 0.15000000596046448))) * 0.60000002384185791);
}


float hexagonShape_0(vec2 p_1)
{

#line 46
    vec2 _S3 = abs(p_1);
    float _S4 = _S3.y;
    return 1.0 - smoothstep(0.55000001192092896, 0.60000002384185791, max(_S3.x * 0.86602497100830078 + _S4 * 0.5, _S4));
}


float triangleShape_0(vec2 p_2)
{
    float _S5 = p_2.y;
    float _S6 = _S5 + 0.18186524510383606;

#line 55
    float _S7 = p_2.x * 1.60000002384185791;



    return min(1.0 - smoothstep(-0.01999999955296516, 0.02999999932944775, - (_S5 - 0.36373049020767212)), min(1.0 - smoothstep(-0.01999999955296516, 0.02999999932944775, _S6 + _S7), 1.0 - smoothstep(-0.01999999955296516, 0.02999999932944775, _S6 - _S7))) * (1.0 - smoothstep(0.35333821177482605, 0.37412279844284058, _S5));
}


#line 31
float hash_0(vec2 p_3)
{

#line 32
    return fract(sin(dot(p_3, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}


#line 63
float featherShape_0(vec2 p_4)
{

#line 64
    float _S8 = p_4.x;

#line 64
    float _S9 = p_4.y;

    float radius_0 = 0.40000000596046448 + hash_0(floor(p_4 * 12.0 + ModelOffset.x * 0.5)) * 0.15000000596046448;
    return 1.0 - smoothstep(radius_0 - 0.07999999821186066, radius_0 + 0.05000000074505806, sqrt(_S8 * _S8 * 2.5 + _S9 * _S9 * 0.80000001192092896));
}


float starlightShape_0(vec2 p_5)
{
    float _S10 = abs(p_5.x);

#line 73
    float _S11 = abs(p_5.y);

#line 79
    return max(max((1.0 - smoothstep(0.15000000596046448, 0.25, pow(_S10, 0.55000001192092896) + pow(_S11, 0.55000001192092896))) * 0.80000001192092896, (1.0 - smoothstep(0.03999999910593033, 0.07999999821186066, _S11) * (1.0 - smoothstep(0.69999998807907104, 0.89999997615814209, _S10))) * 0.60000002384185791), (1.0 - smoothstep(0.03999999910593033, 0.07999999821186066, _S10) * (1.0 - smoothstep(0.69999998807907104, 0.89999997615814209, _S11))) * 0.60000002384185791);
}


#line 20
vec4 _S12;


#line 20
out vec4 entryPointParam_main_fragColor_0;


#line 20
in vec4 gemini_varying_1;


#line 20
in vec2 gemini_varying_0;


#line 84
void main()
{

#line 85
    float ageRatio_0 = gemini_varying_1.x;
    float typeVal_0 = gemini_varying_1.y;
    float intensity_0 = gemini_varying_1.z;
    float alpha_0 = gemini_varying_1.w;

    if(alpha_0 < 0.0020000000949949)
    {

#line 90
        discard;

#line 90
    }

    vec2 p_6 = (gemini_varying_0 - 0.5) * 2.0;

#line 92
    float shape_0;

#line 92
    vec3 baseColor_0;

#line 98
    if(typeVal_0 < 0.10000000149011612)
    {

#line 98
        const vec3 _S13 = vec3(1.0, 0.75, 0.30000001192092896);

#line 98
        shape_0 = runeShape_0(p_6);

#line 98
        baseColor_0 = _S13;

#line 98
    }
    else
    {

#line 99
        if(typeVal_0 < 0.30000001192092896)
        {

#line 99
            const vec3 _S14 = vec3(0.30000001192092896, 0.85000002384185791, 1.0);

#line 99
            shape_0 = hexagonShape_0(p_6);

#line 99
            baseColor_0 = _S14;

#line 99
        }
        else
        {

#line 100
            if(typeVal_0 < 0.5)
            {

#line 100
                const vec3 _S15 = vec3(1.0, 0.44999998807907104, 0.10000000149011612);

#line 100
                shape_0 = triangleShape_0(p_6);

#line 100
                baseColor_0 = _S15;

#line 100
            }
            else
            {

#line 101
                if(typeVal_0 < 0.69999998807907104)
                {

#line 101
                    const vec3 _S16 = vec3(0.80000001192092896, 0.40000000596046448, 0.89999997615814209);

#line 101
                    shape_0 = featherShape_0(p_6);

#line 101
                    baseColor_0 = _S16;

#line 101
                }
                else
                {

#line 102
                    const vec3 _S17 = vec3(0.94999998807907104, 0.94999998807907104, 1.0);

#line 102
                    shape_0 = starlightShape_0(p_6);

#line 102
                    baseColor_0 = _S17;

#line 101
                }

#line 100
            }

#line 99
        }

#line 98
    }

#line 111
    float combined_0 = clamp(shape_0 + exp(- length(p_6) * 3.5) * 0.30000001192092896, 0.0, 1.0) * (0.80000001192092896 + 0.20000000298023224 * sin(ModelOffset.x * 12.0 + ageRatio_0 * 20.0));

#line 119
    vec4 _S18 = vec4(baseColor_0 * combined_0 * intensity_0 * alpha_0 * (1.0 + shape_0 * 3.0), combined_0 * alpha_0);

#line 119
    _S12 = _S18;

#line 119
    entryPointParam_main_fragColor_0 = _S18;

#line 119
    return;
}

