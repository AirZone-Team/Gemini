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


#line 42
uniform sampler2D BloomSampler;


#line 58
vec4 _S1;


#line 1238
float gfHash_0(vec2 p_0)
{

#line 1239
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}


#line 1239
out vec4 entryPointParam_main_fragColor_0;


#line 1239
in vec2 gemini_varying_0;



void main()
{

    float dist_0 = length(gemini_varying_0 - (Center1.xy * 0.5 + 0.5));

    float flashIntensity_0 = BHParams.w;
    float _S2 = max(BHParams.z, 0.00999999977648258);

    if(flashIntensity_0 < 0.00999999977648258)
    {

#line 1253
        vec4 _S3 = (texture((SceneSampler), (gemini_varying_0)));

#line 1253
        _S1 = _S3;

#line 1253
        entryPointParam_main_fragColor_0 = _S3;

#line 1253
        return;
    }


    float time_0 = TimePack.x;

#line 1265
    float flashMod_0 = flashIntensity_0 * mix(sin(time_0 * 70.0 + gfHash_0(vec2(floor(time_0 * 4.0), 0.0)) * 6.28000020980834961) * 0.5 + 0.5, 1.0, 0.30000001192092896) * (1.0 - smoothstep(0.85000002384185791, 1.0, _S2));


    float ringRadius_0 = _S2 * 1.20000004768371582;


    float _S4 = - dist_0;

#line 1271
    float core_0 = exp(_S4 * 80.0) * flashMod_0 * 200.0;


    float innerGlow_0 = exp(_S4 * 15.0) * flashMod_0 * 30.0;

#line 1281
    float ring_0 = exp(- abs(dist_0 - ringRadius_0) * 35.0) * flashMod_0 * 15.0 + exp(- abs(dist_0 - ringRadius_0 * 0.5) * 30.0) * flashMod_0 * 6.0 + exp(- abs(dist_0 - ringRadius_0 * 1.60000002384185791) * 18.0) * flashMod_0 * 3.0;


    float wideHalo_0 = exp(_S4 / (ringRadius_0 * 1.5 + 0.10000000149011612)) * flashMod_0 * 8.0;

#line 1292
    vec3 ringColor_0 = mix(mix(vec3(1.0, 1.0, 1.0), vec3(0.40000000596046448, 0.64999997615814209, 1.0), smoothstep(0.0, 0.5, _S2)), vec3(0.60000002384185791, 0.20000000298023224, 1.0), smoothstep(0.5, 1.0, _S2));

    vec3 haloColor_0 = mix(vec3(0.69999998807907104, 0.34999999403953552, 1.0), vec3(0.30000001192092896, 0.10000000149011612, 0.69999998807907104), _S2);

#line 1305
    vec4 _S5 = vec4((texture((SceneSampler), (gemini_varying_0))).xyz + (vec3(core_0) + ringColor_0 * (innerGlow_0 + ring_0) + haloColor_0 * wideHalo_0 + vec3(0.40000000596046448, 0.30000001192092896, 1.0) * flashMod_0 * 0.80000001192092896), 1.0);

#line 1305
    _S1 = _S5;

#line 1305
    entryPointParam_main_fragColor_0 = _S5;

#line 1305
    return;
}

