#version 330 core
#line 3 0
uint _S1;


#line 9
vec2 _S2;


#line 117 1
vec4 _S3;


#line 117
out vec2 gemini_varying_0;


#line 13 0
void main()
{

#line 13
    uint _S4 = uint(gl_VertexID);

#line 13
    _S1 = _S4;

#line 13
    vec2 pos_0;

    if(_S4 == 0U)
    {

#line 15
        pos_0 = vec2(-1.0, -1.0);

#line 15
    }
    else
    {

#line 16
        if(_S1 == 1U)
        {

#line 16
            pos_0 = vec2(3.0, -1.0);

#line 16
        }
        else
        {

#line 16
            pos_0 = vec2(-1.0, 3.0);

#line 16
        }

#line 15
    }


    vec2 _S5 = pos_0 * 0.5 + 0.5;

#line 18
    _S2 = _S5;
    vec4 _S6 = vec4(pos_0, 0.0, 1.0);

#line 19
    _S3 = _S6;

#line 19
    gemini_varying_0 = _S5;

#line 19
    gl_Position = _S6;

#line 19
    return;
}

