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


#line 66
vec3 aces_0(vec3 x_0)
{
    return clamp(x_0 * (2.50999999046325684 * x_0 + 0.02999999932944775) / (x_0 * (2.43000006675720215 * x_0 + 0.5899999737739563) + 0.14000000059604645), 0.0, 1.0);
}


#line 58
vec4 _S1;


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 1541
void main()
{

#line 1542
    vec3 hdr_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;

#line 1549
    vec2 uvC_0 = gemini_varying_0 - 0.5;

#line 1555
    vec4 _S2 = vec4(mix(clamp(hdr_0, 0.0, 1.0), aces_0(hdr_0) * (1.0 - dot(uvC_0, uvC_0) * 0.34999999403953552), clamp(MiscParams.z, 0.0, 1.0)), 1.0);

#line 1555
    _S1 = _S2;

#line 1555
    entryPointParam_main_fragColor_0 = _S2;

#line 1555
    return;
}

