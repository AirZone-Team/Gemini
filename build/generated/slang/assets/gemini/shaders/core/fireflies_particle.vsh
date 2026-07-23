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
vec4 _S1;


#line 117 1
vec4 _S2;


#line 3 0
uint _S3;


#line 19
vec2 _S4;


#line 19
out vec4 gemini_varying_0;


#line 19
out vec2 gemini_varying_1;


#line 19
in vec3 Position;


#line 19
in vec4 Color;


void main()
{

#line 23
    uint _S5 = uint(gl_VertexID);

#line 23
    _S3 = _S5;
    vec4 _S6 = (((ModelViewMat) * (vec4(Position, 1.0))));
    _S1 = Color;
    vec4 _S7 = (((ProjMat) * (_S6)));

#line 26
    _S2 = _S7;


    const vec2  uvs_0[4] = vec2[](vec2(-1.0, -1.0), vec2(-1.0, 1.0), vec2(1.0, 1.0), vec2(1.0, -1.0));

#line 35
    uint _S8 = _S5 % 4U;

#line 35
    _S4 = uvs_0[_S8];

#line 35
    gemini_varying_0 = Color;

#line 35
    gl_Position = _S7;

#line 35
    gemini_varying_1 = uvs_0[_S8];

#line 35
    return;
}

