#version 330 core
#line 7 0
uniform sampler2D InputSampler;


#line 9
vec4 _S1;


#line 9
out vec4 entryPointParam_main_fragColor_0;


void main()
{

#line 14
    ivec3 _S2 = ivec3(ivec2(gl_FragCoord.xy), 0);

#line 14
    vec4 _S3 = (texelFetch((InputSampler), ((_S2)).xy, ((_S2)).z));

#line 14
    _S1 = _S3;

#line 14
    entryPointParam_main_fragColor_0 = _S3;

#line 14
    return;
}

