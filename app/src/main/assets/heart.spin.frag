#extension GL_OES_EGL_image_external : require

//necessary
precision mediump float;
uniform samplerExternalOES camTexture;
//cat
uniform sampler2D          image_logo;
uniform sampler2D          image_cloud;

varying vec2               v_CamTexCoordinate;
varying vec2               v_TexCoordinate;
//end necessary

// passed in via our SuperAwesomeRenderer.java
uniform float	           iGlobalTime;
// play around with xy for different sized effect, or pass in via GLES20.glUniform3f();
uniform vec3               iResolution;

//java controlled inputs
uniform float i_x_pos;
uniform float i_y_pos;

// Bruno Gohier - 09-02-2014
//https://www.shadertoy.com/view/4sSGRt#
// layers
const float layers=10.;
const float real_size = 1.5;
const vec2 scale = vec2(1.05,1.25);
const vec2 size = scale * real_size;

// test
const vec3 mag = vec3(0.,1.,0.);
vec3 test = mag;

// coord change
void toSquare(inout vec2 v){ 
    // apect ratio
    float ar = iResolution.x/iResolution.y;
    float size = ar * 3.0;
    //ar=.3;
    v.x +=  i_y_pos/ar -0.8;
    v.x *= size * 2.;
    v.y += (i_x_pos * -1.)/ar;
    v.y *= size;
}

// rotate texcoords
void spin(inout vec2 v, float o)
{   
    float dis = 0.0002;
    float h = o*0.56;
	float t = iGlobalTime * 1.01+h;
    float st = .05*sin(t)+45.;
    //t=clamp(t,0.0,10.1);
    float cosX = cos(st);
	float sinX = sin(st);
    mat2 m = mat2(cosX,-sinX,sinX,cosX);
    v-=0.5;
    m*=.5;
    m+=.5;
    m=m*2.-1.;
	v = m*v;
    v+=.5;
}

void demo_offset_circle(inout vec2 v, float offset){
    float speed = 1.02;
    float dis = 0.02;
    float mx = dis*sin(iGlobalTime * speed + offset);
    float my = dis*cos(iGlobalTime * speed + offset);
	// uv focus
    vec2 center = vec2(mx,my);
    v+=center;
}

// create layer
//s: spin
//o: offset
//d: wave
void addLayer(inout vec4 c,float d,vec2 uv,float s,float o, float i)
{
	// wave uvs around
	//vec2 center = vec2(i_x_pos * xx, i_y_pos * yy);
	vec2 st = uv + vec2(d, 0.);

	//st = uv;
    toSquare(st);
    //demo_offset_circle(st,o);
	spin(st,o);

	// cats
	vec4 t = texture2D(image_logo,st);
	// set white as tranparent
	float ta = step(dot(t.rgb,vec3(.3,.59,.21)),.9);
	ta *= (i+1.)/layers;
	float aa = exp(ta)-1.;
	float mixer = clamp(aa,0.,1.);

	// smoke
	vec4 t2=texture2D(image_cloud,uv+vec2(0.,.5*iGlobalTime));
	float t2a = dot(t2.rgb, vec3(.3,.59,.21));
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
//Music Lens Distort
//https://www.shadertoy.com/view/4sKSWG
void music_len_distort(inout vec4 screen, sampler2D bitmap, vec2 v)
{
    //channel-1 is the source of sound
    //float val = texture2D(iChannel1, vec2(.3,.152)).r;
    //float val = 0.01 + 0.5 *  sin(iDate.w)/2.+.5;
    float val = 0.01 + 0.5 * sin(iGlobalTime)/2.+.5;
    vec2 offset = (v-.5)*val;
    float dist = distance(v,vec2(.5));
    vec2 uv = v+offset*dist;
    float mdst = dist/distance(vec2(.5), vec2(1.));
    float distanceMul = ((((uv.x>1.)||(uv.x<0.))||((uv.y>1.)||(uv.y<0.)))?0.:1.);
	screen = distanceMul*texture2D(bitmap, uv, (dist*val)*25.);
}
// main
void main()
{
	vec2 uv = v_CamTexCoordinate.xy / iResolution.xy;
	//vec2 uv2 = TextureCoordOut.xy / iResolution.xy;
	//uv.y = 1. - uv.y;

    // out color
    vec4 c = vec4(0.);

    // create layers
    for(float i=0.;i<layers;i+=1.){
    	addLayer(c, i*0.0003, uv, 0.002, .15 * i, i);
    }
    // heat shimmers
    float heat_speed = 0.21;

    vec2 heat_action = vec2(heat_speed*iGlobalTime, .0);
    vec4 t2=texture2D(camTexture, uv + heat_action.yx);

    float t2a = dot(t2.rgb,vec3(.3,59.,21));
    vec2 hs = vec2(t2a*0.002,0.);

    //vec4 t3=texture2D(camTexture,uv+hs.xy);
    vec4 t3=texture2D(camTexture,uv);

    // blend background
    c.rgb = mix(t3,c,c.a).rgb;
    c = clamp(c,0.,1.);

    // vignette
    //c.r += 1.95*dot(uv-vec2(.5),uv-vec2(.5));
	gl_FragColor = c;
	//===============
}