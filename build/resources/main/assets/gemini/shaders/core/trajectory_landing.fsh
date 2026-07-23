#version 330 core
#line 3 0
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

#line 15
float ring_0(float distanceFromCenter_0, float radius_0, float width_0)
{

#line 16
    return 1.0 - smoothstep(width_0, width_0 + 0.01200000010430813, abs(distanceFromCenter_0 - radius_0));
}


#line 13
vec4 _S1;


#line 13
out vec4 entryPointParam_main_fragColor_0;


#line 13
in vec2 gemini_varying_1;


#line 13
in vec4 gemini_varying_0;


#line 21
void main()
{

#line 22
    int style_0 = int(floor(gemini_varying_1.x + 0.00009999999747379));
    vec2 point_0 = vec2(fract(gemini_varying_1.x), gemini_varying_1.y) - 0.5;
    float distanceFromCenter_1 = length(point_0);
    float shape_0 = ring_0(distanceFromCenter_1, 0.38999998569488525, 0.02500000037252903);
    float glow_0 = exp(- abs(distanceFromCenter_1 - 0.38999998569488525) * 18.0) * 0.41999998688697815;

#line 26
    float shape_1;

    if(style_0 >= 1)
    {
        float _S2 = point_0.x;

#line 30
        float _S3 = abs(_S2);

#line 30
        float _S4 = point_0.y;

#line 30
        float _S5 = abs(_S4);

#line 30
        shape_1 = max(shape_0, max(max(step(_S3, 0.0130000002682209) * step(0.18000000715255737, _S5), step(_S5, 0.0130000002682209) * step(0.18000000715255737, _S3)), ring_0(distanceFromCenter_1, 0.28999999165534973, 0.01200000010430813) * step(0.72000002861022949, abs(sin((atan((_S4),(_S2))) * 4.0)))));

#line 28
    }
    else
    {

#line 28
        shape_1 = shape_0;

#line 28
    }

#line 38
    if(style_0 >= 2)
    {

#line 39
        float _S6 = (atan((point_0.y),(point_0.x)));

#line 39
        shape_1 = max(shape_1, max(ring_0(distanceFromCenter_1, 0.23999999463558197, 0.00999999977648258) * step(0.47999998927116394, sin(_S6 * 12.0) * 0.5 + 0.5), max(step(0.93000000715255737, cos(_S6 * 8.0)) * smoothstep(0.11999999731779099, 0.20000000298023224, distanceFromCenter_1) * (1.0 - smoothstep(0.2800000011920929, 0.34000000357627869, distanceFromCenter_1)), exp(- distanceFromCenter_1 * 18.0) * 0.77999997138977051)));

#line 38
    }

#line 49
    float alpha_0 = clamp(shape_1 + glow_0, 0.0, 1.0) * gemini_varying_0.w;

#line 49
    bool _S7;
    if(alpha_0 < 0.00400000018998981)
    {

#line 50
        _S7 = true;

#line 50
    }
    else
    {

#line 50
        _S7 = distanceFromCenter_1 > 0.51999998092651367;

#line 50
    }

#line 50
    if(_S7)
    {

#line 50
        discard;

#line 50
    }



    vec4 _S8 = vec4(gemini_varying_0.xyz * (shape_1 * 1.39999997615814209 + glow_0) + vec3(shape_1) * shape_1 * 0.2800000011920929, alpha_0) * ColorModulator;

#line 54
    _S1 = _S8;

#line 54
    entryPointParam_main_fragColor_0 = _S8;

#line 54
    return;
}

