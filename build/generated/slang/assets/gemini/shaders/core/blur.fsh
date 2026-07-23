#version 330 core
#line 15 0
struct SLANG_ParameterGroup_BlurUniforms_0
{
    vec3 Params1;
    vec4 Params2;
    vec4 Color1;
    vec4 Params3;
};


#line 15
layout(std140) uniform BlurUniforms
{
    vec3 Params1;
    vec4 Params2;
    vec4 Color1;
    vec4 Params3;
};

#line 13
uniform sampler2D InputSampler;


#line 24
float roundRectDistance_0(vec2 position_0, vec4 innerRect_0, vec4 radius_0)
{

#line 25
    vec2 _S1 = innerRect_0.zw;

#line 25
    vec2 _S2 = innerRect_0.xy;

    vec2 p_0 = position_0 - (_S2 + _S1) * 0.5;

    vec2 s_0 = step(vec2(0.0), p_0);

    float _S3 = s_0.y;

#line 30
    float _S4 = mix(mix(radius_0.x, radius_0.w, _S3), mix(radius_0.y, radius_0.z, _S3), s_0.x);

#line 36
    vec2 q_0 = abs(p_0) - (_S1 - _S2) * 0.5 + _S4;
    return length(max(q_0, 0.0)) + min(max(q_0.x, q_0.y), 0.0) - _S4;
}

vec4 blur_0()
{

#line 41
    vec2 inputResolution_0 = Params1.xy;
    vec2 uv_0 = gl_FragCoord.xy / inputResolution_0.xy;
    vec4 _S5 = (texture((InputSampler), (uv_0)));



    if((Params1.z) <= 0.0)
    {

#line 48
        return _S5 + Color1;
    }

    vec2 _S6 = Params1.zz / inputResolution_0;

#line 56
    const vec2  _S7[8] = vec2[](vec2(1.0, 0.0), vec2(0.92387950420379639, 0.38268342614173889), vec2(0.70710676908493042, 0.70710676908493042), vec2(0.38268342614173889, 0.92387950420379639), vec2(0.0, 1.0), vec2(-0.38268342614173889, 0.92387950420379639), vec2(-0.70710676908493042, 0.70710676908493042), vec2(-0.92387950420379639, 0.38268342614173889));

#line 56
    int d_0 = 0;

#line 56
    vec4 color_0 = _S5;

#line 67
    for(;;)
    {

#line 67
        if(d_0 < 8)
        {
        }
        else
        {

#line 67
            break;
        }

#line 68
        vec2 _S8 = _S7[d_0] * _S6;

#line 68
        int i_0 = 1;
        for(;;)
        {

#line 69
            if(i_0 <= 5)
            {
            }
            else
            {

#line 69
                break;
            }

#line 70
            vec2 offset_0 = _S8 * (float(i_0) * 0.20000000298023224);
            vec2 _S9 = uv_0 + offset_0;
            vec4 color_1 = color_0 + (texture((InputSampler), (_S9))) + (texture((InputSampler), (uv_0 - offset_0)));

#line 69
            i_0 = i_0 + 1;

#line 69
            color_0 = color_1;

#line 69
        }

#line 67
        d_0 = d_0 + 1;

#line 67
    }

#line 76
    return color_0 / 81.0 + Color1;
}


#line 22
vec4 _S10;


#line 22
out vec4 entryPointParam_main_fragColor_0;


#line 81
void main()
{
    vec2 uLocation_0 = Params2.zw;

#line 92
    float dist_0 = roundRectDistance_0(vec2(gl_FragCoord.x, Params1.y - gl_FragCoord.y), vec4(uLocation_0, uLocation_0 + Params2.xy), Params3);
    float delta_0 = (fwidth((dist_0)));
    float alpha_0 = 1.0 - smoothstep(- delta_0, delta_0, dist_0);

    if(alpha_0 < 0.00100000004749745)
    {

#line 96
        discard;

#line 96
    }
    vec4 _S11 = vec4(blur_0().xyz, alpha_0);

#line 97
    _S10 = _S11;

#line 97
    entryPointParam_main_fragColor_0 = _S11;

#line 97
    return;
}

