#version 330 core
#line 28 0
uniform sampler2D Sampler0;


#line 21
struct SLANG_ParameterGroup_DynamicTransforms_0
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};


#line 21
layout(std140) uniform DynamicTransforms
{
    mat4x4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4x4 TextureMat;
};

#line 1321 1
in vec4 gemini_varying_0;


#line 43 0
float emblemSize_0()
{

#line 43
    return gemini_varying_0.x * 512.0;
}


#line 44
float arcThickness_0()
{

#line 44
    return gemini_varying_0.y * 64.0;
}


#line 45
float progress_0()
{

#line 45
    return gemini_varying_0.z;
}


#line 46
float masterAlpha_0()
{

#line 46
    return gemini_varying_0.w;
}


#line 33
vec4 _S1;


#line 33
out vec4 entryPointParam_main_fragColor_0;


#line 33
in vec2 gemini_varying_1;


#line 50
void main()
{

#line 51
    float size_0 = emblemSize_0();
    float _S2 = size_0 * 0.5;
    float trackTh_0 = min(arcThickness_0(), _S2) * 0.5;
    float prog_0 = clamp(progress_0(), 0.0, 1.0);
    float alpha_0 = masterAlpha_0();



    float trackHalf_0 = trackTh_0 * 0.5;


    float midR_0 = _S2 - trackTh_0;
    float avatarR_0 = midR_0 - trackHalf_0 - 1.0;


    vec2 p_0 = (gemini_varying_1 - 0.5) * size_0;
    float d_0 = length(p_0);


    float ang_0 = (atan((p_0.y),(p_0.x))) + 1.57079637050628662;

#line 70
    float ang_1;
    if(ang_0 < 0.0)
    {

#line 71
        ang_1 = ang_0 + 6.28318548202514648;

#line 71
    }
    else
    {

#line 71
        ang_1 = ang_0;

#line 71
    }



    vec2 _S3 = mix(vec2(0.125), vec2(0.25), clamp(p_0 / avatarR_0 * 0.5 + 0.5, 0.0, 1.0));
    vec4 _S4 = (texture((Sampler0), (_S3)));

#line 85
    float _S5 = abs(d_0 - midR_0);


    vec4 color_0 = mix(vec4(mix(vec3(0.92500001192092896, 0.90600001811981201, 0.97600001096725464), _S4.xyz, _S4.w), 1.0 - smoothstep(avatarR_0 - 1.20000004768371582, avatarR_0, d_0)), vec4(vec3(0.86299997568130493, 0.83099997043609619, 0.94900000095367432), 1.0), 1.0 - smoothstep(trackHalf_0 - 0.69999998807907104, trackHalf_0 + 0.69999998807907104, _S5));


    float halfArc_0 = prog_0 * 3.14159274101257324;

    float dAng_0 = abs(ang_1 - 3.14159274101257324);

#line 93
    float dAng_1;
    if(dAng_0 > 3.14159274101257324)
    {

#line 94
        dAng_1 = 6.28318548202514648 - dAng_0;

#line 94
    }
    else
    {

#line 94
        dAng_1 = dAng_0;

#line 94
    }

    float _S6 = step(0.0020000000949949, prog_0);


    float _S7 = 3.14159274101257324 - halfArc_0;
    float _S8 = 3.14159274101257324 + halfArc_0;

#line 108
    vec4 color_1 = mix(color_0, vec4(vec3(0.41200000047683716, 0.27799999713897705, 0.79199999570846558), 1.0), (1.0 - smoothstep(trackTh_0 - 0.89999997615814209, trackTh_0 + 0.89999997615814209, _S5)) * max((1.0 - smoothstep(halfArc_0 - 1.5 / max(midR_0, 1.0), halfArc_0, dAng_1)) * _S6, (1.0 - smoothstep(trackTh_0 - 1.0, trackTh_0, min(length(p_0 - vec2(sin(_S7), - cos(_S7)) * midR_0), length(p_0 - vec2(sin(_S8), - cos(_S8)) * midR_0)))) * _S6));

    float _S9 = color_1.w;

#line 110
    if(_S9 < 0.00400000018998981)
    {

#line 110
        discard;

#line 110
    }
    vec4 _S10 = vec4(color_1.xyz, _S9 * alpha_0) * ColorModulator;

#line 111
    _S1 = _S10;

#line 111
    entryPointParam_main_fragColor_0 = _S10;

#line 111
    return;
}

