#version 330 core
#line 3 0
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

#line 15
float hash21_0(vec2 point_0)
{

#line 16
    return fract(sin(dot(point_0, vec2(127.09999847412109375, 311.70001220703125))) * 43758.546875);
}


#line 13
vec4 _S1;


#line 13
out vec4 entryPointParam_main_fragColor_0;


#line 13
in vec2 gemini_varying_1;


#line 13
in vec4 gemini_varying_0;


#line 21
void main()
{

#line 22
    float across_0 = abs(gemini_varying_1.y);

#line 22
    bool _S2;
    if(across_0 > 1.0)
    {

#line 23
        _S2 = true;

#line 23
    }
    else
    {

#line 23
        _S2 = (gemini_varying_0.w) < 0.0020000000949949;

#line 23
    }

#line 23
    if(_S2)
    {

#line 23
        discard;

#line 23
    }

    float _S3 = - across_0;

#line 25
    float core_0 = exp(_S3 * 7.5);
    float aura_0 = exp(_S3 * 2.40000009536743164) * 0.34000000357627869;

#line 33
    vec3 color_0 = gemini_varying_0.xyz * (core_0 * (1.04999995231628418 + (0.72000002861022949 + 0.2800000011920929 * sin(gemini_varying_1.x * 6.28318548202514648)) * 0.64999997615814209) + aura_0 + smoothstep(0.44999998807907104, 1.0, hash21_0(vec2(floor(gemini_varying_1.x * 5.0), floor(across_0 * 7.0)))) * core_0 * 0.18000000715255737) + vec3(core_0) * core_0 * 0.37999999523162842;
    float alpha_0 = gemini_varying_0.w * clamp(core_0 + aura_0, 0.0, 1.0);
    if(alpha_0 < 0.00300000002607703)
    {

#line 35
        discard;

#line 35
    }

    vec4 _S4 = vec4(color_0, alpha_0) * ColorModulator;

#line 37
    _S1 = _S4;

#line 37
    entryPointParam_main_fragColor_0 = _S4;

#line 37
    return;
}

