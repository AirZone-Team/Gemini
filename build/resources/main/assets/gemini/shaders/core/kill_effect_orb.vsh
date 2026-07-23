#version 330 core
#line 16 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 16
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 23
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 23
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 117 1
vec4 _S1;


#line 33 0
vec3 _S2;


#line 31
vec4 _S3;


#line 32
vec2 _S4;


#line 32
out vec3 gemini_varying_2;


#line 32
out vec4 gemini_varying_0;


#line 32
out vec2 gemini_varying_1;


#line 32
in vec3 Position;


#line 32
in vec4 Color;


#line 32
in vec2 UV0;



void main()
{

#line 38
    vec4 _S5 = (((ModelViewMat) * (vec4(Position, 1.0))));
    vec4 _S6 = (((ProjMat) * (_S5)));

#line 39
    _S1 = _S6;
    vec3 _S7 = _S5.xyz;

#line 40
    _S2 = _S7;
    _S3 = Color;
    _S4 = UV0;

#line 42
    gl_Position = _S6;

#line 42
    gemini_varying_2 = _S7;

#line 42
    gemini_varying_0 = Color;

#line 42
    gemini_varying_1 = UV0;

#line 42
    return;
}

