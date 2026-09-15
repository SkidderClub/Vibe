


#ifdef GL_ES
precision lowp float;
#endif






#ifdef GL_ES
precision lowp float;
#endif


uniform float time;
uniform vec2 resolution;


const float count = 20.8;
const float speed = 0.5;


float Hash( vec2 p, in float s)
{
    vec3 p2 = vec3(p.xy,27.0 * abs(sin(s)));
    return fract(sin(dot(p2,vec3(27.1,61.7, 12.4)))*273758.5453123);
}


float noise(in vec2 p, in float s)
{
    vec2 i = floor(p);
    vec2 f = fract(p);
    f *= f * (3.0-2.0*f);
    
    
    return mix(mix(Hash(i + vec2(0.,0.), s), Hash(i + vec2(1.,0.), s),f.x),
               mix(Hash(i + vec2(0.,1.), s), Hash(i + vec2(1.,1.), s),f.x),
               f.y) * s;
}


float fbm(vec2 p)
{
    float v = - noise(p * 02., 0.35);
    v += noise(p * 01.1, 0.5) - noise(p * 01.1, 0.25);
    v += noise(p * 03.1, 0.25) - noise(p * 03.1, 0.125);
    v += noise(p * 04.1, 0.125) - noise(p * 05.1, 2.3625);
    v += noise(p * 05.1, 0.50625) - noise(p * 16., 6.13125);
    v += noise(p * 26.1, 1.23125);
    return v;
}


void main( void )
{
    float worktime = (time * speed) + 100000.0;
    
    vec2 uv = ( gl_FragCoord.xy / resolution.xy ) * 5.0 - 2.0;
    uv.y *= resolution.y/resolution.x;
    
    
    vec3 finalColor = vec3( 0.0, 0.0, 0.0 );
    for( float i = 1.0; i < count; i++ )
    {
        float t = abs(1.0 / ((uv.y + fbm( uv + worktime / i )) * (i * 100.0)));
        finalColor +=  t * vec3( i * 0.1, 0.9, 1.90 );
    }
    
    gl_FragColor = vec4( finalColor, 1.0 );
    
    
}