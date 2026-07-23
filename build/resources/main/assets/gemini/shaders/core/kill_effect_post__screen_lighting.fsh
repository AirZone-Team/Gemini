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


#line 548
uniform sampler2D DepthSampler;


#line 42
uniform sampler2D BloomSampler;


#line 58
vec4 _S1;


#line 551
vec3 slViewPosFromDepth_0(vec2 uv_0, float depth_0)
{

    float near_0 = CameraParams.z;
    float far_0 = CameraParams.w;



    float viewZ_0 = 2.0 * far_0 * near_0 / (far_0 + near_0 - (depth_0 * 2.0 - 1.0) * (far_0 - near_0));
    float halfFovTan_0 = tan(CameraParams.x * 0.5);


    return vec3((uv_0.x * 2.0 - 1.0) * viewZ_0 * CameraParams.y * halfFovTan_0, (uv_0.y * 2.0 - 1.0) * viewZ_0 * halfFovTan_0, viewZ_0);
}


vec3 screenSpaceNormal_0(vec2 uv_1, float depth_1, vec3 viewPos_0)
{


    vec2 _S2 = vec2(1.0 / Params.x, 0.0);

#line 571
    vec2 _S3 = uv_1 + _S2;

#line 571
    vec3 posR_0 = slViewPosFromDepth_0(_S3, (texture((DepthSampler), (_S3))).x);

    vec2 _S4 = uv_1 - _S2;

#line 573
    vec3 posL_0 = slViewPosFromDepth_0(_S4, (texture((DepthSampler), (_S4))).x);

    vec2 _S5 = vec2(0.0, 1.0 / Params.y);

#line 575
    vec2 _S6 = uv_1 + _S5;

#line 575
    vec3 posU_0 = slViewPosFromDepth_0(_S6, (texture((DepthSampler), (_S6))).x);

    vec2 _S7 = uv_1 - _S5;

#line 577
    vec3 posD_0 = slViewPosFromDepth_0(_S7, (texture((DepthSampler), (_S7))).x);

#line 582
    float _S8 = viewPos_0.z;

#line 587
    if((max(max(abs(posR_0.z - _S8), abs(posL_0.z - _S8)), max(abs(posU_0.z - _S8), abs(posD_0.z - _S8)))) > 8.0)
    {

#line 588
        return vec3(0.0, 0.0, -1.0);
    }



    vec3 n_0 = normalize(cross(normalize(posR_0 - posL_0), normalize(posU_0 - posD_0)));

#line 593
    vec3 n_1;


    if((dot(n_0, vec3(0.0, 0.0, -1.0))) < 0.0)
    {

#line 596
        n_1 = - n_0;

#line 596
    }
    else
    {

#line 596
        n_1 = n_0;

#line 596
    }

    return n_1;
}


