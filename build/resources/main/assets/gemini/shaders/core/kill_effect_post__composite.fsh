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


#line 1570
void main()
{


    vec4 _S2 = vec4((texture((SceneSampler), (gemini_varying_0))).xyz + (texture((BloomSampler), (gemini_varying_0))).xyz * Params.z, 1.0);

#line 1574
    _S1 = _S2;

#line 1574
    entryPointParam_main_fragColor_0 = _S2;

#line 1574
    return;
}

