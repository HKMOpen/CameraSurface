package com.camerasurfacegr;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;

import com.camerasurfacegr.gles.WindowSurface;

/**
 * Created by zJJ on 7/3/2016.
 */
public class AdvancePositionCam extends Camera2BasicFragment {

    public static AdvancePositionCam newInstance() {
        return new AdvancePositionCam();
    }

    @Override
    protected boolean useFrontCamera() {
        return true;
    }


    public static final String TAG = "callback";

    private Object mLock = new Object();

    /**
     * Draws updates as fast as the system will allow.
     * <p/>
     * In 4.4, with the synchronous buffer queue queue, the frame rate will be limited.
     * In previous (and future) releases, with the async queue, many of the frames we
     * render may be dropped.
     * <p/>
     * The correct thing to do here is use Choreographer to schedule frame updates off
     * of vsync, but that's not nearly as much fun.
     */
    @Override
    protected void doAnimation(WindowSurface eglSurface, SurfaceTexture mSurfaceTexture) {


        final int BLOCK_WIDTH = 80;
        final int BLOCK_SPEED = 2;
        float clearColor = 0.0f;
        int xpos = -BLOCK_WIDTH / 2;
        int xdir = BLOCK_SPEED;
        int width = eglSurface.getWidth();
        int height = eglSurface.getHeight();

        Log.d(TAG, "Animating " + width + "x" + height + " EGL surface");

        //  while (true) {
        // Check to see if the TextureView's SurfaceTexture is still valid.
        synchronized (mLock) {
            SurfaceTexture surfaceTexture = mSurfaceTexture;
            if (surfaceTexture == null) {
                Log.d(TAG, "doAnimation exiting");
                return;
            }
        }

        // Still alive, render a frame.
        GLES20.glClearColor(clearColor, clearColor, clearColor, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
        GLES20.glScissor(xpos, height / 4, BLOCK_WIDTH, height / 2);
        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);

        // Publish the frame.  If we overrun the consumer, frames will be dropped,
        // so on a sufficiently fast device the animation will run at faster than
        // the display refresh rate.
        //
        // If the SurfaceTexture has been destroyed, this will throw an exception.
        eglSurface.swapBuffers();

        // Advance state
        clearColor += 0.015625f;
        if (clearColor > 1.0f) {
            clearColor = 0.0f;
        }
        xpos += xdir;
        if (xpos <= -BLOCK_WIDTH / 2 || xpos >= width - BLOCK_WIDTH / 2) {
            Log.d(TAG, "change direction");
            xdir = -xdir;

        }
        // }
    }
}
