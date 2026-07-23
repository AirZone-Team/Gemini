#version 330 core
#line 44 0
struct SLANG_ParameterGroup_PostUniforms_0
{
    vec4 Params;
    vec4 TimePack;
    vec4 Center1;
    vec4 Center2;
    vec4 PassParams;
    vec4 BHParams;
    vec4 CameraParams;
    vec4 LightViewPos;
    vec4 LightColor;
    vec4 MiscParams;
};


#line 44
layout(std140) uniform PostUniforms
{
    vec4 Params;
    vec4 TimePack;
    vec4 Center1;
    vec4 Center2;
    vec4 PassParams;
    vec4 BHParams;
    vec4 CameraParams;
    vec4 LightViewPos;
    vec4 LightColor;
    vec4 MiscParams;
};

#line 41
uniform sampler2D SceneSampler;


#line 379
uniform sampler2D DepthSampler;


#line 42
uniform sampler2D BloomSampler;


#line 58
vec4 _S1;


#line 384
vec3 viewPosFromDepth_0(vec2 uv_0, float depth_0)
{

    float near_0 = CameraParams.z;
    float far_0 = CameraParams.w;

#line 396
    float viewZ_0 = 2.0 * far_0 * near_0 / (far_0 + near_0 - (depth_0 * 2.0 - 1.0) * (far_0 - near_0));

    float halfFovTan_0 = tan(CameraParams.x * 0.5);



    return vec3((uv_0.x * 2.0 - 1.0) * viewZ_0 * CameraParams.y * halfFovTan_0, (uv_0.y * 2.0 - 1.0) * viewZ_0 * halfFovTan_0, viewZ_0);
}


#line 73
float postHash_0(vec2 p_0)
{

#line 74
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}


#line 406
float phaseHG_0(float cosTheta_0, float g_0)
{

#line 407
    float g2_0 = g_0 * g_0;
    float denom_0 = 1.0 + g2_0 - 2.0 * g_0 * cosTheta_0;
    return (1.0 - g2_0) / (12.56637096405029297 * denom_0 * sqrt(denom_0));
}


#line 409
out vec4 entryPointParam_main_fragColor_0;


#line 409
in vec2 gemini_varying_0;



void main()
{

#line 415
    float strength_0 = PassParams.y;
    vec3 scene_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;
    float depth_1 = (texture((DepthSampler), (gemini_varying_0))).x;

    if(strength_0 < 0.00999999977648258)
    {

#line 420
        vec4 _S2 = vec4(scene_0, 1.0);

#line 420
        _S1 = _S2;

#line 420
        entryPointParam_main_fragColor_0 = _S2;

#line 420
        return;
    }



    vec3 viewPos_0 = viewPosFromDepth_0(gemini_varying_0, depth_1);


    vec3 lightPos_0 = LightViewPos.xyz;
    float lightRadius_0 = LightViewPos.w;

    vec3 toLight_0 = lightPos_0 - viewPos_0;
    float distToLight_0 = length(toLight_0);
    vec3 _S3 = toLight_0 / max(distToLight_0, 0.00100000004749745);

    if(distToLight_0 > (lightRadius_0 * 4.0))
    {
        vec4 _S4 = vec4(scene_0, 1.0);

#line 437
        _S1 = _S4;

#line 437
        entryPointParam_main_fragColor_0 = _S4;

#line 437
        return;
    }

#line 443
    int steps_0 = clamp(int(MiscParams.y), 8, 32);
    float stepSize_0 = distToLight_0 / float(steps_0);


    float jitter_0 = postHash_0(gemini_varying_0 + fract(TimePack.x)) * stepSize_0;


    const vec3 _S5 = vec3(0.0);

#line 450
    int i_0 = 0;

#line 450
    float t_0 = jitter_0;

#line 450
    float transmittance_0 = 1.0;

#line 450
    vec3 accumulated_0 = _S5;


    for(;;)
    {

#line 453
        if(i_0 < 32)
        {
        }
        else
        {

#line 453
            break;
        }

#line 454
        if(i_0 >= steps_0)
        {

#line 454
            break;
        }

        float d_0 = length(lightPos_0 - (viewPos_0 + _S3 * t_0));


        float density_0 = exp(- d_0 * d_0 / (lightRadius_0 * lightRadius_0 * 0.25));

#line 460
        vec3 accumulated_1;

        if(density_0 > 0.0020000000949949)
        {
            float _S6 = density_0 * stepSize_0;
            float transmittance_1 = transmittance_0 * exp(- (_S6 * 0.11999999731779099));

#line 476
            vec3 accumulated_2 = accumulated_0 + LightColor.xyz * LightColor.w / (1.0 + d_0 * d_0 * 0.0007999999797903) * (_S6 * phaseHG_0(dot(normalize(- viewPos_0), _S3), 0.64999997615814209) * transmittance_1 * 0.02500000037252903);

#line 476
            transmittance_0 = transmittance_1;

#line 476
            accumulated_1 = accumulated_2;

#line 462
        }
        else
        {

#line 462
            accumulated_1 = accumulated_0;

#line 462
        }

#line 479
        float t_1 = t_0 + stepSize_0;
        if(t_1 > distToLight_0)
        {

#line 480
            accumulated_0 = accumulated_1;

#line 480
            break;
        }

#line 453
        i_0 = i_0 + 1;

#line 453
        t_0 = t_1;

#line 453
        accumulated_0 = accumulated_1;

#line 453
    }

#line 484
    vec3 godRays_0 = accumulated_0 * strength_0;

#line 491
    vec4 _S7 = vec4(scene_0 + mix(godRays_0, godRays_0 * LightColor.xyz, exp(- distToLight_0 / (lightRadius_0 * 1.5)) * 0.40000000596046448), 1.0);

#line 491
    _S1 = _S7;

#line 491
    entryPointParam_main_fragColor_0 = _S7;

#line 491
    return;
}

