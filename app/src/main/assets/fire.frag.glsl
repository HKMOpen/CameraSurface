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


float noise(vec3 p) //Thx to Las^Mercury
{
	vec3 i = floor(p);
	vec4 a = dot(i, vec3(1., 57., 21.)) + vec4(0., 57., 21., 78.);
	vec3 f = cos((p-i)*acos(-1.))*(-.5)+.5;
	a = mix(sin(cos(a)*a),sin(cos(1.+a)*(1.+a)), f.x);
	a.xy = mix(a.xz, a.yw, f.y);
	return mix(a.x, a.y, f.z);
}

float sphere(vec3 p, vec4 spr)
{
	return length(spr.xyz-p) - spr.w;
}

float flame(vec3 p)
{
	float d = sphere(p*vec3(1.,.5,1.), vec4(.0,-1.,.0,1.));
	return d + (noise(p+vec3(.0,iGlobalTime*2.,.0)) + noise(p*3.)*.5)*.25*(p.y) ;
}

float scene(vec3 p)
{
	return min(100.-length(p) , abs(flame(p)) );
}

vec4 raymarch(vec3 org, vec3 dir)
{
	float d = 0.0, glow = 0.0, eps = 0.02;
	vec3  p = org;
	bool glowed = false;

	for(int i=0; i<64; i++)
	{
		d = scene(p) + eps;
		p += d * dir;
		if( d>eps )
		{
			if(flame(p) < .0)
				glowed=true;
			if(glowed)
       			glow = float(i)/64.;
		}
	}
	return vec4(p,glow);
}

void main()
{
    //vec4 cameraColor = texture2D(camTexture, v_CamTexCoordinate);

	vec2 v = -1.0 + 2.0 * v_CamTexCoordinate.xy / iResolution.xy;
	v.x *= iResolution.x/iResolution.y;

	vec3 org = vec3(0., -2., 4.);
	vec3 dir = normalize(vec3(v.x*1.6, -v.y, -1.5));

	//vec4 p = raymarch(org, dir);
	//float glow = p.w;

	vec4 col = mix(vec4(1.,.5,.1,1.), vec4(0.1,.5,1.,1.), p.y*.02+.4);

	gl_FragColor = mix(vec4(0.), col, pow(0.1*2.,4.));
	//gl_FragColor = mix(vec4(1.), mix(vec4(1.,.5,.1,1.),vec4(0.1,.5,1.,1.),p.y*.02+.4), pow(glow*2.,4.));
}
