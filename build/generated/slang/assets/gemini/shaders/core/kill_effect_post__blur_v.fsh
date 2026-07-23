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


#line 215
void main()
{

#line 216
    vec2 _S2 = vec2(0.0, 1.0 / Params.y);

    float _S3 = max(PassParams.w * 0.5, 0.5);

    const vec3 _S4 = vec3(0.0);



    int _S5 = clamp(int(ceil(_S3 * 3.0)), 3, 8);

#line 224
    int i_0 = -8;

#line 224
    vec3 color_0 = _S4;

#line 224
    float weightSum_0 = 0.0;

    for(;;)
    {

#line 226
        if(i_0 <= 8)
        {
        }
        else
        {

#line 226
            break;
        }

#line 227
        if((abs(i_0)) > _S5)
        {

#line 227
            i_0 = i_0 + 1;

#line 226
            continue;
        }
        float w_0 = exp(- float(i_0 * i_0) / (2.0 * _S3 * _S3));
        vec2 _S6 = gemini_varying_0 + _S2 * float(i_0);
        float weightSum_1 = weightSum_0 + w_0;

#line 230
        color_0 = color_0 + (texture((SceneSampler), (_S6))).xyz * w_0;

#line 230
        weightSum_0 = weightSum_1;

#line 226
        i_0 = i_0 + 1;

#line 226
    }

#line 232
    vec4 _S7 = vec4(color_0 / weightSum_0, 1.0);

#line 232
    _S1 = _S7;

#line 232
    entryPointParam_main_fragColor_0 = _S7;

#line 232
    return;
}

