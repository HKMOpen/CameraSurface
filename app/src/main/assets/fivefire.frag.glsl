#extension GL_OES_EGL_image_external : require

//necessary
precision mediump float;
uniform samplerExternalOES camTexture;

varying vec2 v_CamTexCoordinate;
varying vec2 v_TexCoordinate;
//end necessary

// passed in via our SuperAwesomeRenderer.java
uniform float	iGlobalTime;

// play around with xy for different sized effect, or pass in via GLES20.glUniform3f();
uniform vec3    iResolution;


//////////////////////
// Fire Flame shader
// https://www.shadertoy.com/view/XsXSWS
// procedural noise from IQ
vec2 hash( vec2 p )
{
	p = vec2( dot(p,vec2(127.1,311.7)),
			 dot(p,vec2(269.5,183.3)) );
	return -1.0 + 2.0*fract(sin(p)*43758.5453123);
}

float noise( in vec2 p )
{
	const float K1 = 0.366025404; // (sqrt(3)-1)/2;
	const float K2 = 0.211324865; // (3-sqrt(3))/6;

	vec2 i = floor( p + (p.x+p.y)*K1 );

	vec2 a = p - i + (i.x+i.y)*K2;
	vec2 o = (a.x>a.y) ? vec2(1.0,0.0) : vec2(0.0,1.0);
	vec2 b = a - o + K2;
	vec2 c = a - 1.0 + 2.0*K2;

	vec3 h = max( 0.5-vec3(dot(a,a), dot(b,b), dot(c,c) ), 0.0 );

	vec3 n = h*h*h*h*vec3( dot(a,hash(i+0.0)), dot(b,hash(i+o)), dot(c,hash(i+1.0)));

	return dot( n, vec3(70.0) );
}

float fbm(vec2 uv)
{
	float f;
	mat2 m = mat2( 1.6,  1.2, -1.2,  1.6 );
	f  = 0.5000*noise( uv ); uv = m*uv;
	f += 0.2500*noise( uv ); uv = m*uv;
	f += 0.1250*noise( uv ); uv = m*uv;
	f += 0.0625*noise( uv ); uv = m*uv;
	f = 0.5 + 0.5*f;
	return f;
}

const float ar_real_size = 1.0;
const vec2 ar_scale = vec2(2.,5);
// no defines, standard redish flames
//#define BLUE_FLAME
//#define GREEN_FLAME
void main()
{
	vec2 uv = v_CamTexCoordinate.xy / iResolution.xy;
	vec2 q = uv.yx;

    	float strength = floor(q.x-0.3);
    	float T3 = max(3.,1.25*strength)*iGlobalTime;
    	//q.x = mod(q.x,1.)-0.5;
    	//q.y -= 0.25;

        vec2 size = ar_scale * ar_real_size;
        q*=size;
        q.y -= 1.55;
        //q.x = mod(q.x,1.)-0.5;
        q.x -=.94;

    	float n = fbm(strength*q.xy - vec2(T3,0));
    	float c = 1. - 16. * pow( max( 0., length(q*vec2(1.8+q.y*1.5,.75) ) - n * max( 0., q.y+.25 ) ),1.2 );
    	float c1 = n * c * (1.5-pow(0.05*uv.y,4.));
    	//float c1 = n * c * (1.5-pow(2.50*uv.y,4.));


    	c1=clamp(0.68,0.18,0.9);

    	vec3 col = vec3(0.4*c1, 1.9*c1*c1*c1, c1*c1*c1*c1*c1*c1);


        col = col.xzy*2.9;

        vec3 rot = vec3(0.);

    	float a = c * (1.-pow(uv.x,2.));

        vec4 fire = vec4(mix(vec3(0.),col.zyx,a), 1.0);

	gl_FragColor = mix(texture2D(camTexture, uv),vec4(1.0),fire);
}