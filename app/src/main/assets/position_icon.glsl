// "Quake Logo" by Krzysztof Narkowicz @knarkowicz
//https://www.shadertoy.com/view/4dKXDy#
const float MATH_PI = float( 3.14159265359 );

float VisibilityTerm( float roughness, float ndotv, float ndotl )
{
	float m2	= roughness * roughness;
	float visV	= ndotl * sqrt( ndotv * ( ndotv - ndotv * m2 ) + m2 );
	float visL	= ndotv * sqrt( ndotl * ( ndotl - ndotl * m2 ) + m2 );
	return 0.5 / max( visV + visL, 0.00001 );
}

float DistributionTerm( float roughness, float ndoth )
{
	float m2	= roughness * roughness;
	float d		= ( ndoth * m2 - ndoth ) * ndoth + 1.0;
	return m2 / ( d * d * MATH_PI );
}

vec3 FresnelTerm( vec3 specularColor, float vdoth )
{
	vec3 fresnel = clamp( 50.0 * specularColor.y, 0.0, 1.0 ) * specularColor + ( 1.0 - specularColor ) * pow( ( 1.0 - vdoth ), 5.0 );
	return fresnel;
}

vec3 LightSpecular( vec3 normal, vec3 viewDir, vec3 lightDir, vec3 lightColor, float roughness, vec3 specularColor )
{
	vec3 halfVec = normalize( viewDir + lightDir );

	float vdoth = clamp( dot( viewDir,	halfVec	 ), 0.0, 1.0 );
	float ndoth	= clamp( dot( normal,	halfVec	 ), 0.0, 1.0 );
	float ndotv = clamp( dot( normal,	viewDir  ), 0.0, 1.0 );
	float ndotl = clamp( dot( normal,	lightDir ), 0.0, 1.0 );

   	vec3	f = FresnelTerm( specularColor, vdoth );
	float	d = DistributionTerm( roughness, ndoth );
	float	v = VisibilityTerm( roughness, ndotv, ndotl );

    vec3 specular;
	specular = lightColor * f * ( d * v * MATH_PI * ndotl );
	return specular;
}

float Cylinder( vec3 p, float r, float height )
{
	float d = length( p.xz ) - r;
	d = max( d, abs( p.y ) - height );
	return d;
}

float Sphere( vec3 p, float s )
{
	return length( p ) - s;
}

float Box( vec3 p, vec3 b )
{
	vec3 d = abs( p ) - b;
	return min( max( d.x, max( d.y, d.z ) ), 0.0 ) + length( max( d, 0.0 ) );
}

float Substract( float a, float b )
{
    return max( a, -b );
}

float SubstractRound( float a, float b, float r )
{
	vec2 u = max( vec2( r + a, r - b ), vec2( 0.0, 0.0 ) );
	return min( -r, max( a, -b ) ) + length( u );
}

float Union( float a, float b )
{
    return min( a, b );
}

float UnionRound( float a, float b, float k )
{
    float h = clamp( 0.5 + 0.5 * ( b - a ) / k, 0.0, 1.0 );
    return mix( b, a, h ) - k * h * ( 1.0 - h );
}

float TriPrism( vec3 p, vec3 h )
{
    vec3 q = abs( p );
    return max( q.y - h.y, max( q.z * 0.866025 + p.x * h.z, -p.x ) - h.x * 0.5 );
}

float Scene( vec3 p, mat3 localToWorld )
{
    p = p * localToWorld;

    // ring
    float a = Cylinder( p, 1.0, 0.1 );
    float b = Cylinder( p + vec3( 0.12, 0.0, 0.0 ), 0.9, 0.2 );
    float ring = Substract( a, b );

    // nail
    float c = Box( p + vec3( -0.8, 0.0, 0.0 ), vec3( 0.4, 0.1, 0.1 ) );
    float d = Box( p + vec3( -0.4, 0.0, 0.0 ), vec3( 0.02, 0.1, 0.25 ) );
    float e = TriPrism( p + vec3( -1.274, 0.0, 0.0 ), vec3( 0.149, 0.1, 0.16 ) );
    float nail = Union( UnionRound( c, d, 0.1 ), e );

    // dents
    float f = TriPrism( p + vec3( +0.08, 0.0, -0.85 ), vec3( 0.1, 0.2, 0.5 ) );
    float g = TriPrism( p + vec3( -0.45, 0.0, -0.4 ), vec3( 0.1, 0.2, 0.16 ) );
    float h = TriPrism( p + vec3( -0.8, 0.0, -0.65 ), vec3( 0.08, 0.2, 0.16 ) );
    float i = TriPrism( p + vec3( -0.9, 0.0, 0.3 ), vec3( 0.04, 0.2, 0.16 ) );
    float j = TriPrism( p + vec3( +0.3, 0.0, 0.68 ), vec3( 0.28, 0.2, 0.16 ) );
    float k = TriPrism( p + vec3( -0.45, 0.0, -0.94 ), vec3( 0.07, 0.2, 0.5 ) );
    float l = TriPrism( p + vec3( 0.0, 0.0, 1.06 ), vec3( 0.1, 0.2, 0.5 ) );

    float dents = Union( Union( Union( Union( Union( Union( f, g ), h ), i ), j ), k ), l );
    ring = SubstractRound( ring, dents, 0.03 );

    float ret = Union( ring, nail );
	return ret;
}

