package camera.surface.gr.camerasurfaceview.RenderFilters;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.support.annotation.Nullable;

import com.camerasurfacegr.gles.Cam2Renderer;

/**
 * Created by zJJ on 7/4/2016.
 */
public class ExampleRenderer extends Cam2Renderer {
    private float offsetR = 0.5f;
    private float offsetG = 0.5f;
    private float offsetB = 0.5f;

    public ExampleRenderer(Context context, SurfaceTexture texture, int width, int height) {
        super(context, texture, width, height);
    }

    public ExampleRenderer(Context context, SurfaceTexture texture, int width, int height, @Nullable String fragPath, @Nullable String vertPath) {
        super(context, texture, width, height, "touchcolor.frag.glsl", "touchcolor.vert.glsl");
    }

    /**
     * we override {@link #setUniformsAndAttribs()} and make sure to call the super so we can add
     * our own uniforms to our shaders here. CameraRenderer handles the rest for us automatically
     */
    @Override
    protected void setUniformsAndAttribs() {
        super.setUniformsAndAttribs();

        int offsetRLoc = GLES20.glGetUniformLocation(mCameraShaderProgram, "offsetR");
        int offsetGLoc = GLES20.glGetUniformLocation(mCameraShaderProgram, "offsetG");
        int offsetBLoc = GLES20.glGetUniformLocation(mCameraShaderProgram, "offsetB");

        GLES20.glUniform1f(offsetRLoc, offsetR);
        GLES20.glUniform1f(offsetGLoc, offsetG);
        GLES20.glUniform1f(offsetBLoc, offsetB);
    }

    /**
     * take touch points on that textureview and turn them into multipliers for the color channels
     * of our shader, simple, yet effective way to illustrate how easy it is to integrate app
     * interaction into our glsl shaders
     *
     * @param rawX raw x on screen
     * @param rawY raw y on screen
     */
    public void setTouchPoint(float rawX, float rawY) {
        offsetR = rawX / mSurfaceWidth;
        offsetG = rawY / mSurfaceHeight;
        offsetB = offsetR / offsetG;
    }
}