#line 73
float postHash_0(vec2 p_0)
{

#line 74
    return fract(sin(dot(p_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}


#line 602
bool slProjectToScreen_0(vec3 pos_0, out vec2 screenUV_0, out float expectedDepth_0)
{

#line 603
    screenUV_0 = vec2(0.0);
    expectedDepth_0 = 1.0;
    float fov_0 = CameraParams.x;
    float aspect_0 = CameraParams.y;
    float near_1 = CameraParams.z;
    float far_1 = CameraParams.w;

    float _S9 = pos_0.z;

#line 610
    if(_S9 < near_1)
    {

#line 610
        return false;
    }
    float halfFovTan_1 = tan(fov_0 * 0.5);



    float _S10 = pos_0.x / (_S9 * aspect_0 * halfFovTan_1) * 0.5 + 0.5;

#line 616
    screenUV_0 = vec2(_S10, pos_0.y / (_S9 * halfFovTan_1) * 0.5 + 0.5);

#line 616
    bool _S11;
    if(_S10 < 0.0)
    {

#line 617
        _S11 = true;

#line 617
    }
    else
    {

#line 617
        _S11 = (screenUV_0.x) > 1.0;

#line 617
    }

#line 617
    if(_S11)
    {

#line 617
        _S11 = true;

#line 617
    }
    else
    {

#line 617
        _S11 = (screenUV_0.y) < 0.0;

#line 617
    }

#line 617
    if(_S11)
    {

#line 617
        _S11 = true;

#line 617
    }
    else
    {

#line 617
        _S11 = (screenUV_0.y) > 1.0;

#line 617
    }

#line 617
    if(_S11)
    {

#line 618
        return false;
    }


    expectedDepth_0 = (far_1 + near_1 - 2.0 * far_1 * near_1 / _S9) / (far_1 - near_1) * 0.5 + 0.5;
    return true;
}


#line 707
in vec2 gemini_varying_0;


#line 631
float slShadowVisibility_0(vec3 viewPos_1, vec3 N_0, vec3 lightPos_0)
{

#line 632
    vec3 toLight_0 = lightPos_0 - viewPos_1;
    float dist_0 = length(toLight_0);
    vec3 L_0 = toLight_0 / max(dist_0, 0.00100000004749745);


    vec3 _S12 = viewPos_1 + N_0 * 0.05999999865889549 + L_0 * 0.05000000074505806;
    float rayLen_0 = dist_0 - 0.15000000596046448;
    if(rayLen_0 <= 0.0)
    {

#line 639
        return 1.0;
    }

    float stepSize_0 = rayLen_0 / 10.0;



    float _S13 = stepSize_0 * (0.5 + postHash_0(gemini_varying_0 * 173.0 + fract(TimePack.x) * 7.0) * 0.5);

#line 646
    int i_0 = 0;

#line 646
    float t_0 = _S13;

    for(;;)
    {

#line 648
        if(i_0 < 10)
        {
        }
        else
        {

#line 648
            break;
        }
        vec2 suv_0;
        float expected_0;
        bool _S14 = slProjectToScreen_0(_S12 + L_0 * t_0, suv_0, expected_0);

#line 652
        if(_S14)
        {


            if(((texture((DepthSampler), (suv_0))).x) < (expected_0 - (0.00120000005699694 + 0.00400000018998981 * (t_0 / rayLen_0))))
            {

#line 657
                return 0.11999999731779099;
            }

#line 652
        }

#line 660
        float t_1 = t_0 + stepSize_0;

#line 648
        i_0 = i_0 + 1;

#line 648
        t_0 = t_1;

#line 648
    }

#line 662
    return 1.0;
}


#line 62
float luminance_0(vec3 c_0)
{

#line 62
    return dot(c_0, vec3(0.29899999499320984, 0.58700001239776611, 0.11400000005960464));
}


#line 62
out vec4 entryPointParam_main_fragColor_0;


#line 667
void main()
{

#line 668
    vec3 lightPos_1 = LightViewPos.xyz;
    float lightRadius_0 = LightViewPos.w;
    vec3 lightCol_0 = LightColor.xyz;
    float intensity_0 = LightColor.w;

    vec3 scene_0 = (texture((SceneSampler), (gemini_varying_0))).xyz;
    float depth_2 = (texture((DepthSampler), (gemini_varying_0))).x;


    if(depth_2 >= 0.99900001287460327)
    {

        vec4 _S15 = vec4(scene_0 + lightCol_0 * (exp(- length(gemini_varying_0 - Center1.xy * 0.5 - 0.5) * 2.5) * intensity_0 * 0.03999999910593033), 1.0);

#line 680
        _S1 = _S15;

#line 680
        entryPointParam_main_fragColor_0 = _S15;

#line 680
        return;
    }



    vec3 viewPos_2 = slViewPosFromDepth_0(gemini_varying_0, depth_2);


    vec3 toLight_1 = lightPos_1 - viewPos_2;
    float dist_1 = length(toLight_1);
    vec3 L_1 = toLight_1 / max(dist_1, 0.00100000004749745);

#line 696
    float atten_0 = 1.0 / (1.0 + dist_1 * 0.09000000357627869 + dist_1 * dist_1 * 0.03200000151991844) * (1.0 - smoothstep(lightRadius_0 * 0.60000002384185791, lightRadius_0, dist_1));

    if(atten_0 < 0.0020000000949949)
    {

#line 699
        vec4 _S16 = vec4(scene_0, 1.0);

#line 699
        _S1 = _S16;

#line 699
        entryPointParam_main_fragColor_0 = _S16;

#line 699
        return;
    }



    vec3 N_1 = screenSpaceNormal_0(gemini_varying_0, depth_2, viewPos_2);


    float shadow_0 = slShadowVisibility_0(viewPos_2, N_1, lightPos_1);



    float halfLambert_0 = max(dot(N_1, L_1), 0.0) * 0.5 + 0.5;

#line 720
    vec3 _S17 = lightCol_0 * intensity_0 * atten_0;

#line 728
    vec3 lit_0 = scene_0 + (_S17 * (halfLambert_0 * halfLambert_0) * shadow_0 + lightCol_0 * pow(max(dot(N_1, normalize(L_1 + normalize(vec3(0.0, 0.0, 1.0)))), 0.0), 32.0) * atten_0 * intensity_0 * 0.34999999403953552 * shadow_0 + _S17 * 0.05999999865889549);
    float sceneLum_0 = luminance_0(scene_0);
    float litLum_0 = luminance_0(lit_0);

#line 730
    bool _S18;
    if(litLum_0 > 8.0)
    {

#line 731
        _S18 = litLum_0 > (sceneLum_0 * 3.0);

#line 731
    }
    else
    {

#line 731
        _S18 = false;

#line 731
    }

#line 731
    vec3 lit_1;

#line 731
    if(_S18)
    {

#line 731
        lit_1 = mix(lit_0, scene_0, smoothstep(8.0, 15.0, litLum_0));

#line 731
    }
    else
    {

#line 731
        lit_1 = lit_0;

#line 731
    }



    vec4 _S19 = vec4(lit_1, 1.0);

#line 735
    _S1 = _S19;

#line 735
    entryPointParam_main_fragColor_0 = _S19;

#line 735
    return;
}

