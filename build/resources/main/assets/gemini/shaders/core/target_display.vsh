#version 330 core
#line 14 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 14
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 8
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 8
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 117 1
vec4 _S1;


#line 22 0
vec4 _S2;


#line 23
vec2 _S3;


#line 23
out vec4 gemini_varying_0;


#line 23
out vec2 gemini_varying_1;


#line 23
in vec3 Position;


#line 23
in vec4 Color;


#line 23
in vec2 UV0;


void main()
{

#line 28
    vec4 _S4 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 28
    _S1 = _S4;
    _S2 = Color;
    _S3 = UV0;

#line 30
    gl_Position = _S4;

#line 30
    gemini_varying_0 = Color;

#line 30
    gemini_varying_1 = UV0;

#line 30
    return;
}

