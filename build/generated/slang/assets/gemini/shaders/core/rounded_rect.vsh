#version 330 core
#line 3 0
uint _S1;


#line 117 1
vec4 _S2;


#line 11 0
vec2 _S3;


#line 11
out vec2 gemini_varying_0;


void main()
{

#line 15
    uint _S4 = uint(gl_VertexID);

#line 15
    _S1 = _S4;

#line 15
    vec2 pos_0;

    if(_S4 == 0U)
    {

#line 17
        pos_0 = vec2(-1.0, -1.0);

#line 17
    }
    else
    {

#line 18
        if(_S1 == 1U)
        {

#line 18
            pos_0 = vec2(3.0, -1.0);

#line 18
        }
        else
        {

#line 18
            pos_0 = vec2(-1.0, 3.0);

#line 18
        }

#line 17
    }



    vec4 _S5 = vec4(pos_0, 0.0, 1.0);

#line 21
    _S2 = _S5;
    vec2 _S6 = pos_0 * 0.5 + 0.5;

#line 22
    _S3 = _S6;

#line 22
    gl_Position = _S5;

#line 22
    gemini_varying_0 = _S6;

#line 22
    return;
}