float CastRay( in vec3 ro, in vec3 rd, mat3 localToWorld )
{
    const float maxd = 5.0;

	float h = 0.5;
    float t = 0.0;

    for ( int i = 0; i < 50; ++i )
    {
        if ( h < 0.001 || t > maxd )
        {
            break;
        }

	    h = Scene( ro + rd * t, localToWorld );
        t += h;
    }

    if ( t > maxd )
    {
        t = -1.0;
    }

    return t;
}

vec3 SceneNormal( in vec3 pos, mat3 localToWorld )
{
	vec3 eps = vec3( 0.001, 0.0, 0.0 );
	vec3 nor = vec3(
	    Scene( pos + eps.xyy, localToWorld ) - Scene( pos - eps.xyy, localToWorld ),
	    Scene( pos + eps.yxy, localToWorld ) - Scene( pos - eps.yxy, localToWorld ),
	    Scene( pos + eps.yyx, localToWorld ) - Scene( pos - eps.yyx, localToWorld ) );
	return normalize( -nor );
}

void mainImage( out vec4 fragColor, in vec2 fragCoord )
{
	vec2 q = fragCoord.xy / iResolution.xy;
    vec2 p = -1.0 + 2.0 * q;
	p.x *= iResolution.x / iResolution.y;

	vec3 rayOrigin 	= vec3( 0.0, -0.28, -3.5 );
    vec3 rayDir 	= normalize( vec3( p.x, p.y, 2.0 ) );

    float theta = -0.5 * MATH_PI;
    mat3 rotX = mat3(
        vec3( cos( theta ), sin( theta ), 0.0 ),
        vec3( -sin( theta ), cos( theta ), 0.0 ),
		vec3( 0.0, 0.0, 1.0 )
        );

    vec2 mo = iMouse.xy / iResolution.xy;
    float phi = 0.5 * MATH_PI + 2.0 * iGlobalTime + 10.0 * mo.x;
    mat3 rotY = mat3(
        vec3( 1.0, 0.0, 0.0 ),
        vec3( 0.0, cos( phi ), sin( phi ) ),
        vec3( 0.0, -sin( phi ), cos( phi ) )
        );

    mat3 localToWorld = rotX * rotY;


	vec3 color = texture2D( iChannel0, q ).yyy * 0.3;

	float t = CastRay( rayOrigin, rayDir, localToWorld );
    if ( t > 0.0 )
    {
        vec3 pos = rayOrigin + t * rayDir;
        vec3 normal = SceneNormal( pos, localToWorld );
        vec3 lightDir = normalize( vec3( 0.5, 0.3, 1.0 ) );
        vec3 lightColor = vec3( 1.6 );

        vec3 posLS = pos * localToWorld;
        vec3 nrmLS = normal * localToWorld;
        vec2 uvX = posLS.yz;
        vec2 uvY = posLS.xz;
        vec2 uvZ = posLS.xy;

        vec3 textureX = texture2D( iChannel0, uvX ).xyz;
        vec3 textureY = texture2D( iChannel0, uvY ).xyz;
        vec3 textureZ = texture2D( iChannel0, uvZ ).xyz;

        vec3 weights = max( abs( nrmLS ), 0.00001 );
		weights /= weights.x + weights.y + weights.z;

        vec3 texture = textureX * weights.x + textureY * weights.y + textureZ * weights.z;

        float rustMask = clamp( texture.x * 3.0 - 0.5, 0.0, 1.0 );

        vec3 diffuseColor = mix( vec3( 0.0 ), texture, rustMask );
        diffuseColor *= diffuseColor * vec3( 0.94, 0.72, 0.47 ) * 1.5;
        vec3 specularColor = mix( texture, vec3( 0.04 ), rustMask );
        float roughness = mix( 0.2, 0.6, rustMask );

        vec3 diffuse = lightColor * clamp( dot( normal, lightDir ), 0.0, 1.0 );
        color = diffuseColor * ( diffuse + 0.2 );
        color += LightSpecular( normal, rayDir, lightDir, lightColor, roughness, specularColor );
    }

    fragColor = vec4( color, 1.0 );
}

