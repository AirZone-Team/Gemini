#version 330 core
#line 6 0
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 6
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 13
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 13
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};



vec3 _S1;


#line 20
vec4 _S2;


#line 117 1
vec4 _S3;


#line 117
out vec3 gemini_varying_1;


#line 117
out vec4 gemini_varying_0;


#line 117
in vec3 Position;


#line 117
in vec4 Color;


#line 25 0
void main()
{

#line 26
    vec4 _S4 = (((ModelViewMat) * (vec4(Position, 1.0))));
    vec3 _S5 = _S4.xyz;

#line 27
    _S1 = _S5;
    _S2 = Color;
    vec4 _S6 = (((ProjMat) * (_S4)));

#line 29
    _S3 = _S6;

#line 29
    gemini_varying_1 = _S5;

#line 29
    gemini_varying_0 = Color;

#line 29
    gl_Position = _S6;

#line 29
    return;
}

