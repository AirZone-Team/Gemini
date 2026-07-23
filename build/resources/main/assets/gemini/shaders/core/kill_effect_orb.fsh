#version 330 core
#line 29 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 29
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};



vec4 _S1;


#line 40
out vec4 entryPointParam_main_fragColor_0;


#line 40
in vec4 gemini_varying_0;


#line 49
in vec2 gemini_varying_1;


#line 49
in vec3 gemini_varying_2;


#line 44
void main()
{

#line 45
    float progress_0 = gemini_varying_0.x;
    float heat_0 = gemini_varying_0.y;
    float intensity_0 = gemini_varying_0.z * 4.0;
    float alpha_0 = gemini_varying_0.w;
    float _S2 = max(gemini_varying_1.x, 0.00100000004749745);

#line 49
    bool _S3;

    if(alpha_0 < 0.0020000000949949)
    {

#line 51
        _S3 = true;

#line 51
    }
    else
    {

#line 51
        _S3 = intensity_0 < 0.0020000000949949;

#line 51
    }

#line 51
    if(_S3)
    {

#line 51
        discard;

#line 51
        entryPointParam_main_fragColor_0 = _S1;

#line 51
        return;
    }

    vec3 C_0 = (((ModelViewMat) * (vec4(0.0, 0.0, 0.0, 1.0)))).xyz;


    vec3 D_0 = normalize(gemini_varying_2);



    vec3 oc_0 = - C_0;
    float b_0 = dot(oc_0, D_0);

    float h_0 = b_0 * b_0 - (dot(oc_0, oc_0) - _S2 * _S2);
    if(h_0 < 0.0)
    {

#line 65
        discard;

#line 65
        entryPointParam_main_fragColor_0 = _S1;

#line 65
        return;
    }

#line 66
    float h_1 = sqrt(h_0);
    float _S4 = - b_0;

#line 67
    float _S5 = max(_S4 - h_1, 0.0);
    float t1_0 = _S4 + h_1;
    if(t1_0 <= _S5)
    {

#line 69
        discard;

#line 69
        entryPointParam_main_fragColor_0 = _S1;

#line 69
        return;
    }

    vec3 _S6 = mix(vec3(1.0, 0.55000001192092896, 0.15000000596046448), vec3(0.87999999523162842, 0.95999997854232788, 1.1799999475479126), heat_0);
    vec3 _S7 = mix(vec3(0.44999998807907104, 0.05999999865889549, 0.01999999955296516), vec3(1.08000004291534424, 0.31999999284744263, 0.07999999821186066), heat_0);



    float _S8 = (t1_0 - _S5) / 12.0;
    const vec3 _S9 = vec3(0.0);

#line 78
    int i_0 = 0;

#line 78
    float trans_0 = 1.0;

#line 78
    vec3 acc_0 = _S9;


    for(;;)
    {

#line 81
        if(i_0 < 12)
        {
        }
        else
        {

#line 81
            break;
        }

#line 82
        float _S10 = float(i_0);
        vec3 p_0 = D_0 * (_S5 + (_S10 + 0.5) * _S8);
        float d_0 = length(p_0 - C_0) / _S2;



        float _S11 = - (d_0 * d_0);

#line 98
        float density_0 = (exp(_S11 * 16.0) * 2.59999990463256836 + exp(_S11 * 5.0) + exp(_S11 * 1.5) * 0.2800000011920929) * (0.8399999737739563 + 0.15999999642372131 * sin(p_0.x * 5.69999980926513672 + p_0.y * 7.30000019073486328 - p_0.z * 4.90000009536743164 + progress_0 * 34.0 + _S10 * 0.69999998807907104)) + exp(- pow(d_0 - (0.23999999463558197 + progress_0 * 0.57999998331069946), 2.0) * 120.0) * (1.0 - smoothstep(0.81999999284744263, 1.0, progress_0)) * 1.14999997615814209;

#line 103
        vec3 acc_1 = acc_0 + mix(_S6, _S7, smoothstep(0.0, 0.85000002384185791, d_0)) * density_0 * _S8 * trans_0;
        float trans_1 = trans_0 * exp(- density_0 * _S8 * 0.30000001192092896);

#line 81
        i_0 = i_0 + 1;

#line 81
        trans_0 = trans_1;

#line 81
        acc_0 = acc_1;

#line 81
    }

#line 112
    vec3 rgb_0 = acc_0 * 0.5 * intensity_0 * (1.0 + 0.10000000149011612 * sin(progress_0 * 46.0 + gemini_varying_2.x * 3.0) + 0.03999999910593033 * sin(progress_0 * 91.0 + gemini_varying_2.y * 5.0));



    vec4 _S12 = vec4(rgb_0, clamp(dot(rgb_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464)) * 0.5, 0.0, 1.0) * alpha_0) * ColorModulator;

#line 116
    _S1 = _S12;
    if((_S12.w) < 0.00050000002374873)
    {

#line 117
        discard;

#line 117
    }

#line 117
    entryPointParam_main_fragColor_0 = _S1;

#line 117
    return;
}

