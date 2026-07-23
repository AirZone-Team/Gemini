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


#line 761
uniform sampler2D DepthSampler;


#line 42
uniform sampler2D BloomSampler;


#line 58
vec4 _S1;


#line 764
vec3 ssrtViewPosFromDepth_0(vec2 uv_0, float depth_0)
{

    float near_0 = CameraParams.z;
    float far_0 = CameraParams.w;



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


#line 781
bool projectToScreen_0(vec3 pos_0, out vec2 screenUV_0, out float expectedDepth_0)
{

#line 782
    screenUV_0 = vec2(0.0);
    expectedDepth_0 = 1.0;
    float fov_0 = CameraParams.x;
    float aspect_0 = CameraParams.y;
    float near_1 = CameraParams.z;
    float far_1 = CameraParams.w;

    float viewZ_1 = pos_0.z;
    if(viewZ_1 < near_1)
    {

#line 790
        return false;
    }
    float halfFovTan_1 = tan(fov_0 * 0.5);



    float _S2 = pos_0.x / (viewZ_1 * aspect_0 * halfFovTan_1) * 0.5 + 0.5;

#line 796
    screenUV_0 = vec2(_S2, pos_0.y / (viewZ_1 * halfFovTan_1) * 0.5 + 0.5);

#line 796
    bool _S3;
    if(_S2 < 0.0)
    {

#line 797
        _S3 = true;

#line 797
    }
    else
    {

#line 797
        _S3 = (screenUV_0.x) > 1.0;

#line 797
    }

#line 797
    if(_S3)
    {

#line 797
        _S3 = true;

#line 797
    }
    else
    {

#line 797
        _S3 = (screenUV_0.y) < 0.0;

#line 797
    }

#line 797
    if(_S3)
    {

#line 797
        _S3 = true;

#line 797
    }
    else
    {

#line 797
        _S3 = (screenUV_0.y) > 1.0;

#line 797
    }

#line 797
    if(_S3)
    {

#line 798
        return false;
    }



    expectedDepth_0 = (far_1 + near_1 - 2.0 * far_1 * near_1 / viewZ_1) / (far_1 - near_1) * 0.5 + 0.5;
    return true;
}


#line 804
out vec4 entryPointParam_main_fragColor_0;


#line 804
in vec2 gemini_varying_0;



void main()
{

#line 809
    vec3 hitColor_0;
    float ssrIntensity_0 = MiscParams.x;
    vec3 scene_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;
    float depth_1 = (texture((DepthSampler), (gemini_varying_0))).x;

#line 812
    bool _S4;


    if(depth_1 >= 0.99900001287460327)
    {

#line 815
        _S4 = true;

#line 815
    }
    else
    {

#line 815
        _S4 = ssrIntensity_0 < 0.01999999955296516;

#line 815
    }

#line 815
    if(_S4)
    {

#line 816
        vec4 _S5 = vec4(scene_0, 1.0);

#line 816
        _S1 = _S5;

#line 816
        entryPointParam_main_fragColor_0 = _S5;

#line 816
        return;
    }



    vec3 viewPos_0 = ssrtViewPosFromDepth_0(gemini_varying_0, depth_1);



    float distToLight_0 = length(LightViewPos.xyz - viewPos_0);
    if(distToLight_0 > (LightViewPos.w * 2.5))
    {

#line 827
        vec4 _S6 = vec4(scene_0, 1.0);

#line 827
        _S1 = _S6;

#line 827
        entryPointParam_main_fragColor_0 = _S6;

#line 827
        return;
    }



    float _S7 = 1.0 / Params.y;
    vec2 _S8 = vec2(1.0 / Params.x, 0.0);

#line 833
    vec2 _S9 = gemini_varying_0 + _S8;
    vec2 _S10 = gemini_varying_0 + _S8;

#line 833
    vec3 posR_0 = ssrtViewPosFromDepth_0(_S9, (texture((DepthSampler), (_S10))).x);

    vec3 posL_0 = ssrtViewPosFromDepth_0(gemini_varying_0 - _S8, (texture((DepthSampler), (gemini_varying_0 - _S8))).x);

    vec2 _S11 = vec2(0.0, _S7);

#line 837
    vec3 posU_0 = ssrtViewPosFromDepth_0(gemini_varying_0 + _S11, (texture((DepthSampler), (gemini_varying_0 + _S11))).x);

    vec3 posD_0 = ssrtViewPosFromDepth_0(gemini_varying_0 - _S11, (texture((DepthSampler), (gemini_varying_0 - _S11))).x);


    float _S12 = viewPos_0.z;

#line 842
    vec3 N_0;


    if((max(max(abs(posR_0.z - _S12), abs(posL_0.z - _S12)), max(abs(posU_0.z - _S12), abs(posD_0.z - _S12)))) > 8.0)
    {

#line 845
        N_0 = vec3(0.0, 0.0, -1.0);

#line 845
    }
    else
    {


        vec3 N_1 = normalize(cross(normalize(posR_0 - posL_0), normalize(posU_0 - posD_0)));
        if((dot(N_1, vec3(0.0, 0.0, -1.0))) < 0.0)
        {

#line 851
            N_0 = - N_1;

#line 851
        }
        else
        {

#line 851
            N_0 = N_1;

#line 851
        }

#line 845
    }

#line 855
    vec3 V_0 = normalize(vec3(0.0, 0.0, 1.0));



    float _S13 = mix(0.03999999910593033, 1.0, pow(1.0 - abs(dot(N_0, V_0)), 5.0));


    if(_S13 < 0.05000000074505806)
    {

#line 863
        vec4 _S14 = vec4(scene_0, 1.0);

#line 863
        _S1 = _S14;

#line 863
        entryPointParam_main_fragColor_0 = _S14;

#line 863
        return;
    }



    vec3 _S15 = reflect(- V_0, N_0);



    float maxDist_0 = distToLight_0 * 1.29999995231628418;

    float stepSize_0 = maxDist_0 / 32.0;


    float _S16 = postHash_0(gemini_varying_0 + fract(TimePack.x * 100.0)) * stepSize_0 * 0.5;

    const vec3 _S17 = vec3(0.0);

#line 879
    int i_0 = 0;

#line 879
    float rayDist_0 = _S16;


    for(;;)
    {

#line 882
        if(i_0 < 32)
        {
        }
        else
        {

#line 882
            hitColor_0 = _S17;

#line 882
            break;
        }

#line 883
        if(i_0 >= 32)
        {

#line 883
            _S4 = true;

#line 883
        }
        else
        {

#line 883
            _S4 = rayDist_0 > maxDist_0;

#line 883
        }

#line 883
        if(_S4)
        {

#line 883
            hitColor_0 = _S17;

#line 883
            break;
        }
        vec3 rayPos_0 = viewPos_0 + _S15 * rayDist_0;
        vec2 screenUV_1;
        float expectedDepth_1;

        bool _S18 = projectToScreen_0(rayPos_0, screenUV_1, expectedDepth_1);

#line 889
        if(!_S18)
        {

#line 889
            rayDist_0 = rayDist_0 + stepSize_0;

            i_0 = i_0 + 1;

#line 882
            continue;
        }

#line 894
        vec2 _S19 = screenUV_1;

#line 894
        float actualDepth_0 = (texture((DepthSampler), (_S19))).x;
        float depthDiff_0 = actualDepth_0 - expectedDepth_1;

#line 895
        bool _S20;


        if(depthDiff_0 > (- stepSize_0 * 0.80000001192092896))
        {

#line 898
            _S20 = depthDiff_0 < (stepSize_0 * 3.0);

#line 898
        }
        else
        {

#line 898
            _S20 = false;

#line 898
        }

#line 898
        if(_S20)
        {

#line 898
            hitColor_0 = (texture((SceneSampler), (screenUV_1))).xyz * _S13 * (1.0 / (1.0 + rayDist_0 * rayDist_0 * 0.00499999988824129)) * exp(- length(LightViewPos.xyz - ssrtViewPosFromDepth_0(screenUV_1, actualDepth_0)) / LightViewPos.w);

#line 911
            break;
        }

#line 911
        rayDist_0 = rayDist_0 + max(stepSize_0 * (0.5 + abs(rayPos_0.z) * 0.01499999966472387), stepSize_0 * 0.25);

#line 882
        i_0 = i_0 + 1;

#line 882
    }

#line 931
    vec4 _S21 = vec4(scene_0 + hitColor_0 * (ssrIntensity_0 * 0.55000001192092896 * (1.0 - smoothstep(0.69999998807907104, 1.0, length(gemini_varying_0 - 0.5) * 2.0)) * mix(0.40000000596046448, 1.0, _S13)), 1.0);

#line 931
    _S1 = _S21;

#line 931
    entryPointParam_main_fragColor_0 = _S21;

#line 931
    return;
}

