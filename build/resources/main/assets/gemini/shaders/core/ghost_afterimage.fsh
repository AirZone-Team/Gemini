#version 330 core
#line 27 0
float circle_0(vec2 p_0, float r_0)
{

#line 28
    return length(p_0) - r_0;
}


#line 22
float roundedRect_0(vec2 p_1, vec2 halfSize_0, float r_1)
{

#line 23
    vec2 d_0 = abs(p_1) - halfSize_0 + r_1;
    return length(max(d_0, 0.0)) + min(max(d_0.x, d_0.y), 0.0) - r_1;
}


#line 17
float smin_0(float a_0, float b_0, float k_0)
{

#line 18
    float h_0 = max(k_0 - abs(a_0 - b_0), 0.0) / k_0;
    return min(a_0, b_0) - h_0 * h_0 * k_0 * 0.25;
}


#line 13
vec4 _S1;


#line 13
out vec4 entryPointParam_main_fragColor_0;


#line 13
in vec4 gemini_varying_1;


#line 13
in vec2 gemini_varying_0;


#line 33
void main()
{

#line 34
    float fade_0 = gemini_varying_1.x;
    vec3 ghostCol_0 = gemini_varying_1.yzw;
    float alpha_0 = fade_0 * 0.55000001192092896;

    if(alpha_0 < 0.0020000000949949)
    {

#line 38
        discard;

#line 38
    }

    vec2 p_2 = (gemini_varying_0 - 0.5) * 2.0;

#line 47
    float laCos_0 = cos(-0.15000000596046448);

#line 47
    float laSin_0 = sin(-0.15000000596046448);
    vec2 laP_0 = p_2 - vec2(-0.23999999463558197, 0.11999999731779099);
    float _S2 = laP_0.x;

#line 49
    float _S3 = laP_0.y;
    const vec2 _S4 = vec2(0.06499999761581421, 0.25);

    float raCos_0 = cos(0.15000000596046448);

#line 52
    float raSin_0 = sin(0.15000000596046448);
    vec2 raP_0 = p_2 - vec2(0.23999999463558197, 0.11999999731779099);
    float _S5 = raP_0.x;

#line 54
    float _S6 = raP_0.y;


    const vec2 _S7 = vec2(0.07999999821186066, 0.23999999463558197);

#line 66
    float body_0 = smin_0(smin_0(smin_0(smin_0(smin_0(smin_0(circle_0(p_2 - vec2(0.0, 0.57999998331069946), 0.2199999988079071), roundedRect_0(p_2 - vec2(0.0, 0.37999999523162842), vec2(0.07999999821186066, 0.05000000074505806), 0.02999999932944775), 0.01999999955296516), roundedRect_0(p_2 - vec2(0.0, 0.01999999955296516), vec2(0.20000000298023224, 0.30000001192092896), 0.03999999910593033), 0.05000000074505806), roundedRect_0(vec2(_S2 * laCos_0 - _S3 * laSin_0, _S2 * laSin_0 + _S3 * laCos_0), _S4, 0.03999999910593033), 0.05000000074505806), roundedRect_0(vec2(_S5 * raCos_0 - _S6 * raSin_0, _S5 * raSin_0 + _S6 * raCos_0), _S4, 0.03999999910593033), 0.05000000074505806), roundedRect_0(p_2 - vec2(-0.09000000357627869, -0.37999999523162842), _S7, 0.03999999910593033), 0.05000000074505806), roundedRect_0(p_2 - vec2(0.09000000357627869, -0.37999999523162842), _S7, 0.03999999910593033), 0.05000000074505806);

#line 71
    float innerGlow_0 = 1.0 - smoothstep(-0.07999999821186066, 0.05999999865889549, body_0);
    float edgeGlow_0 = exp(- abs(body_0 + 0.01999999955296516) * 18.0) * 0.80000001192092896;



    float shapeMask_0 = clamp(1.0 - smoothstep(-0.01999999955296516, 0.02999999932944775, body_0) + innerGlow_0 * 0.40000000596046448 + edgeGlow_0 * 0.5 + exp(- abs(body_0) * 5.0) * 0.34999999403953552 * 0.30000001192092896, 0.0, 1.0);

#line 84
    vec4 _S8 = vec4(ghostCol_0 * shapeMask_0 * fade_0 * (1.0 + innerGlow_0 * 2.5 + edgeGlow_0), shapeMask_0 * alpha_0);

#line 84
    _S1 = _S8;

#line 84
    entryPointParam_main_fragColor_0 = _S8;

#line 84
    return;
}

