package camera.surface.gr.camerasurfaceview.RenderFilters;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.SystemClock;

import com.camerasurfacegr.gles.Cam2Renderer;

import camera.surface.gr.camerasurfaceview.R;

/**
 * Created by zJJ on 7/4/2016.
 */
public class LipsKissRender extends Cam2Renderer {
    private float mTileAmount = 1.f;
    private static final String GLSL = "fivefire.frag.glsl";
    private static final String GLSL_real = "heart.spin.frag";
    private float screen_x = 0.5f;
    private float screen_y = 0.5f;
    private static final float MAX_X_DELTA = 403.0f;
    private static final float MAX_Y_DELTA = 937;

    public LipsKissRender(Context context, SurfaceTexture texture, int width, int height) {
        super(context, texture, width, height, GLSL_real, "heart.spin.vert");
    }

    @Override
    protected void addTextures() {
        try {
            addTextureFromAssetsFolder("ic_nike.png", "image_logo", false);
        } catch (Exception e) {
            e.printStackTrace();
        }
        //addTexture(R.drawable.ic_lipkiss_512, "image_logo", false);
        addTexture(R.drawable.ic_hkcloud, "image_cloud", true);
    }

    @Override
    protected void setUniformsAndAttribs() {
        //always call super so that the built-in fun stuff can be set first
        super.setUniformsAndAttribs();
        int globalTimeHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iGlobalTime");
        GLES20.glUniform1f(globalTimeHandle, SystemClock.currentThreadTimeMillis() / 100.0f);
        int resolutionHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iResolution");
        GLES20.glUniform3f(resolutionHandle, 1.f, 1.f, 1.f);
        int x_gl_handle = GLES20.glGetUniformLocation(mCameraShaderProgram, "i_x_pos");
        GLES20.glUniform1f(x_gl_handle, screen_x);
        int y_gl_handle = GLES20.glGetUniformLocation(mCameraShaderProgram, "i_y_pos");
        GLES20.glUniform1f(y_gl_handle, screen_y);
    }

    public void setTileAmount(float tileAmount) {
        this.mTileAmount = tileAmount;
    }

    private void genRandomPosition() {
        float x_max = (float) mSurfaceWidth - MAX_X_DELTA;
        float y_max = (float) mSurfaceHeight - MAX_Y_DELTA;
        double cx = Math.random() * (double) x_max;
        double cy = Math.random() * (double) y_max;
        screen_x = (float) cx / (float) mSurfaceWidth;
        screen_y = (float) cy / (float) mSurfaceHeight;
    }

    /**
     * take touch points on that textureview and turn them into multipliers for the color channels
     * of our shader, simple, yet effective way to illustrate how easy it is to integrate app
     * interaction into our glsl shaders
     *
     * @param rawX raw x on screen
     * @param rawY raw y on screen
     */
    public final void setTouchPoint(float rawX, float rawY) {
        screen_x = rawX / (float) mSurfaceWidth;
        screen_y = rawY / (float) mSurfaceHeight;
    }

    public final void setRandomPositionLogo() {
        genRandomPosition();
    }

}
