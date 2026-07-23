#version 330 core
#line 41 0
uniform sampler2D SceneSampler;

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

#line 42
uniform sampler2D BloomSampler;


#line 58
vec4 _S1;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 1433
void main()
{

#line 1434
    vec3 scene_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;

#line 1434
    float flashIntensity_0;



    if((abs(BHParams.y - 7.0)) < 0.5)
    {

#line 1438
        flashIntensity_0 = BHParams.w;

#line 1438
    }
    else
    {
        if((abs(BHParams.y - 8.0)) < 0.5)
        {


            float p_0 = BHParams.z;

            float reignitePhase_0 = (p_0 - 0.34000000357627869) / 0.05499999970197678;

#line 1447
            flashIntensity_0 = (exp(- p_0 * 6.0) + exp(- reignitePhase_0 * reignitePhase_0) * 0.41999998688697815) * BHParams.w * smoothstep(0.0, 0.05000000074505806, p_0);

#line 1441
        }
        else
        {

#line 1441
            flashIntensity_0 = BHParams.z;

#line 1441
        }

#line 1438
    }

#line 1454
    if(flashIntensity_0 < 0.00100000004749745)
    {

#line 1455
        vec4 _S2 = vec4(scene_0, 1.0);

#line 1455
        _S1 = _S2;

#line 1455
        entryPointParam_main_fragColor_0 = _S2;

#line 1455
        return;
    }

#line 1461
    float flash_0 = clamp(flashIntensity_0, 0.0, 1.0);
    float flash_1 = flash_0 * flash_0 * (3.0 - 2.0 * flash_0);

#line 1476
    vec4 _S3 = vec4(mix(mix(scene_0, mix(vec3(1.0, 0.94999998807907104, 0.85000002384185791), vec3(1.0, 1.0, 1.0), flash_1) * 1.5, flash_1 * (1.0 - length(gemini_varying_0 - 0.5) * 0.5)), vec3(1.0), smoothstep(0.80000001192092896, 1.0, flash_1) * 0.89999997615814209), 1.0);

#line 1476
    _S1 = _S3;

#line 1476
    entryPointParam_main_fragColor_0 = _S3;

#line 1476
    return;
}

