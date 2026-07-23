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


#line 18 0
vec2 _S2;


#line 18
out vec2 gemini_varying_0;


#line 18
in vec3 Position;


#line 18
in vec2 UV0;


void main()
{

#line 23
    vec4 _S3 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 23
    _S1 = _S3;
    _S2 = UV0;

#line 24
    gl_Position = _S3;

#line 24
    gemini_varying_0 = UV0;

#line 24
    return;
}

