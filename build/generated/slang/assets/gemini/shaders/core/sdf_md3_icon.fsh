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

#line 29
float heartSDF_0(vec2 p_0)
{

#line 30
    float r_0 = dot(p_0, p_0) - 1.0;
    float _S1 = p_0.x;

#line 31
    float _S2 = p_0.y;

#line 36
    return (r_0 * r_0 * r_0 - _S1 * _S1 * _S2 * _S2 * _S2) / max(length(vec2(6.0 * _S1 * r_0 * r_0 - 2.0 * _S1 * _S2 * _S2 * _S2, 6.0 * _S2 * r_0 * r_0 - 3.0 * _S1 * _S1 * _S2 * _S2)), 0.18000000715255737);
}


#line 21
float segmentSDF_0(vec2 p_1, vec2 a_0, vec2 b_0, float radius_0)
{

#line 22
    vec2 pa_0 = p_1 - a_0;
    vec2 ba_0 = b_0 - a_0;

    return length(pa_0 - ba_0 * clamp(dot(pa_0, ba_0) / max(dot(ba_0, ba_0), 0.00000999999974738), 0.0, 1.0)) - radius_0;
}


#line 19
vec4 _S3;


#line 19
out vec4 entryPointParam_main_fragColor_0;


#line 19
in vec2 gemini_varying_1;


#line 19
in vec2 gemini_varying_2;


#line 19
in vec2 gemini_varying_3;


#line 19
in vec4 gemini_varying_0;


#line 41
void main()
{
    vec2 p_2 = gemini_varying_1 / gemini_varying_2 * 2.0 - 1.0;
    int icon_0 = int(gemini_varying_3.x + 0.5);


    bool _S4 = icon_0 == 0;

#line 47
    bool _S5;

#line 47
    if(_S4)
    {

#line 47
        _S5 = true;

#line 47
    }
    else
    {

#line 47
        _S5 = icon_0 == 1;

#line 47
    }

#line 47
    float dist_0;

#line 47
    if(_S5)
    {


        float heart_0 = heartSDF_0(vec2(p_2.x * 1.15999996662139893, - p_2.y * 1.15999996662139893 + 0.02999999932944775));
        if(_S4)
        {

#line 52
            dist_0 = heart_0;

#line 52
        }
        else
        {

#line 52
            dist_0 = abs(heart_0) - 0.07500000298023224;

#line 52
        }

#line 47
    }
    else
    {



        if(icon_0 == 2)
        {
            const vec2 _S6 = vec2(0.0, 0.2800000011920929);

#line 55
            dist_0 = min(segmentSDF_0(p_2, vec2(-0.55000001192092896, -0.2800000011920929), _S6, 0.10499999672174454), segmentSDF_0(p_2, _S6, vec2(0.55000001192092896, -0.2800000011920929), 0.10499999672174454));

#line 53
        }
        else
        {


            if(icon_0 == 3)
            {
                const vec2 _S7 = vec2(0.0, -0.2800000011920929);

#line 60
                dist_0 = min(segmentSDF_0(p_2, vec2(-0.55000001192092896, 0.2800000011920929), _S7, 0.10499999672174454), segmentSDF_0(p_2, _S7, vec2(0.55000001192092896, 0.2800000011920929), 0.10499999672174454));

#line 58
            }
            else
            {


                if(icon_0 == 4)
                {
                    const vec2 _S8 = vec2(-0.14000000059604645, 0.37999999523162842);

#line 65
                    dist_0 = min(segmentSDF_0(p_2, vec2(-0.55000001192092896, -0.01999999955296516), _S8, 0.10499999672174454), segmentSDF_0(p_2, _S8, vec2(0.57999998331069946, -0.37999999523162842), 0.10499999672174454));

#line 63
                }
                else
                {

#line 63
                    dist_0 = min(segmentSDF_0(p_2, vec2(-0.46000000834465027, -0.46000000834465027), vec2(0.46000000834465027, 0.46000000834465027), 0.10000000149011612), segmentSDF_0(p_2, vec2(-0.46000000834465027, 0.46000000834465027), vec2(0.46000000834465027, -0.46000000834465027), 0.10000000149011612));

#line 63
                }

#line 58
            }

#line 53
        }

#line 47
    }

#line 77
    vec4 color_0 = gemini_varying_0 * ColorModulator;
    vec4 _S9 = vec4(color_0.xyz, color_0.w * (1.0 - smoothstep(0.0, max((fwidth((dist_0))), 0.00009999999747379), dist_0)));

#line 78
    _S3 = _S9;

    if((_S9.w) < 0.00400000018998981)
    {

#line 81
        discard;

#line 80
    }

#line 80
    entryPointParam_main_fragColor_0 = _S3;

#line 80
    return;
}

