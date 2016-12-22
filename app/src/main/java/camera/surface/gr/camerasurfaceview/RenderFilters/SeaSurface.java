package camera.surface.gr.camerasurfaceview.RenderFilters;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.SystemClock;
import android.support.annotation.Nullable;

import com.camerasurfacegr.gles.Cam2Renderer;

/**
 * Created by zJJ on 7/4/2016.
 */
public class SeaSurface extends Cam2Renderer {
    private float mTileAmount = 1.f;


    private float screen_x = 0.5f;
    private float screen_y = 0.5f;

    public void setTileAmount(float tileAmount) {
      //  this.mTileAmount = tileAmount;
        screen_x = tileAmount;
    }

    public SeaSurface(Context context, SurfaceTexture texture, int width, int height) {
        this(context, texture, width, height, null, null);
    }

    public SeaSurface(Context context, SurfaceTexture texture, int width, int height, @Nullable String fragPath, @Nullable String vertPath) {
        super(context, texture, width, height, "seascape.frag.glsl", "touchcolor.vert.glsl");
    }

    @Override
    protected void setUniformsAndAttribs() {
        super.setUniformsAndAttribs();

        int globalTimeHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iGlobalTime");
        GLES20.glUniform1f(globalTimeHandle, SystemClock.currentThreadTimeMillis() / 100.0f);

        int resolutionHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iResolution");
        GLES20.glUniform3f(resolutionHandle, 1.f, 1.f, 1.f);

        int screenPositionHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iMouse");
        GLES20.glUniform4f(screenPositionHandle, screen_x, screen_y, 0, 0);

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
        screen_x = rawX / mSurfaceWidth;
        screen_y = rawY / mSurfaceHeight;
    }

}
