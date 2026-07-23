#version 330 core
#line 41 0
uniform sampler2D SceneSampler;


#line 42
uniform sampler2D BloomSampler;


#line 44
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
vec4 _S1;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 1498
void main()
{

#line 1499
    vec3 current_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;
    vec3 prev_0 = (texture((BloomSampler), (gemini_varying_0))).xyz;



    float strength_0 = 1.0 - BHParams.z;
    float strength_1 = strength_0 * strength_0;
    if(strength_1 < 0.00999999977648258)
    {

#line 1507
        vec4 _S2 = vec4(current_0, 1.0);

#line 1507
        _S1 = _S2;

#line 1507
        entryPointParam_main_fragColor_0 = _S2;

#line 1507
        return;
    }

#line 1518
    vec4 _S3 = vec4(current_0 + prev_0 * 0.5 * strength_1 + dot(prev_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464)) * 0.15000000596046448 * strength_1, 1.0);

#line 1518
    _S1 = _S3;

#line 1518
    entryPointParam_main_fragColor_0 = _S3;

#line 1518
    return;
}

