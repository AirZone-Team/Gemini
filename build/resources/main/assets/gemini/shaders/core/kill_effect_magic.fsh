#version 330 core
#line 18 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 18
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 63
mat2x2 rot_0(float a_0)
{

#line 64
    float c_0 = cos(a_0);
    float s_0 = sin(a_0);
    return mat2x2(c_0, - s_0, s_0, c_0);
}


#line 75
float ring_0(vec2 uv_0, float outer_0, float inner_0, float blur_0)
{

#line 76
    float d_0 = length(uv_0);
    return smoothstep(inner_0 - blur_0, inner_0 + blur_0, d_0) * (1.0 - smoothstep(outer_0 - blur_0, outer_0 + blur_0, d_0));
}


#line 71
float circle_0(vec2 uv_1, float r_0, float blur_1)
{

#line 72
    return 1.0 - smoothstep(r_0 - blur_1, r_0 + blur_1, length(uv_1));
}


#line 32
float hash_0(vec2 p_0)
{

#line 33
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}

float noise_0(vec2 p_1)
{

#line 37
    vec2 i_0 = floor(p_1);
    vec2 _S1 = fract(p_1);
    vec2 f_0 = _S1 * _S1 * (3.0 - 2.0 * _S1);

#line 46
    float _S2 = f_0.x;

#line 46
    return mix(mix(hash_0(i_0), hash_0(i_0 + vec2(1.0, 0.0)), _S2), mix(hash_0(i_0 + vec2(0.0, 1.0)), hash_0(i_0 + vec2(1.0, 1.0)), _S2), f_0.y);
}

float fbm_0(vec2 p_2)
{

    const vec2 _S3 = vec2(100.0);

#line 52
    vec2 _S4 = p_2;

#line 52
    int i_1 = 0;

#line 52
    float a_1 = 0.5;

#line 52
    float v_0 = 0.0;
    for(;;)
    {

#line 53
        if(i_1 < 5)
        {
        }
        else
        {

#line 53
            break;
        }

#line 54
        float v_1 = v_0 + a_1 * noise_0(_S4);
        vec2 _S5 = _S4 * 2.0 + _S3;
        float a_2 = a_1 * 0.5;

#line 53
        int _S6 = i_1 + 1;

#line 53
        _S4 = _S5;

#line 53
        i_1 = _S6;

#line 53
        a_1 = a_2;

#line 53
        v_0 = v_1;

#line 53
    }

#line 58
    return v_0;
}


#line 28
vec4 _S7;


#line 28
out vec4 entryPointParam_main_fragColor_0;


#line 28
in vec4 gemini_varying_0;


#line 28
in vec2 gemini_varying_1;


