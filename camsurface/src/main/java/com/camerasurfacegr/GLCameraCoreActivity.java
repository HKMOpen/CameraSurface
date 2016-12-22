package com.camerasurfacegr;

import android.Manifest;
import android.content.pm.ActivityInfo;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.LayoutRes;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Toast;

import com.camerasurfacegr.gles.Cam2Renderer;
import com.camerasurfacegr.gles.ShaderUtils;
import com.camerasurfacegr.prompt.PermissionsHelper;

import java.io.File;
import java.util.Arrays;

/**
 * Created by zJJ on 7/6/2016.
 */
public abstract class GLCameraCoreActivity extends FragmentActivity implements Cam2Renderer.OnRendererReadyListener, PermissionsHelper.PermissionsListener {
    private static final String TAG = GLCameraCoreActivity.class.getSimpleName();
    private static final String TAG_CAMERA_FRAGMENT = "tag_camera_frag";

    /**
     * filename for our test video output
     */
    private static final String TEST_VIDEO_FILE_NAME = "test_video.mp4";
    private PermissionsHelper mPermissionsHelper;
    private TextureView mTextureView;

    /**
     * Custom fragment used for encapsulating all the {@link android.hardware.camera2} apis.
     */
    private Camera2BasicSetup mCameraFragment;

    /**
     * Our custom renderer for this example, which extends {@link Cam2Renderer} and then adds custom
     * shaders, which turns shit green, which is easy.
     */
    private Cam2Renderer mRenderer;

