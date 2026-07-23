#version 330 core
#line 27 0
struct SLANG_ParameterGroup_ElementData_0
{
    vec2 u_pos;
    vec2 u_size;
    float u_radius;
    float u_enabled;
    vec4 u_accent;
};


#line 27
layout(std140) uniform ElementData
{
    vec2 u_pos;
    vec2 u_size;
    float u_radius;
    float u_enabled;
    vec4 u_accent;
};

#line 20
struct SLANG_ParameterGroup_FrameData_0
{
    vec2 u_mouse;
    float u_time;
    float u_pad0;
};


#line 20
layout(std140) uniform FrameData
{
    vec2 u_mouse;
    float u_time;
    float u_pad0;
};

#line 36
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 36
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 49
float roundedBoxSDF_0(vec2 p_0, vec2 halfSize_0, float r_0)
{

#line 50
    vec2 q_0 = abs(p_0) - halfSize_0 + r_0;
    return length(max(q_0, 0.0)) + min(max(q_0.x, q_0.y), 0.0) - r_0;
}


#line 59
float hash2D_0(vec2 p_1)
{
    return fract(sin(dot(p_1, vec2(12.98980045318603516, 78.233001708984375))) * 43758.546875);
}


#line 46
vec4 _S1;


#line 46
out vec4 entryPointParam_main_fragColor_0;


#line 46
in vec2 gemini_varying_1;


#line 66
void main()
{
    vec2 halfSize_1 = u_size * 0.5;


    vec2 uv_0 = (gemini_varying_1 - u_pos) / u_size;


    float dist_0 = roundedBoxSDF_0(gemini_varying_1 - (u_pos + halfSize_1), halfSize_1, u_radius);

    float mask_0 = 1.0 - smoothstep(0.0, (fwidth((dist_0))), dist_0);
    if(mask_0 < 0.00499999988824129)
    {

#line 77
        discard;

#line 77
    }

#line 83
    float _S2 = uv_0.y;

#line 92
    float _S3 = uv_0.x;

#line 116
    vec3 baseColor_0 = mix(vec3(0.07000000029802322, 0.07999999821186066, 0.10000000149011612), vec3(0.10000000149011612, 0.10999999940395355, 0.13500000536441803), _S2) + vec3(exp(- _S2 * 18.0) * 0.10000000149011612) + clamp(pow(1.0 - min(min(_S3, 1.0 - _S3), min(_S2, 1.0 - _S2)) * 3.0, 4.0), 0.0, 1.0) * vec3(0.03999999910593033, 0.04500000178813934, 0.05999999865889549) + (hash2D_0(gemini_varying_1 + u_time * 4.0) - 0.5) * 0.01200000010430813 + (0.5 + vec3(sin(u_time * 0.20000000298023224), sin(u_time * 0.15000000596046448 + 2.0), sin(u_time * 0.18000000715255737 + 4.0)) * 0.5) * 0.01999999955296516 + exp(- length(gemini_varying_1 - u_mouse) * 0.00600000005215406) * 0.07999999821186066 * vec3(0.55000001192092896, 0.57999998331069946, 0.68000000715255737);


    vec3 accentRGB_0 = u_accent.xyz;

#line 119
    vec3 baseColor_1;
    if((u_enabled) > 0.5)
    {

#line 120
        baseColor_1 = baseColor_0 + accentRGB_0 * 0.10000000149011612 + accentRGB_0 * (1.0 - smoothstep(-3.0, -0.5, dist_0)) * 0.30000001192092896 + accentRGB_0 * (exp(- abs(dist_0) * 0.11999999731779099) * 0.05999999865889549);

#line 120
    }
    else
    {

#line 120
        baseColor_1 = baseColor_0;

#line 120
    }

#line 139
    vec4 _S4 = vec4(baseColor_1 * ColorModulator.xyz, mask_0 * ColorModulator.w * 0.89999997615814209);

#line 139
    _S1 = _S4;

#line 139
    entryPointParam_main_fragColor_0 = _S4;

#line 139
    return;
}

