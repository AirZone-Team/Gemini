#version 330 core
#line 10 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 10
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 3
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

#line 117 1
vec4 _S1;


#line 20 0
vec4 _S2;


#line 21
vec2 _S3;


#line 22
ivec2 _S4;


#line 23
ivec2 _S5;


#line 23
out vec4 gemini_varying_0;


#line 23
out vec2 gemini_varying_1;


#line 23
flat out ivec2 gemini_varying_2;


#line 23
flat out ivec2 gemini_varying_3;


#line 23
in vec3 Position;


#line 23
in vec4 Color;


#line 23
in vec2 UV0;


#line 23
in ivec2 UV1;


#line 23
in ivec2 UV2;


void main()
{

#line 28
    vec4 _S6 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 28
    _S1 = _S6;
    _S2 = Color;
    _S3 = UV0;
    _S4 = UV1;
    _S5 = UV2;

#line 32
    gl_Position = _S6;

#line 32
    gemini_varying_0 = Color;

#line 32
    gemini_varying_1 = UV0;

#line 32
    gemini_varying_2 = UV1;

#line 32
    gemini_varying_3 = UV2;

#line 32
    return;
}