    /**
     * boolean for triggering restart of camera after completed rendering
     */
    private boolean mRestartCamera = false;
    private boolean mPermissionsSatisfied = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        setContentView(layoutRes());
        mTextureView = (TextureView) findViewById(R.id.surface_v1_base);
        bindview(mTextureView);
        setupCameraFragment(startCameraPositionUseBackCamera() ? Camera2BasicSetup.CAMERA_PRIMARY : Camera2BasicSetup.CAMERA_FORWARD);
        setupInteraction();
        //setup permissions for M or start normally
        //setup permissions for M or start normally
        if (PermissionsHelper.isMorHigher())
            setupPermissions();

    }

    protected boolean startCameraPositionUseBackCamera() {
        return true;
    }

    @LayoutRes
    abstract protected int layoutRes();

    abstract protected void bindview(TextureView textureview);

    private void setupPermissions() {
        mPermissionsHelper = PermissionsHelper.attach(this);
        mPermissionsHelper.setRequestedPermissions(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        );
    }


    /**
     * create the camera fragment responsible for handling camera state and add it to our activity
     */
    private void setupCameraFragment(int camera_start_position) {
        if (mCameraFragment != null && mCameraFragment.isAdded())
            return;

        mCameraFragment = Camera2BasicSetup.getInstance();
        mCameraFragment.setCameraToUse(camera_start_position); //pick which camera u want to use, we default to forward
        mCameraFragment.setTextureView(mTextureView);

        //add fragment to our setup and let it work its magic
        FragmentTransaction transaction = getSupportFragmentManager().beginTransaction();
        transaction.add(mCameraFragment, TAG_CAMERA_FRAGMENT);
        transaction.commit();
    }

    /**
     * add a listener for touch on our surface view that will pass raw values to our renderer for
     * use in our shader to control color channels.
     */
    private void setupInteraction() {
        mTextureView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return onScreenTouch(event, event.getX(), event.getY());
            }
        });
    }

    protected abstract boolean onScreenTouch(MotionEvent ev, final float x, final float y);

    /**
     * Things are good to go and we can continue on as normal. If this is called after a user
     * sees a dialog, then onResume will be called next, allowing the app to continue as normal.
     */
    @Override
    public void onPermissionsSatisfied() {
        Log.d(TAG, "onPermissionsSatisfied()");
        mPermissionsSatisfied = true;
    }

    /**
     * User did not grant the permissions needed for out app, so we show a quick toast and kill the
     * activity before it can continue onward.
     *
     * @param failedPermissions string array of which permissions were denied
     */
    @Override
    public void onPermissionsFailed(String[] failedPermissions) {
        Log.e(TAG, "onPermissionsFailed()" + Arrays.toString(failedPermissions));
        mPermissionsSatisfied = false;
        Toast.makeText(this, "shadercam needs all permissions to function, please try again.", Toast.LENGTH_LONG).show();
        this.finish();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
        ShaderUtils.goFullscreen(this.getWindow());
        /**
         * if we're on M and not satisfied, check for permissions needed
         * {@link PermissionsHelper#checkPermissions()} will also instantly return true if we've
         * checked prior and we have all the correct permissions, allowing us to continue, but if its
         * false, we want to {@code return} here so that the popup will trigger without {@link #setReady(SurfaceTexture, int, int)}
         * being called prematurely
         */
        if (PermissionsHelper.isMorHigher() && !mPermissionsSatisfied) {
            if (!mPermissionsHelper.checkPermissions())
                return;
            else
                mPermissionsSatisfied = true; //extra helper as callback sometimes isnt quick enough for future results
        }
        if (!mTextureView.isAvailable())
            mTextureView.setSurfaceTextureListener(mTextureListener); //set listener to handle when its ready
        else
            setReady(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
    }

    @Override
    protected void onPause() {
        super.onPause();
        shutdownCamera(false);
        mTextureView.setSurfaceTextureListener(null);
    }


    public final void onClickRecord() {
        if (mRenderer.isRecording())
            stopRecording();
        else
            startRecording();
    }


    public final void onClickSwapCamera() {
        mCameraFragment.swapCamera();
    }

    public final void captureStillImage() {
        mRenderer.capture();
    }

    /**
     * called whenever surface texture becomes initially available or whenever a camera restarts after
     * completed recording or resuming from onpause
     *
     * @param surface {@link SurfaceTexture} that we'll be drawing into
     * @param width   width of the surface texture
     * @param height  height of the surface texture
     */
    protected void setReady(SurfaceTexture surface, int width, int height) {
        mRenderer = getRenderer(surface, width, height);
        mRenderer.setCameraFragment(mCameraFragment);
        mRenderer.setOnRendererReadyListener(this);
        mRenderer.start();

        //initial config if needed
        mCameraFragment.configureTransform(width, height);
    }

    /**
     * Override this method for easy usage of stock example setup, allowing for easy
     * recording with any shader.
     */
    protected abstract Cam2Renderer getRenderer(SurfaceTexture surface, int width, int height);

    private void startRecording() {
        mRenderer.startRecording(getVideoFile());
        // mRecordBtn.setText("Stop");
        onStartRecording();
    }

    protected void onStartRecording() {

    }

    protected void onStopRecording() {

    }

    private void stopRecording() {
        mRenderer.stopRecording();
        // mRecordBtn.setText("Record");
        //restart so surface is recreated
        shutdownCamera(true);
        onStopRecording();
        Toast.makeText(this, "File recording complete: " + getVideoFile().getAbsolutePath(), Toast.LENGTH_LONG).show();
    }

    private File getVideoFile() {
        return new File(Environment.getExternalStorageDirectory(), TEST_VIDEO_FILE_NAME);
    }

    /**
     * kills the camera in camera fragment and shutsdown render thread
     *
     * @param restart whether or not to restart the camera after shutdown is complete
     */
    private void shutdownCamera(boolean restart) {
        //make sure we're here in a working state with proper permissions when we kill the camera
        if (PermissionsHelper.isMorHigher() && !mPermissionsSatisfied) return;

        //check to make sure we've even created the cam and renderer yet
        if (mCameraFragment == null || mRenderer == null) return;
        mCameraFragment.closeCamera();
        mRestartCamera = restart;
        mRenderer.getRenderHandler().sendShutdown();
        mRenderer = null;
    }

    /**
     * Interface overrides from our {@link Cam2Renderer.OnRendererReadyListener}
     * interface. Since these are being called from inside the Cam2Renderer thread, we need to make sure
     * that we call our methods from the {@link #runOnUiThread(Runnable)} method, so that we don't
     * throw any exceptions about touching the UI from non-UI threads.
     * <p>
     * Another way to handle this would be to create a Handler/Message system similar to how our
     * {@link Cam2Renderer.RenderHandler} works.
     */
    @Override
    public void onRendererReady() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mCameraFragment.setPreviewTexture(mRenderer.getPreviewTexture());
                mCameraFragment.openCamera();
            }
        });
    }

    @Override
    public void onRendererFinished() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (mRestartCamera) {
                    setReady(mTextureView.getSurfaceTexture(), mTextureView.getWidth(), mTextureView.getHeight());
                    mRestartCamera = false;
                }
            }
        });
    }


    /**
     * {@link android.view.TextureView.SurfaceTextureListener} responsible for setting up the rest of the
     * rendering and recording elements once our TextureView is good to go.
     */
    private TextureView.SurfaceTextureListener mTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surface, final int width, final int height) {
            //convenience method since we're calling it from two places
            setReady(surface, width, height);
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
            mCameraFragment.configureTransform(width, height);
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return true;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
        }
    };

}
