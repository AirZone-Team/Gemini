#version 330 core
#line 10 0
struct SLANG_ParameterGroup_SweepUniforms_0
{
    vec4 Params;
    vec4 Geometry;
    vec4 Style;
    vec4 Motion;
    vec4 Primary;
    vec4 Accent;
    vec4 Core;
    vec4 Misc;
};


#line 10
layout(std140) uniform SweepUniforms
{
    vec4 Params;
    vec4 Geometry;
    vec4 Style;
    vec4 Motion;
    vec4 Primary;
    vec4 Accent;
    vec4 Core;
    vec4 Misc;
};

#line 3
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 3
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 27
vec3 spectrum_0(float t_0)
{

#line 28
    return 0.56000000238418579 + 0.43999999761581421 * cos(6.28318548202514648 * (t_0 + vec3(0.0, 0.68000000715255737, 0.34999999403953552)));
}

vec3 palette_0(float t_1)
{

#line 32
    int mode_0 = int(Style.y + 0.5);
    if(mode_0 == 1)
    {

#line 33
        return mix(Primary.xyz, Accent.xyz, t_1);
    }

#line 34
    if(mode_0 == 2)
    {

#line 34
        return spectrum_0(t_1 - Params.x * Motion.x * 0.07999999821186066);
    }

#line 35
    if(mode_0 == 3)
    {
        return mix(Primary.xyz, Accent.xyz, 0.5 + 0.5 * sin(Params.x * Motion.x * 4.0 + t_1 * 6.28318548202514648));
    }
    return Primary.xyz;
}


#line 86
in vec2 gemini_varying_0;


#line 86
in vec4 gemini_varying_1;


#line 42
vec4 particleMode_0()
{

#line 43
    vec2 p_0 = (gemini_varying_0 - 0.5) * 2.0;
    float distanceToCenter_0 = length(p_0);
    float _S1 = (atan((p_0.y),(p_0.x)));
    float star_0 = 0.72000002861022949 + 0.2800000011920929 * cos(_S1 * (4.0 + mod(Style.x, 3.0) * 2.0));

    float _S2 = - distanceToCenter_0;

#line 48
    float core_0 = exp(_S2 * distanceToCenter_0 * 11.0);

    float energy_0 = (core_0 + pow(abs(cos(_S1 * 2.0)), 22.0) * exp(_S2 * 2.5) * 0.5 + exp(_S2 * 4.0) * 0.2800000011920929) * smoothstep(star_0, star_0 - 0.2800000011920929, distanceToCenter_0);

#line 55
    return vec4(mix(palette_0(gemini_varying_1.z), Core.xyz, core_0) * (energy_0 * (0.86000001430511475 + 0.14000000059604645 * sin(Params.x * 28.0 + gemini_varying_1.z * 31.0)) * (1.0 + Geometry.w * core_0 * 1.60000002384185791)), energy_0 * gemini_varying_1.x * gemini_varying_1.w * Primary.w);
}

vec4 speedLineMode_0()
{

#line 59
    float crossGlow_0 = exp(- abs(gemini_varying_0.y) * 4.80000019073486328);


    float head_0 = exp(- abs(gemini_varying_0.x - 0.72000002861022949) * 7.0);

    float energy_1 = crossGlow_0 * (smoothstep(0.0, 0.12999999523162842, gemini_varying_0.x) * smoothstep(1.0, 0.47999998927116394, gemini_varying_0.x)) * (0.68000000715255737 + 0.31999999284744263 * sin(gemini_varying_0.x * 23.0 - Params.x * Motion.x * 9.0) + head_0 * 0.80000001192092896);


    return vec4(mix(palette_0(gemini_varying_1.x), Core.xyz, head_0 * 0.64999997615814209) * (energy_1 * (1.0 + Geometry.w * crossGlow_0)), energy_1 * gemini_varying_1.w * Primary.w);
}

vec4 lightningMode_0()
{

#line 71
    float coreLine_0 = exp(- abs(gemini_varying_0.y) * 5.80000019073486328);

#line 76
    float energy_2 = (coreLine_0 + exp(- abs(gemini_varying_0.y) * 1.64999997615814209) * 0.34000000357627869) * (smoothstep(0.0, 0.07999999821186066, gemini_varying_0.x) * smoothstep(1.0, 0.89999997615814209, gemini_varying_0.x)) * (0.72000002861022949 + 0.2800000011920929 * sin(Params.x * 51.0 + gemini_varying_1.x * 27.0));


    return vec4(mix(Accent.xyz, Core.xyz, coreLine_0) * (energy_2 * (1.29999995231628418 + Geometry.w * coreLine_0 * 2.20000004768371582)), clamp(energy_2, 0.0, 1.0) * gemini_varying_1.w * Primary.w);
}


#line 23
vec4 _S3;


#line 23
out vec4 entryPointParam_main_fragColor_0;


#line 84
void main()
{

#line 84
    vec4 result_0;

    if((gemini_varying_1.y) < 0.5)
    {

#line 86
        result_0 = particleMode_0();

#line 86
    }
    else
    {

#line 87
        if((gemini_varying_1.z) < 0.5)
        {

#line 87
            result_0 = speedLineMode_0();

#line 87
        }
        else
        {

#line 87
            result_0 = lightningMode_0();

#line 87
        }

#line 86
    }


    vec4 _S4 = result_0 * ColorModulator;

#line 89
    _S3 = _S4;
    if((_S4.w) < 0.0020000000949949)
    {

#line 90
        discard;

#line 90
    }

#line 90
    entryPointParam_main_fragColor_0 = _S3;

#line 90
    return;
}