#line 85
void main()
{

#line 86
    float time_0 = gemini_varying_0.x;
    float stage_0 = gemini_varying_0.y * 8.0;
    float intensity_0 = gemini_varying_0.z;
    float alpha_0 = gemini_varying_0.w;


    vec2 uv_2 = (gemini_varying_1 - 0.5) * 2.0;

    float d_1 = length(uv_2);
    float _S8 = (atan((uv_2.y),(uv_2.x)));

#line 95
    float rotSpeed_0;



    if(stage_0 > 1.5)
    {

#line 99
        rotSpeed_0 = 1.5;

#line 99
    }
    else
    {

#line 99
        rotSpeed_0 = 0.80000001192092896;

#line 99
    }
    float rotation_0 = time_0 * rotSpeed_0 * 3.14159011840820312;



    const vec3 gold_0 = vec3(1.0, 0.75, 0.15000000596046448);
    const vec3 brightGold_0 = vec3(1.0, 0.87999999523162842, 0.34999999403953552);
    const vec3 whiteGold_0 = vec3(1.0, 0.94999998807907104, 0.69999998807907104);


    float outerRing_0 = ring_0(uv_2, 0.94999998807907104, 0.87999999523162842, 0.00800000037997961);
    float innerRing_0 = ring_0(uv_2, 0.81999999284744263, 0.77999997138977051, 0.00600000005215406);
    float thinRing_0 = ring_0(uv_2, 0.60000002384185791, 0.58499997854232788, 0.00400000018998981);

#line 135
    float rune_0 = step(0.92000001668930054, abs(sin(_S8 * 4.0 + rotation_0 * 0.5))) * ring_0(uv_2, 0.93000000715255737, 0.80000001192092896, 0.00999999977648258) + step(0.94999998807907104, abs(sin(_S8 * 8.0 + rotation_0 * 0.30000001192092896))) * ring_0(uv_2, 0.85000002384185791, 0.75, 0.00800000037997961) * 0.69999998807907104 + step(0.93999999761581421, abs(sin(_S8 * 16.0 + rotation_0 * 0.69999998807907104))) * ring_0(uv_2, 0.62000000476837158, 0.55000001192092896, 0.00499999988824129) * 0.5;

#line 141
    float spokes_0 = smoothstep(0.9649999737739563, 0.99500000476837158, abs(cos(_S8 * 6.0 - rotation_0 * 0.64999997615814209))) * ring_0(uv_2, 0.75999999046325684, 0.2800000011920929, 0.00600000005215406) * (1.0 - smoothstep(0.2800000011920929, 0.40000000596046448, abs(fract(d_1 * 7.0 - time_0) - 0.5)));



    float glyphs_0 = smoothstep(0.72000002861022949, 0.93999999761581421, sin(_S8 * 24.0 + rotation_0 * 0.34999999403953552) * 0.5 + 0.5) * ring_0(uv_2, 0.72000002861022949, 0.6600000262260437, 0.00600000005215406);


    float orbitSpark_0 = exp(- pow(d_1 - 0.51999998092651367, 2.0) * 900.0) * pow(max(0.0, sin(_S8 * 12.0 - rotation_0 * 2.0)), 18.0);

#line 158
    float pulse_0 = (sin(d_1 * 15.0 - time_0 * 8.0) * 0.5 + 0.5) * (exp(- d_1 * 2.5) * 0.20000000298023224) * smoothstep(0.0, 0.30000001192092896, time_0);


    float ringTotal_0 = outerRing_0 + innerRing_0 * 0.80000001192092896 + thinRing_0 * 0.60000002384185791;
    float circleTotal_0 = circle_0(uv_2, 0.25, 0.0) * 0.30000001192092896 + circle_0(uv_2, 0.07999999821186066, 0.00999999977648258) * 0.60000002384185791;

#line 175
    vec3 rgb_0 = mix(gold_0, brightGold_0, fbm_0((((uv_2) * (rot_0(- rotation_0)))) * 6.0 + time_0 * 0.20000000298023224) * 0.15000000596046448 * (outerRing_0 + innerRing_0 + thinRing_0) + rune_0 * 0.30000001192092896) * ringTotal_0 + mix(brightGold_0, whiteGold_0, rune_0 * 0.5) * rune_0 * 0.89999997615814209 + whiteGold_0 * spokes_0 * 0.41999998688697815 + brightGold_0 * glyphs_0 * 0.64999997615814209 + whiteGold_0 * orbitSpark_0 * 1.79999995231628418 + gold_0 * circleTotal_0 + whiteGold_0 * pulse_0;

#line 175
    vec3 rgb_1;


    if(stage_0 < 1.5)
    {

#line 178
        rgb_1 = rgb_0 * (smoothstep(0.0, 0.25, time_0) * smoothstep(0.0, 0.15000000596046448, d_1));

#line 178
    }
    else
    {

#line 178
        rgb_1 = rgb_0;

#line 178
    }

#line 188
    vec4 _S9 = vec4(rgb_1 * intensity_0, (ringTotal_0 * 0.69999998807907104 + rune_0 * 0.80000001192092896 + spokes_0 * 0.34999999403953552 + glyphs_0 * 0.55000001192092896 + orbitSpark_0 + circleTotal_0 * 0.5 + pulse_0) * alpha_0) * ColorModulator;

#line 188
    _S7 = _S9;

    if((_S9.w) < 0.0020000000949949)
    {

#line 191
        discard;

#line 190
    }

#line 190
    entryPointParam_main_fragColor_0 = _S7;

#line 190
    return;
}

