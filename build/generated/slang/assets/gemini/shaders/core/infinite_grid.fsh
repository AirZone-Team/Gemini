#version 330 core
#line 10 0
struct SLANG_ParameterGroup_GridUniforms_0
{
    vec4 Params;
};


#line 10
layout(std140) uniform GridUniforms
{
    vec4 Params;
};

#line 19
float hash_0(vec2 p_0)
{

#line 20
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise_0(vec2 p_1)
{

#line 24
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);

#line 33
    float _S2 = f_0.x;

#line 33
    return mix(mix(hash_0(i_0), hash_0(i_0 + vec2(1.0, 0.0)), _S2), mix(hash_0(i_0 + vec2(0.0, 1.0)), hash_0(i_0 + vec2(1.0, 1.0)), _S2), f_0.y);
}

float fbm_0(vec2 p_2)
{

#line 36
    int i_1 = 0;

#line 36
    float amplitude_0 = 0.5;

#line 36
    float frequency_0 = 1.0;

#line 36
    float value_0 = 0.0;



    for(;;)
    {

#line 40
        if(i_1 < 5)
        {
        }
        else
        {

#line 40
            break;
        }

#line 41
        float value_1 = value_0 + amplitude_0 * noise_0(p_2 * frequency_0);
        float amplitude_1 = amplitude_0 * 0.5;
        float frequency_1 = frequency_0 * 2.0;

#line 40
        i_1 = i_1 + 1;

#line 40
        amplitude_0 = amplitude_1;

#line 40
        frequency_0 = frequency_1;

#line 40
        value_0 = value_1;

#line 40
    }

#line 45
    return value_0;
}


#line 15
vec4 _S3;


#line 15
out vec4 entryPointParam_main_fragColor_0;


#line 15
in vec2 gemini_varying_0;


#line 52
void main()
{

#line 53
    vec2 resolution_0 = Params.xy;
    float time_0 = Params.z;
    float aspect_0 = resolution_0.x / resolution_0.y;

    vec2 uv_0 = gemini_varying_0;
    uv_0[0] = uv_0[0] * aspect_0;


    const vec3 color_0 = vec3(0.01999999955296516);



    vec2 gridUv_0 = uv_0;
    gridUv_0[1] = gridUv_0[1] + time_0 * 0.02999999932944775;


    gridUv_0[0] = gridUv_0[0] * (1.0 / (gridUv_0.y * 2.5 + 1.0));

    vec2 _S4 = fract(gridUv_0 * 40.0);


    float _S5 = _S4.x;
    float _S6 = _S4.y;

#line 111
    vec4 _S7 = vec4(color_0 + vec3(0.07999999821186066) * (max(1.0 - smoothstep(0.0, 0.00400000018998981, min(_S5, 1.0 - _S5)), 1.0 - smoothstep(0.0, 0.00400000018998981, min(_S6, 1.0 - _S6))) * (1.0 - smoothstep(0.0, 0.60000002384185791, gemini_varying_0.y)) * (smoothstep(0.0, 0.15000000596046448, gemini_varying_0.x) * smoothstep(0.0, 0.15000000596046448, 1.0 - gemini_varying_0.x))) + color_0 * fbm_0(uv_0 * 3.0 + time_0 * 0.05000000074505806) + vec3(0.25, 0.44999998807907104, 1.0) * (fbm_0(vec2(uv_0.x * 2.0 + time_0 * 0.07999999821186066, uv_0.y * 1.5)) * smoothstep(0.30000001192092896, 0.69999998807907104, gemini_varying_0.y) * smoothstep(0.80000001192092896, 0.40000000596046448, gemini_varying_0.y) * mix(0.60000002384185791, 1.0, sin(uv_0.x * 4.0 + time_0 * 0.20000000298023224) * 0.5 + 0.5)) * 0.02999999932944775, 1.0);

#line 111
    _S3 = _S7;

#line 111
    entryPointParam_main_fragColor_0 = _S7;

#line 111
    return;
}

