package camera.surface.gr.camerasurfaceview.RenderFilters;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.os.SystemClock;

import com.camerasurfacegr.gles.Cam2Renderer;

/**
 * Created by zJJ on 7/4/2016.
 */
public class SuperAwesomeRenderer extends Cam2Renderer {

    private float mTileAmount = 1.f;

    public SuperAwesomeRenderer(Context context, SurfaceTexture texture, int width, int height) {
        super(context, texture, width, height, "superawesome.frag.glsl", "superawesome.vert.glsl");
    }

    @Override
    protected void setUniformsAndAttribs() {
        //always call super so that the built-in fun stuff can be set first
        super.setUniformsAndAttribs();

        int globalTimeHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iGlobalTime");
        GLES20.glUniform1f(globalTimeHandle, SystemClock.currentThreadTimeMillis() / 100.0f);

        int resolutionHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "iResolution");
        GLES20.glUniform3f(resolutionHandle, mTileAmount, mTileAmount, 1.f);
    }

    public void setTileAmount(float tileAmount) {
        this.mTileAmount = tileAmount;
    }

}