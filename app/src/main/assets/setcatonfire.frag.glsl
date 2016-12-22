#extension GL_OES_EGL_image_external : require

//necessary
precision mediump float;
uniform samplerExternalOES camTexture;

varying vec2 v_CamTexCoordinate;
varying vec2 v_TexCoordinate;
//end necessary


// Bruno Gohier - 09-02-2014

// layers
const float layers=25.;

// uv focus
vec2 center = vec2(.584,.979) + .2*sin(iGlobalTime);
vec2 size = vec2(.32,.965);

// test
const vec3 mag = vec3(1.,0.,1.);
vec3 test = mag;

// apect ratio
float ar = iResolution.x/iResolution.y;

// coord change
void to01(inout vec2 v){v = v*.5+.5;}
void to11(inout vec2 v){v = v*2.-1.;}
vec2 ret11(vec2 v){ to11(v); return v; }
void toSquare(inout vec2 v){ v.x -= .5*(1.-1./ar);  v.x *= ar; }

// rotate texcoords
void spin(inout vec2 v, float s, float o)
{
	float t = sin(iGlobalTime * s + o);
	float ct = cos(t);
	float st = sin(t);
	mat2 m = mat2(ct,st,-st,ct);
	v = m*v;
}

// create layer
void addLayer(inout vec4 c,float d,vec2 uv,float s,float o,float i)
{
	// wave uvs around
	vec2 st = uv-vec2(0.,d);
	toSquare(st);
	to11(st);
	spin(st,s,o);
	st *= size;
	st+=center;

	// cats
	vec4 t = texture2D(iChannel0,st);

	// set white as tranparent
	float ta = step(dot(t.rgb,vec3(.3,.59,.21)),.9);
	ta *= (i+1.)/layers;
	float aa = exp(ta)-1.;
	float mixer = clamp(aa,0.,1.);

	// smoke
	vec4 t2=texture2D(iChannel1,uv+vec2(0.,.5*iGlobalTime));
	float t2a = dot(t2.rgb,vec3(.3,.59,.21));
	t2a += i/layers;

	// change hue
	vec3 hue = mix(vec3(1.,1.,0.),vec3(1,.0,0.),4.*(layers-i)/layers);
	t.rgb *= hue;

	// burn
	c.rgb *= (i/layers)*vec3(1.);
	c.rgb = mix(c,t,mixer).rgb;

	// accumulative alpha
	c.a = clamp(t2a*(c.a + ta),0.,1.5);
}

// main
void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
	vec2 uv = fragCoord.xy / iResolution.xy;
	uv.y = 1. - uv.y;

	// out color
	vec4 c = vec4(0.);

	// create layers
	for(float i=0.;i<layers;i+=1.)
		addLayer(c,i*0.03,uv,2.,i*.15,i);

	// heat shimmers
	vec4 t2=texture2D(iChannel2,uv+vec2(0.,.5*iGlobalTime));
	float t2a = dot(t2.rgb,vec3(.3,59.,21));
	vec2 hs = vec2(t2a*.002,0.);
	vec4 t3=texture2D(iChannel2,uv+hs);

	// blend background
	c.rgb = mix(t3,c,c.a).rgb;
	c = clamp(c,0.,1.);

	// vignette
	c.r += .5*dot(uv-vec2(.5),uv-vec2(.5));

	fragColor = c;

	//===============

	if(test != mag)
		fragColor = vec4(test,1.);
}