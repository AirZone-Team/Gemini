#version 330 core
#line 21 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 21
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 14
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 14
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 11
vec2 _S1;


#line 12
vec4 _S2;


#line 117 1
vec4 _S3;


#line 117
out vec2 gemini_varying_0;


#line 117
out vec4 gemini_varying_1;


#line 117
in vec2 UV0;


#line 117
in vec4 Color;


#line 117
in vec3 Position;


#line 27 0
void main()
{

#line 28
    _S1 = UV0;
    _S2 = Color;

#line 35
    vec4 _S4 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 35
    _S3 = _S4;

#line 35
    gemini_varying_0 = UV0;

#line 35
    gemini_varying_1 = Color;

#line 35
    gl_Position = _S4;

#line 35
    return;
}

