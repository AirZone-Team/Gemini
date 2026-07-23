#version 330 core
#line 13 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 13
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 7
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 7
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


#line 21
out vec4 gemini_varying_0;


#line 21
out vec2 gemini_varying_1;


#line 21
in vec3 Position;


#line 21
in vec4 Color;


void main()
{

#line 26
    vec4 _S4 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 26
    _S1 = _S4;
    _S2 = Color;
    vec2 _S5 = Position.xy;

#line 28
    _S3 = _S5;

#line 28
    gl_Position = _S4;

#line 28
    gemini_varying_0 = Color;

#line 28
    gemini_varying_1 = _S5;

#line 28
    return;
}

