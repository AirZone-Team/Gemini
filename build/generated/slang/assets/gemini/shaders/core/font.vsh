#version 330 core
#line 15 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 15
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 9
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 9
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 117 1
vec4 _S1;


#line 23 0
vec4 _S2;


#line 24
vec2 _S3;


#line 24
out vec4 gemini_varying_0;


#line 24
out vec2 gemini_varying_1;


#line 24
in vec3 Position;


#line 24
in vec4 Color;


#line 24
in vec2 UV0;


void main()
{

#line 29
    vec4 _S4 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 29
    _S1 = _S4;
    _S2 = Color;
    _S3 = UV0;

#line 31
    gl_Position = _S4;

#line 31
    gemini_varying_0 = Color;

#line 31
    gemini_varying_1 = UV0;

#line 31
    return;
}

