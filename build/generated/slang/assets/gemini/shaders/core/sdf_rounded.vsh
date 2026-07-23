#version 330 core
#line 19 0
struct SLANG_ParameterGroup_Projection_0
{
    mat4x4 ProjMat;
};


#line 19
layout(std140) uniform Projection
{
    mat4x4 ProjMat;
};

#line 13
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 13
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 117 1
vec4 _S1;


#line 30 0
vec4 _S2;


#line 31
vec2 _S3;


#line 32
vec2 _S4;


#line 33
vec2 _S5;


#line 33
out vec4 gemini_varying_0;


#line 33
out vec2 gemini_varying_1;


#line 33
out vec2 gemini_varying_2;


#line 33
out vec2 gemini_varying_3;


#line 33
in vec3 Position;


#line 33
in vec4 Color;


#line 33
in vec2 UV0;


#line 41
in ivec2 UV1;


#line 41
in ivec2 UV2;


#line 37
void main()
{

#line 38
    vec4 _S6 = ((((((ProjMat) * (ModelViewMat)))) * (vec4(Position, 1.0))));

#line 38
    _S1 = _S6;
    _S2 = Color;
    vec2 _S7 = (((TextureMat) * (vec4(UV0, 0.0, 1.0)))).xy;

#line 40
    _S3 = _S7;
    vec2 _S8 = vec2(UV1);

#line 41
    _S4 = _S8;
    vec2 _S9 = vec2(UV2);

#line 42
    _S5 = _S9;

#line 42
    gl_Position = _S6;

#line 42
    gemini_varying_0 = Color;

#line 42
    gemini_varying_1 = _S7;

#line 42
    gemini_varying_2 = _S8;

#line 42
    gemini_varying_3 = _S9;

#line 42
    return;
}

