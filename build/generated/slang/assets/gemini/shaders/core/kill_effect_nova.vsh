#version 330 core
#line 17 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 17
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 10
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 10
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 117 1
vec4 _S1;


#line 25 0
vec4 _S2;


#line 26
vec2 _S3;


#line 26
out vec4 gemini_varying_0;


#line 26
out vec2 gemini_varying_1;


#line 26
in vec3 Position;


#line 26
in vec4 Color;


#line 26
in vec2 UV0;


void main()
{

#line 31
    vec4 _S4 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 31
    _S1 = _S4;
    _S2 = Color;
    _S3 = UV0;

#line 33
    gl_Position = _S4;

#line 33
    gemini_varying_0 = Color;

#line 33
    gemini_varying_1 = UV0;

#line 33
    return;
}

