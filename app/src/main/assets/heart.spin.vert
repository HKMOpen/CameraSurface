//position
attribute vec4 position;

//camera transform and texture
uniform mat4 camTextureTransform;
attribute vec4 camTexCoordinate;
attribute vec4 image_logo_txCoordinate;
attribute vec4 image_cloud_txCoordinate;
//attribute vec4 iChannel0TexCoordinate;
//attribute vec4 iChannel1TexCoordinate;
//varying vec2 TextureCoordOut;
//tex coords
varying vec2 v_CamTexCoordinate;
//varying vec2 v_iChannel0;
//varying vec2 v_iChannel1;
void main()
{
    //camera texcoord needs to be manipulated by the transform given back from the system
    v_CamTexCoordinate = (camTextureTransform * camTexCoordinate).xy;
    // v_iChannel0 = iChannel0TexCoordinate.xy;
    // v_iChannel1 = iChannel1TexCoordinate.xy;
    gl_Position = position;

    //TextureCoordOut = vec2(gl_MultiTexCoord0);
}