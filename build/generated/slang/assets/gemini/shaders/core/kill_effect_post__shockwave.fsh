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


#line 58
out vec4 entryPointParam_main_fragColor_0;


#line 58
in vec2 gemini_varying_0;


#line 1331
void main()
{
    vec2 aspect_0 = vec2(1.0, Params.y / Params.x);
    vec2 delta_0 = (gemini_varying_0 - (Center1.xy * 0.5 + 0.5)) * aspect_0;
    float dist_0 = length(delta_0);
    vec2 dir_0 = delta_0 / max(dist_0, 0.00009999999747379);

    float p_0 = clamp(BHParams.z, 0.0, 1.0);
    float intensity_0 = BHParams.w;


    float e_0 = 1.0 - pow(1.0 - p_0, 3.0);


    float R1_0 = e_0 * 1.35000002384185791;

#line 1350
    float _S2 = (atan((delta_0.y),(delta_0.x)));
    float turb_0 = 0.80000001192092896 + 0.20000000298023224 * sin(_S2 * 14.0 + dist_0 * 30.0 + p_0 * 18.0) * sin(_S2 * 5.0 - p_0 * 9.0);


    float w1_0 = exp(- abs(dist_0 - R1_0 * turb_0) * 26.0);
    float w2_0 = exp(- abs(dist_0 - (e_0 * 0.94999998807907104 + 0.01499999966472387) * turb_0) * 22.0) * 0.55000001192092896;
    float w3_0 = exp(- abs(dist_0 - (e_0 * 0.60000002384185791 + 0.00800000037997961) * turb_0) * 18.0) * 0.30000001192092896;


    float p4_0 = clamp((p_0 - 0.2199999988079071) / 0.77999997138977051, 0.0, 1.0);



    float w4_0 = exp(- abs(dist_0 - (1.0 - pow(1.0 - p4_0, 3.0)) * 1.22000002861022949 * turb_0) * 28.0) * smoothstep(0.0, 0.07999999821186066, p4_0) * (1.0 - smoothstep(0.87999999523162842, 1.0, p4_0)) * 0.77999997138977051;

    float p5_0 = clamp((p_0 - 0.5) / 0.5, 0.0, 1.0);



    float w5_0 = exp(- abs(dist_0 - (1.0 - pow(1.0 - p5_0, 2.59999990463256836)) * 1.01999998092651367 * turb_0) * 24.0) * smoothstep(0.0, 0.10000000149011612, p5_0) * (1.0 - smoothstep(0.8399999737739563, 1.0, p5_0)) * 0.57999998331069946;

#line 1376
    vec2 warpedUv_0 = gemini_varying_0 - dir_0 * ((w1_0 * 0.03999999910593033 + w2_0 * 0.02400000020861626 + w3_0 * 0.01400000043213367 + w4_0 * 0.02899999916553497 + w5_0 * 0.02199999988079071) * intensity_0 * smoothstep(0.0, 0.03999999910593033, p_0) * (1.0 - p_0 * 0.5)) / aspect_0;

#line 1381
    vec2 cOff_0 = dir_0 * ((w1_0 * 0.00800000037997961 + w2_0 * 0.00400000018998981 + w4_0 * 0.00499999988824129 + w5_0 * 0.00400000018998981) * intensity_0 * (1.0 - p_0 * 0.44999998807907104)) / aspect_0;
    vec3 scene_0;
    scene_0[0] = (texture((SceneSampler), (warpedUv_0 + cOff_0))).x;
    scene_0[1] = (texture((SceneSampler), (warpedUv_0))).y;
    scene_0[2] = (texture((SceneSampler), (warpedUv_0 - cOff_0))).z;

#line 1398
    float reigniteA_0 = (p_0 - 0.37999999523162842) / 0.10999999940395355;
    float reigniteB_0 = (p_0 - 0.63999998569488525) / 0.09000000357627869;

#line 1405
    vec3 _S3 = scene_0 + mix(vec3(1.0, 0.98000001907348633, 0.92000001668930054), vec3(0.55000001192092896, 0.72000002861022949, 1.0), clamp(p_0 * 1.39999997615814209, 0.0, 1.0)) * ((w1_0 * 3.40000009536743164 + w2_0 * 1.79999995231628418 + w3_0 + w4_0 * 2.34999990463256836 + w5_0 * 1.75) * (1.0 - p_0 * 0.41999998688697815) * intensity_0) + vec3(1.0, 0.75, 0.44999998807907104) * (exp(- dist_0 * 3.5 / max(R1_0, 0.02999999932944775)) * (exp(- p_0 * 2.20000004768371582) * 2.15000009536743164 + (exp(- reigniteA_0 * reigniteA_0) * 0.85000002384185791 + exp(- reigniteB_0 * reigniteB_0) * 0.55000001192092896)) * intensity_0 * (1.0 - smoothstep(0.85000002384185791, 1.0, p_0)));

#line 1405
    scene_0 = _S3;

#line 1410
    vec4 _S4 = vec4(mix((texture((SceneSampler), (gemini_varying_0))).xyz, _S3, 1.0 - smoothstep(0.75, 1.0, length(gemini_varying_0 - 0.5) * 2.0)), 1.0);

#line 1410
    _S1 = _S4;

#line 1410
    entryPointParam_main_fragColor_0 = _S4;

#line 1410
    return;
}

