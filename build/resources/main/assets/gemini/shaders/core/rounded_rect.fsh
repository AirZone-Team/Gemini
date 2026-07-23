#version 330 core
#line 15 0
struct SLANG_ParameterGroup_RoundedRectUniforms_0
{
    vec4 rect;
    vec4 radii;
    vec4 fillColor;
    vec4 borderColor;
    vec4 params;
    vec4 fillTL;
    vec4 fillTR;
    vec4 fillBR;
    vec4 fillBL;
    vec4 borderTL;
    vec4 borderTR;
    vec4 borderBR;
    vec4 borderBL;
};


#line 15
layout(std140) uniform RoundedRectUniforms
{
    vec4 rect;
    vec4 radii;
    vec4 fillColor;
    vec4 borderColor;
    vec4 params;
    vec4 fillTL;
    vec4 fillTR;
    vec4 fillBR;
    vec4 fillBL;
    vec4 borderTL;
    vec4 borderTR;
    vec4 borderBR;
    vec4 borderBL;
};

#line 38
float roundRectDistance_0(vec2 position_0, vec4 bounds_0, vec4 radius_0)
{

#line 39
    vec2 _S1 = bounds_0.zw;

#line 39
    vec2 _S2 = bounds_0.xy;

    vec2 p_0 = position_0 - (_S2 + _S1) * 0.5;


    vec2 s_0 = step(vec2(0.0), p_0);

    float _S3 = s_0.y;

#line 45
    float _S4 = mix(mix(radius_0.w, radius_0.x, _S3), mix(radius_0.z, radius_0.y, _S3), s_0.x);

#line 51
    vec2 q_0 = abs(p_0) - (_S1 - _S2) * 0.5 + _S4;
    return length(max(q_0, 0.0)) + min(max(q_0.x, q_0.y), 0.0) - _S4;
}



vec4 bilinearColor_0(vec4 tl_0, vec4 tr_0, vec4 br_0, vec4 bl_0, vec2 uv_0)
{

#line 58
    float _S5 = uv_0.x;

    return mix(mix(bl_0, br_0, _S5), mix(tl_0, tr_0, _S5), uv_0.y);
}


#line 32
vec4 _S6;


#line 32
out vec4 entryPointParam_main_fragColor_0;


#line 32
in vec2 gemini_varying_0;


#line 65
void main()
{


    float dist_0 = roundRectDistance_0(gl_FragCoord.xy, vec4(rect.xy, rect.xy + rect.zw), radii);

    float edge_0 = (fwidth((dist_0)));
    float fillAlpha_0 = 1.0 - smoothstep(0.0, edge_0, dist_0);

#line 72
    vec4 fill_0;


    if((params.y) > 0.5)
    {

#line 75
        fill_0 = bilinearColor_0(fillTL, fillTR, fillBR, fillBL, gemini_varying_0);

#line 75
    }
    else
    {

#line 75
        fill_0 = fillColor;

#line 75
    }


    float borderThickness_0 = params.x;

#line 78
    vec4 border_0;

    if((params.z) > 0.5)
    {

#line 80
        border_0 = bilinearColor_0(borderTL, borderTR, borderBR, borderBL, gemini_varying_0);

#line 80
    }
    else
    {

#line 80
        border_0 = borderColor;

#line 80
    }

#line 80
    float borderAlpha_0;



    if(borderThickness_0 > 0.0)
    {
        float _S7 = - borderThickness_0;

#line 86
        borderAlpha_0 = fillAlpha_0 * smoothstep(_S7 - edge_0, _S7, dist_0);

#line 84
    }
    else
    {

#line 84
        borderAlpha_0 = 0.0;

#line 84
    }

#line 91
    vec4 borderLayer_0 = vec4(border_0.xyz, border_0.w * borderAlpha_0);

    vec4 _S8 = mix(vec4(fill_0.xyz, fill_0.w * fillAlpha_0), borderLayer_0, borderLayer_0.w);

#line 93
    _S6 = _S8;

    if((_S8.w) < 0.00100000004749745)
    {

#line 96
        discard;

#line 95
    }

#line 95
    entryPointParam_main_fragColor_0 = _S6;

#line 95
    return;
}

