package com.camerasurfacegr.prompt;

import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.util.Log;
import android.view.TextureView;

import com.camerasurfacegr.gles.EglCore;
import com.camerasurfacegr.gles.WindowSurface;

/**
 * Created by zJJ on 7/3/2016.
 */
public class Renderer extends Thread {
    // implements TextureView.SurfaceTextureListener
    private Object mLock = new Object();        // guards mSurfaceTexture, mDone
    private SurfaceTexture mSurfaceTexture;
    private EglCore mEglCore;
    private boolean mDone, sReleaseInCallback;
    public static final String TAG = "callback";

    public Renderer() {
        super("TextureViewGL Renderer");
    }

    public void setReleaseInCallback(boolean b) {
        this.sReleaseInCallback = b;
    }

    @Override
    public void run() {
        while (true) {
            SurfaceTexture surfaceTexture = null;

            // Latch the SurfaceTexture when it becomes available.  We have to wait for
            // the TextureView to create it.
            synchronized (mLock) {
                while (!mDone && (surfaceTexture = mSurfaceTexture) == null) {
                    try {
                        mLock.wait();
                    } catch (InterruptedException ie) {
                        throw new RuntimeException(ie);     // not expected
                    }
                }
                if (mDone) {
                    break;
                }
            }
            Log.d(TAG, "Got surfaceTexture=" + surfaceTexture);

            // Create an EGL surface for our new SurfaceTexture.  We're not on the same
            // thread as the SurfaceTexture, which is a concern for the *consumer*, which
            // wants to call updateTexImage().  Because we're the *producer*, i.e. the
            // one generating the frames, we don't need to worry about being on the same
            // thread.
            mEglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
            WindowSurface windowSurface = new WindowSurface(mEglCore, mSurfaceTexture);
            windowSurface.makeCurrent();

            // Render frames until we're told to stop or the SurfaceTexture is destroyed.
            doAnimation(windowSurface);

            windowSurface.release();
            mEglCore.release();
            if (!sReleaseInCallback) {
                Log.i(TAG, "Releasing SurfaceTexture in renderer thread");
                surfaceTexture.release();
            }
        }

        Log.d(TAG, "Renderer thread exiting");
    }

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
    private void doAnimation(WindowSurface eglSurface) {
        final int BLOCK_WIDTH = 80;
        final int BLOCK_SPEED = 2;
        float clearColor = 0.0f;
        int xpos = -BLOCK_WIDTH / 2;
        int xdir = BLOCK_SPEED;
        int width = eglSurface.getWidth();
        int height = eglSurface.getHeight();

        Log.d(TAG, "Animating " + width + "x" + height + " EGL surface");

        while (true) {
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
        }
    }
}