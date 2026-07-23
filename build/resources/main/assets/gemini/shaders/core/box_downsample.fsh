#version 330 core
#line 8 0
struct SLANG_ParameterGroup_SamplerInfo_0
{
    vec2 OutSize;
    vec2 InSize;
};


#line 8
layout(std140) uniform SamplerInfo
{
    vec2 OutSize;
    vec2 InSize;
};

#line 6
uniform sampler2D InSampler;


#line 15
vec4 _S1;


#line 15
out vec4 entryPointParam_main_fragColor_0;


#line 15
in vec2 gemini_varying_0;


void main()
{

#line 20
    vec2 oneTexel_0 = 1.0 / InSize;

    vec2 factor_0 = InSize / OutSize;

#line 31
    vec2 stepX_0 = vec2(oneTexel_0.x * (factor_0.x * 0.5), 0.0);
    vec2 stepY_0 = vec2(0.0, oneTexel_0.y * (factor_0.y * 0.5));

    vec2 _S2 = gemini_varying_0 - stepX_0;
    vec2 _S3 = gemini_varying_0 + stepX_0;



    vec4 _S4 = ((texture((InSampler), (_S2 - stepY_0))) + (texture((InSampler), (_S3 - stepY_0))) + (texture((InSampler), (_S2 + stepY_0))) + (texture((InSampler), (_S3 + stepY_0)))) * 0.25;

#line 39
    _S1 = _S4;

#line 39
    entryPointParam_main_fragColor_0 = _S4;

#line 39
    return;
}

