package com.camerasurfacegr.gles;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.SurfaceTexture;
import android.media.MediaRecorder;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.opengl.GLUtils;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.MediaStore;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.widget.Toast;

import com.camerasurfacegr.Camera2BasicSetup;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Comparator;

/**
 * Base camera rendering class. Responsible for rendering to proper window contexts, as well as
 * recording video with built-in media recorder.
 * <p/>
 * Subclass this and add any kind of fun stuff u want, new shaders, textures, uniforms - go to town!
 * <p/>
 * TODO: add methods for users to create their own mediarecorders/change basic settings of default mr
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Cam2Renderer extends Thread implements SurfaceTexture.OnFrameAvailableListener {
    private static final String TAG = Cam2Renderer.class.getSimpleName();
    private static final String THREAD_NAME = "CameraRendererThread";

    /**
     * if you create new files, just override these defaults in your subclass and
     * don't edit the {@link #vertexShaderCode} and {@link #fragmentShaderCode} variables
     */
    protected static String DEFAULT_FRAGMENT_SHADER = "camera.frag.glsl";

    protected static String DEFAULT_VERTEX_SHADER = "camera.vert.glsl";
    protected static String DEFAULT_CACHE_FILE_PATH = "/hxxxlop/";
    /**
     * Current context for use with utility methods
     */
    protected Context mContext;

    protected int mSurfaceWidth, mSurfaceHeight;

    protected float mSurfaceAspectRatio;

    /**
     * main texture for display, based on TextureView that is created in activity or fragment
     * and passed in after onSurfaceTextureAvailable is called, guaranteeing its existence.
     */
    private SurfaceTexture mSurfaceTexture;

    /**
     * EGLCore used for creating {@link WindowSurface}s for preview and recording
     */
    private EglCore mEglCore;

    /**
     * Primary {@link WindowSurface} for rendering to screen
     */
    private WindowSurface mWindowSurface;

    /**
     * primary {@link WindowSurface} for use with mediarecorder
     */
    private WindowSurface mRecordSurface;

    /**
     * Texture created for GLES rendering of camera data
     */
    private SurfaceTexture mPreviewTexture;
    private SurfaceTexture mStillImageTexture;
    /**
     * if you override these in ctor of subclass, loader will ignore the files listed above
     */
    protected String vertexShaderCode;

    protected String fragmentShaderCode;

    /**
     * Basic mesh rendering code
     */
    private static float squareSize = 1.0f;

    private static float squareCoords[] = {
            -squareSize, squareSize, // 0.0f,     // top left
            squareSize, squareSize, // 0.0f,   // top right
            -squareSize, -squareSize, // 0.0f,   // bottom left
            squareSize, -squareSize, // 0.0f,   // bottom right
    };

    private static short drawOrder[] = {0, 1, 2, 1, 3, 2};

    private FloatBuffer textureBuffer;
    /**
     * textureCoordinates
     */
    private float textureCoords[] = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f,
    };

    protected int mCameraShaderProgram;

    private FloatBuffer vertexBuffer;

    private ShortBuffer drawListBuffer;

    private int textureCoordinateHandle;

    private int positionHandle;

    /**
     * "arbitrary" maximum number of textures. seems that most phones dont like more than 16
     */
    public static final int MAX_TEXTURES = 16;

    /**
     * for storing all texture ids from genTextures, and used when binding
     * after genTextures, id[0] is reserved for camera texture
     */
    private int[] mTexturesIds = new int[MAX_TEXTURES];

    /**
     * array of proper constants for use in creation,
     * updating, and drawing. most phones max out at 16
     * same number as {@link #MAX_TEXTURES}
     * <p/>
     * Used in our implementation of {@link #addTexture(int, String, boolean)}
     */
    private int[] mTextureConsts = {
            GLES20.GL_TEXTURE1,
            GLES20.GL_TEXTURE2,
            GLES20.GL_TEXTURE3,
            GLES20.GL_TEXTURE4,
            GLES20.GL_TEXTURE5,
            GLES20.GL_TEXTURE6,
            GLES20.GL_TEXTURE7,
            GLES20.GL_TEXTURE8,
            GLES20.GL_TEXTURE9,
            GLES20.GL_TEXTURE10,
            GLES20.GL_TEXTURE11,
            GLES20.GL_TEXTURE12,
            GLES20.GL_TEXTURE13,
            GLES20.GL_TEXTURE14,
            GLES20.GL_TEXTURE15,
            GLES20.GL_TEXTURE16,
    };


    /**
     * array of {@link Texture} objects used for looping through
     * during the render pass. created in {@link #addTexture(int, String, boolean)}
     * and looped in {@link #setExtraTextures()}
     */
    private ArrayList<Texture> mTextureArray;


    /**
     * matrix for transforming our camera texture, available immediately after {@link #mPreviewTexture}s
     * {@code updateTexImage()} is called in our main {@link #draw()} loop.
     */
    private float[] mCameraTransformMatrix = new float[16];

    /**
     * Handler for communcation with the UI thread. Implementation below at
     * {@link RenderHandler RenderHandler}
     */
    private RenderHandler mHandler;

    /**
     * Interface listener for some callbacks to the UI thread when rendering is setup and finished.
     */
    private OnRendererReadyListener mOnRendererReadyListener;
    /**
     * Interface listener for the callback from the capturing still images
     */
    private OnCaptureStillImageComplete mOnCaptureStillImageComplete;
    /**
     * Width and height storage of our viewport size, so we can properly accomodate any size View
     * used to display our preview on screen.
     */
    private int mViewportWidth, mViewportHeight;

    /**
     * boolean for recording so we cans wap the recording buffer into place
     */
    private boolean mIsRecording = false, mCaptureStillImage = false;

    /**
     * Reference to our users CameraFragment to ease setting viewport size. Thought about decoupling but wasn't
     * worth the listener/callback hastle
     */
    private Camera2BasicSetup mCameraFragment;

    /**
     * Default {@link MediaRecorder} instance so we can record all the cool shit we make. You can override this,
     * but make sure you handle the deletion of temp files yourself.
     */
    private MediaRecorder mMediaRecorder;


    /**
     * Bitrate of our recorded video passed to our default {@link MediaRecorder}
     */
    private static final int VIDEO_BIT_RATE = 10000000;

    private static final int VIDEO_WIDTH = 720;

    /**
     * Height of our recorded video - notice that if we use {@link com.camerasurfacegr.Views.SquareTextureView} that
     * we can pss in the same value as the width here to make sure we render out a square movie. Otherwise, it will stretch the square
     * textureview preview into a fullsize video. Play with the numbers here and the size of the TextureView you use to see the different types
     * of output depending on scale values
     */
    private static final int VIDEO_HEIGHT = 1280;

    /**
     * Array of ints for use with screen orientation hint in our MediaRecorder.
     * See {@link #setupMediaRecorder()} for more info on its usage.
     */
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    /**
     * temp file we write to for recording, then copy to where user wants to save video file
     */
    private File mTempOutputFile;

    /**
     * file passed by user where to save the video upon completion of recording
     */
    private File mOutputFile = null;

    private String mFragmentShaderPath;
    private String mVertexShaderPath;


    /**
     * Simple ctor to use default shaders
     */
    public Cam2Renderer(Context context, SurfaceTexture texture, int width, int height) {
        init(context, texture, width, height, DEFAULT_FRAGMENT_SHADER, DEFAULT_VERTEX_SHADER);
    }

    /**
     * Main constructor for passing in shaders to override the default shader.
     * Context, texture, width, and height are passed in automatically by CameraTextureListener
     *
     * @param fragPath the file name of your fragment shader, ex: "lip_service.frag" if it is top-level /assets/ folder. Add subdirectories if needed
     * @param vertPath the file name of your vertex shader, ex: "lip_service.vert" if it is top-level /assets/ folder. Add subdirectories if needed
     */
    public Cam2Renderer(Context context, SurfaceTexture texture, int width, int height, String fragPath, String vertPath) {
        init(context, texture, width, height, fragPath, vertPath);
    }

    private void init(Context context, SurfaceTexture texture, int width, int height, String fragPath, String vertPath) {
        this.setName(THREAD_NAME);

        this.mContext = context;
        this.mSurfaceTexture = texture;

        this.mSurfaceWidth = width;
        this.mSurfaceHeight = height;
        this.mSurfaceAspectRatio = (float) width / height;

        this.mFragmentShaderPath = fragPath;
        this.mVertexShaderPath = vertPath;
    }

    private void initialize() {
        mTextureArray = new ArrayList<>();

        setupCameraFragment();
        setupMediaRecorder();
        setViewport(mSurfaceWidth, mSurfaceHeight);

        if (fragmentShaderCode == null || vertexShaderCode == null) {
            loadFromShadersFromAssets(mFragmentShaderPath, mVertexShaderPath);
        }
    }

    private void setupCameraFragment() {
        if (mCameraFragment == null) {
            throw new RuntimeException("CameraFragment is null! Please call setCameraFragment prior to initialization.");
        }

        mCameraFragment.setOnViewportSizeUpdatedListener(new Camera2BasicSetup.OnViewportSizeUpdatedListener() {
            @Override
            public void onViewportSizeUpdated(int viewportWidth, int viewportHeight) {
                mViewportWidth = viewportWidth;
                mViewportHeight = viewportHeight;
            }
        });
    }

    private void loadFromShadersFromAssets(String pathToFragment, String pathToVertex) {
        try {
            fragmentShaderCode = ShaderUtils.getStringFromFileInAssets(mContext, pathToFragment);
            vertexShaderCode = ShaderUtils.getStringFromFileInAssets(mContext, pathToVertex);
        } catch (IOException e) {
            Log.e(TAG, "loadFromShadersFromAssets() failed. Check paths to assets.\n" + e.getMessage());
        }
    }

    /**
     * In order to properly make use of our awesome camera fragment and its renderers, we want
     * to record the cool shit we do - so lets use the stock {@link MediaRecorder} class to do that.
     * Because, i mean, why would I want to waste a month writing and implementing my own version
     * when this should do it all on its own, right? ...right? :(
     */

    private void setupMediaRecorder() {
        File outputDir = mContext.getCacheDir();
        try {
            mTempOutputFile = File.createTempFile("temp_mov", "mp4", outputDir);
        } catch (IOException e) {
            throw new RuntimeException("Temp file could not be created. Message: " + e.getMessage());
        }

        mMediaRecorder = new MediaRecorder();

        //set the sources
        /**
         * {@link MediaRecorder.AudioSource.CAMCORDER} is nice because on some fancier
         * phones microphones will be aligned towards whatever camera is being used, giving us better
         * directional audio. And if it doesn't have that, it will fallback to the default Microphone.
         */
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);

        /**
         * Using {@link MediaRecorder.VideoSource.SURFACE} creates a {@link Surface}
         * for us to use behind the scenes. We then pass this service to our {@link ExampleRenderer}
         * later on for creation of our EGL contexts to render to.
         *
         * {@link MediaRecorder.VideoSource.SURFACE} is also the default for rendering
         * out Camera2 api data without any shader manipulation at all.
         */
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);

        //set output
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        /**
         * This would eventually be worth making a parameter at each call to {@link #setupMediaRecorder()}
         * so that you can pass in a timestamp or unique file name each time to setup up.
         */
        mMediaRecorder.setOutputFile(mTempOutputFile.getPath());

        /**
         * Media Recorder can be finicky with certain video sizes, so lets make sure we pass it
         * something 'normal' - ie 720p or 1080p. this will create a surface of the same size,
         * which will be used by our renderer for drawing once recording is enabled
         */
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setVideoEncodingBitRate(VIDEO_BIT_RATE);
        mMediaRecorder.setVideoSize(VIDEO_WIDTH, VIDEO_HEIGHT);
        mMediaRecorder.setVideoFrameRate(30);

        //setup audio
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mMediaRecorder.setAudioEncodingBitRate(44800);

        /**
         * we can determine the rotation and orientation of our screen here for dynamic usage
         * but since we know our app will be portrait only, setting the media recorder to
         * 720x1280 rather than 1280x720 and letting orientation be 0 will keep everything looking normal
         */
        int rotation = ((Activity) mContext).getWindowManager().getDefaultDisplay().getRotation();
        int orientation = ORIENTATIONS.get(rotation);
        Log.d(TAG, "orientation: " + orientation);
        mMediaRecorder.setOrientationHint(0);

        try {
            /**
             * There are what seems like an infinite number of ways to fuck up the previous steps,
             * so prepare() will throw an exception if you fail, and hope that stackoverflow can help.
             */
            mMediaRecorder.prepare();
        } catch (IOException e) {
            Toast.makeText(mContext, "MediaRecorder failed on prepare()", Toast.LENGTH_LONG).show();
            Log.e(TAG, "MediaRecorder failed on prepare() " + e.getMessage());
        }

        Log.d(TAG, "MediaRecorder surface: " + mMediaRecorder.getSurface() + " isValid: " + mMediaRecorder.getSurface().isValid());
    }

    /**
     * Initialize all necessary components for GLES rendering, creating window surfaces for drawing
     * the preview as well as the surface that will be used by MediaRecorder for recording
     */
    public void initGL() {
        mEglCore = new EglCore(null, EglCore.FLAG_RECORDABLE | EglCore.FLAG_TRY_GLES3);

        //create preview surface
        mWindowSurface = new WindowSurface(mEglCore, mSurfaceTexture);
        mWindowSurface.makeCurrent();

        //create recording surface
        mRecordSurface = new WindowSurface(mEglCore, mMediaRecorder.getSurface(), false);


        initGLComponents();
    }


    private void initGLComponents() {
        onPreSetupGLComponents();

        setupVertexBuffer();
        setupTextures();
        setupCameraTexture();
        setupShaders();

        onSetupComplete();
    }


    // ------------------------------------------------------------
    // deinit
    // ------------------------------------------------------------

    public void deinitGL() {
        deinitGLComponents();

        mWindowSurface.release();
        mRecordSurface.release();
        mEglCore.release();

        if (mMediaRecorder != null)
            mMediaRecorder.release();

        removeUnusedFrame();
    }

    private void removeUnusedFrame() {

        int[] values = new int[1];

        if (mOffscreenTexture > 0) {
            values[0] = mOffscreenTexture;
            GLES20.glDeleteTextures(1, values, 0);
            mOffscreenTexture = -1;
        }
        if (mFramebuffer > 0) {
            values[0] = mFramebuffer;
            GLES20.glDeleteFramebuffers(1, values, 0);
            mFramebuffer = -1;
        }
        if (mDepthBuffer > 0) {
            values[0] = mDepthBuffer;
            GLES20.glDeleteRenderbuffers(1, values, 0);
            mDepthBuffer = -1;
        }
    }

    protected void deinitGLComponents() {
        GLES20.glDeleteTextures(mTextureArray.size(), mTexturesIds, 0);
        GLES20.glDeleteProgram(mCameraShaderProgram);

        mPreviewTexture.release();
        mPreviewTexture.setOnFrameAvailableListener(null);
    }

    // ------------------------------------------------------------
    // setup
    // ------------------------------------------------------------

    /**
     * override this method if there's anything else u want to accomplish before
     * the main camera setup gets underway
     */
    protected void onPreSetupGLComponents() {
        //capture images
     /*   mFullFrameBlit = new FullFrameRect(new Texture2dProgram(Texture2dProgram.ProgramType.TEXTURE_EXT));
        mTextureIdContinueImages = mFullFrameBlit.createTextureObject();
        mStillImageTexture = new SurfaceTexture(mTextureIdContinueImages);
        mStillImageTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
            @Override
            public void onFrameAvailable(SurfaceTexture surfaceTexture) {

            }
        });

        mCircEncoder = new CircularEncoder(VIDEO_WIDTH, VIDEO_HEIGHT, 6000000, mCameraPreviewThousandFps / 1000, 7, mHandler);
        mEncoderSurface = new WindowSurface(mEglCore, mCircEncoder.getInputSurface(), true);
*/
    }

    protected void setupVertexBuffer() {
        // Draw list buffer
        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        // Initialize the texture holder
        ByteBuffer bb = ByteBuffer.allocateDirect(squareCoords.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareCoords);
        vertexBuffer.position(0);
    }

    /**
     * Remember that Android's camera api returns camera texture not as {@link GLES20#GL_TEXTURE_2D}
     * but rather as {@link GLES11Ext#GL_TEXTURE_EXTERNAL_OES}, which we bind here
     */
    protected void setupCameraTexture() {
        //set texture[0] to camera texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexturesIds[0]);
        checkGlError("Texture bind");

        mPreviewTexture = new SurfaceTexture(mTexturesIds[0]);
        mPreviewTexture.setOnFrameAvailableListener(this);
    }

    /**
     * Handling this manually here but check out another impl at {@link GlUtil#createProgram(String, String)}
     */
    protected void setupShaders() {
        int vertexShaderHandle = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShaderHandle, vertexShaderCode);
        GLES20.glCompileShader(vertexShaderHandle);
        checkGlError("Vertex shader compile");

        Log.d(TAG, "vertexShader info log:\n " + GLES20.glGetShaderInfoLog(vertexShaderHandle));

        int fragmentShaderHandle = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShaderHandle, fragmentShaderCode);
        GLES20.glCompileShader(fragmentShaderHandle);
        checkGlError("Pixel shader compile");

        Log.d(TAG, "fragmentShader info log:\n " + GLES20.glGetShaderInfoLog(fragmentShaderHandle));

        mCameraShaderProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(mCameraShaderProgram, vertexShaderHandle);
        GLES20.glAttachShader(mCameraShaderProgram, fragmentShaderHandle);
        GLES20.glLinkProgram(mCameraShaderProgram);
        checkGlError("Shader program compile");

        int[] status = new int[1];
        GLES20.glGetProgramiv(mCameraShaderProgram, GLES20.GL_LINK_STATUS, status, 0);
        if (status[0] != GLES20.GL_TRUE) {
            String error = GLES20.glGetProgramInfoLog(mCameraShaderProgram);
            String fatal = "Error while linking program:\n" + error;
            Log.e("SurfaceTest", fatal);
            compile_error(fatal);
        }
    }

    public interface errorOutput {
        void out(String ex);
    }

    private errorOutput glerror;

    public final void setErrorOutput(errorOutput m) {
        glerror = m;
    }

    protected void compile_error(String error) {
        if (glerror != null) glerror.out(error);
    }

    /**
     * called when all setup is complete on basic GL stuffs
     * override for adding textures and other shaders and make sure to call
     * super so that we can let them know we're done
     */
    protected void onSetupComplete() {
        mOnRendererReadyListener.onRendererReady();
    }

    @Override
    public synchronized void start() {
        initialize();

        if (mOnRendererReadyListener == null)
            throw new RuntimeException("OnRenderReadyListener is not set! Set listener prior to calling start()");

        super.start();
    }


    /**
     * primary loop - this does all the good things
     */
    @Override
    public void run() {
        Looper.prepare();

        //create handler for communication from UI
        mHandler = new RenderHandler(this);

        //initialize all GL on this context
        initGL();

        //LOOOOOOOOOOOOOOOOP
        Looper.loop();

        //we're done here
        deinitGL();

        mOnRendererReadyListener.onRendererFinished();
    }

    /**
     * stop our thread, and make sure we kill a recording if its still happening
     * <p/>
     * this should only be called from our handler to ensure thread-safe
     */
    public void shutdown() {
        synchronized (this) {
            if (mIsRecording)
                stopRecording();
            else //not recording but still needs release
                mMediaRecorder.release();
        }

        //kill ouy thread
        Looper.myLooper().quit();
    }

    private int mOffscreenTexture;
    private int mDepthBuffer;
    private int mFramebuffer;
    private File cache_file_path_location;

    public void savePixelsGL2(int x, int y, int w, int h, File filename) throws IOException, FileNotFoundException {

        int[] frame = new int[1];

        // Create a texture object and bind it.  This will be the color buffer.
        GLES20.glGenTextures(1, frame, 0);
        GlUtil.checkGlError("glGenTextures");
        mOffscreenTexture = frame[0];   // expected > 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mOffscreenTexture);
        GlUtil.checkGlError("glBindTexture " + mOffscreenTexture);

        GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, w, h, 0,
                GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);


        // Set parameters.  We're probably using non-power-of-two dimensions, so
        // some values may not be available for use.
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER,
                GLES20.GL_NEAREST);
        GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER,
                GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S,
                GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T,
                GLES20.GL_CLAMP_TO_EDGE);
        GlUtil.checkGlError("glTexParameter");


        // Create framebuffer object and bind it.
        GLES20.glGenFramebuffers(1, frame, 0);
        checkGlError("glGenFramebuffers");
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, frame[0]);
        checkGlError("glBindFramebuffer" + frame[0]);
        // Create a depth buffer and bind it.
        GLES20.glGenRenderbuffers(1, frame, 0);
        GlUtil.checkGlError("glGenRenderbuffers");
        mDepthBuffer = frame[0];    // expected > 0
        GLES20.glBindRenderbuffer(GLES20.GL_RENDERBUFFER, mDepthBuffer);
        GlUtil.checkGlError("glBindRenderbuffer " + mDepthBuffer);

        // Allocate storage for the depth buffer.
        GLES20.glRenderbufferStorage(GLES20.GL_RENDERBUFFER, GLES20.GL_DEPTH_COMPONENT16, w, h);
        GlUtil.checkGlError("glRenderbufferStorage");

        // Attach the depth buffer and the texture (color buffer) to the framebuffer object.
        GLES20.glFramebufferRenderbuffer(GLES20.GL_FRAMEBUFFER, GLES20.GL_DEPTH_ATTACHMENT,
                GLES20.GL_RENDERBUFFER, mDepthBuffer);
        GlUtil.checkGlError("glFramebufferRenderbuffer");
        GLES20.glFramebufferTexture2D(GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, mOffscreenTexture, 0);
        GlUtil.checkGlError("glFramebufferTexture2D");


        // See if GLES is happy with all this.
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Framebuffer not complete, status=" + status);
        }

        // Switch back to the default framebuffer.
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GlUtil.checkGlError("prepareFramebuffer done");
        ByteBuffer buf = ByteBuffer.allocateDirect(w * h * 4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
        GlUtil.checkGlError("glReadPixels");
        buf.rewind();


        BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filename));
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bmp.copyPixelsFromBuffer(buf);
        bmp.compress(Bitmap.CompressFormat.JPEG, 100, bos);
        bmp.recycle();
        bos.close();
        removeUnusedFrame();

        //  BitmapFactory.Options options = new BitmapFactory.Options();
        //  options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        //  Bitmap bitmap = BitmapFactory.decodeFile(filename.getAbsolutePath(), options);

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        options.inScaled = false;
        Bitmap bm = null;
        BufferedInputStream bin = new BufferedInputStream(new FileInputStream(filename));

        boolean going = true;
        while (going) {
            //bm = BitmapFactory.decodeFile(filename.getAbsolutePath());
            byte[] bMapArray = new byte[bin.available()];
            if (bMapArray.length == 0) continue;
            bin.read(bMapArray);
            // bm = BitmapFactory.decodeStream(fis, null, options);
            bm = BitmapFactory.decodeByteArray(bMapArray, 0, bMapArray.length);
            if (bm == null) continue;
            going = false;
        }

        bm = flip(bm, w, h);
        //ByteArrayOutputStream bin = new ByteArrayOutputStream();
        bos = new BufferedOutputStream(new FileOutputStream(filename));
        bm.compress(Bitmap.CompressFormat.JPEG, 100, bos);
        bm.recycle();
        bos.close();
        bin.close();
        // byte[] b = baos.toByteArray();
        // encImage = Base64.encodeToString(b, Base64.DEFAULT);
        Log.d(TAG, "Saved " + w + "x" + h + " frame as '" + filename.toString() + "'");
        cache_file_path_location = filename;
    }

    public final File getSavedCacheFile() {
        return cache_file_path_location;
    }

    /**
     * Creates a new bitmap by flipping the specified bitmap
     * vertically or horizontally.
     *
     * @param src Bitmap to flip
     * @return New bitmap created by flipping the given one
     * vertically or horizontally as specified by
     * the <code>type</code> parameter or
     * the original bitmap if an unknown type
     * is specified.
     **/
    public static Bitmap flip(@NonNull final Bitmap src, final int w, final int h) {
        Matrix matrix = new Matrix();
        matrix.preScale(1.0f, -1.0f);
        return Bitmap.createBitmap(src, 0, 0, w, h, matrix, true);
    }

    private void save_to_path(String d, Bitmap screen) {
        String path = MediaStore.Images.Media.insertImage(mContext.getContentResolver(), screen, "screenShotBJ" + d + ".png", null);
    }

    public static void saveToCacheFile(Bitmap bmp) {
        saveToFile(getCacheFilename(), bmp);
    }

    public static String getCacheFilename() {
        File f = getSavePath();
        return f.getAbsolutePath() + "/m_verification_cache.png";
    }

    public static File getCacheFile() {
        File f = getSavePath();
        String e = f.getAbsolutePath() + "/m_verification_cache.png";
        return new File(e);
    }

    public static void saveToFile(String filename, Bitmap bmp) {
        try {
            FileOutputStream out = new FileOutputStream(filename);
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out);
            out.flush();
            out.close();
        } catch (Exception e) {
        }
    }

    public static File getSavePath() {
        File path;
        if (hasSDCard()) { // SD card
            path = new File(getSDCardPath() + DEFAULT_CACHE_FILE_PATH);
            path.mkdir();
        } else {
            path = Environment.getDataDirectory();
        }
        return path;
    }

    public static boolean hasSDCard() { // SD????????
        String status = Environment.getExternalStorageState();
        return status.equals(Environment.MEDIA_MOUNTED);
    }

    public static String getSDCardPath() {
        File path = Environment.getExternalStorageDirectory();
        return path.getAbsolutePath();
    }


    /**
     * update the SurfaceTexture to the latest camera image
     */
    protected void updatePreviewTexture() {
        mPreviewTexture.updateTexImage();
        mPreviewTexture.getTransformMatrix(mCameraTransformMatrix);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        boolean swapResult;

        synchronized (this) {
            updatePreviewTexture();

            if (mEglCore.getGlVersion() >= 3) {
                draw();
                if (mIsRecording) {
                    mRecordSurface.makeCurrentReadFrom(mWindowSurface);
                    GlUtil.checkGlError("before glBlitFramebuffer");
                    GLES30.glBlitFramebuffer(
                            0, 0, mWindowSurface.getWidth(), mWindowSurface.getHeight(),
                            0, 0, mRecordSurface.getWidth(), mRecordSurface.getHeight(), //must match the mediarecorder surface size
                            GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST
                    );

                    int err;
                    if ((err = GLES30.glGetError()) != GLES30.GL_NO_ERROR)
                        Log.w(TAG, "ERROR: glBlitFramebuffer failed: 0x" + Integer.toHexString(err));
                    mRecordSurface.setPresentationTime(surfaceTexture.getTimestamp());
                    mRecordSurface.swapBuffers();
                }
            } else {
                /**
                 * GL v2
                 */
                draw();
                if (mIsRecording) {
                    // Draw for recording, swap.
                    mRecordSurface.makeCurrent();
                    setViewport(mRecordSurface.getWidth(), mRecordSurface.getHeight());
                    draw();
                    mRecordSurface.setPresentationTime(surfaceTexture.getTimestamp());
                    mRecordSurface.swapBuffers();
                    setViewport(mWindowSurface.getWidth(), mWindowSurface.getHeight());
                }
            }

            if (mCaptureStillImage) {
                try {
                    savePixelsGL2(0, 0, mWindowSurface.getWidth(), mWindowSurface.getHeight(), getCacheFile());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                if (mOnCaptureStillImageComplete != null) {
                    mOnCaptureStillImageComplete.complete(getSavePath().toString());
                }
                mCaptureStillImage = false;
            }
            //swap main buff
            mWindowSurface.makeCurrent();
            swapResult = mWindowSurface.swapBuffers();
            if (!swapResult) {
                // This can happen if the Activity stops without waiting for us to halt.
                Log.e(TAG, "swapBuffers failed, killing renderer thread");
                shutdown();
            }
        }
    }

    private Bitmap combine(Bitmap bitmap1, Bitmap bitmap2, int w, int h) {
        Bitmap bitmap = null;
        try {

            bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);

            //Bitmap bitmap1 = BitmapFactory.decodeResource(mContext, R.drawable.test1); //blue
            //  Bitmap bitmap2 = BitmapFactory.decodeResource(mContext, R.drawable.test2); //green

/*
            Drawable drawable1 = new BitmapDrawable(bitmap1);
            Drawable drawable2 = new BitmapDrawable(bitmap2);


            drawable1.setBounds(100, 100, 400, 400);
            drawable2.setBounds(150, 150, 350, 350);
            drawable1.draw(c);
            drawable2.draw(c);*/
            canvas.drawBitmap(bitmap1, 0, 0, null);
            canvas.drawBitmap(bitmap2, 0, 0, null);


        } catch (Exception e) {

        }

        return bitmap;
    }

    /**
     * Compares two {@code Size}s based on their areas.
     */
    static class CompareSizesByArea implements Comparator<Size> {

        @Override
        public int compare(Size lhs, Size rhs) {
            // We cast here to ensure the multiplications won't overflow
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() -
                    (long) rhs.getWidth() * rhs.getHeight());
        }

    }

    /**
     * main draw routine
     */
    public void draw() {
        GLES20.glViewport(0, 0, mViewportWidth, mViewportHeight);

        GLES20.glClearColor(1.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        //set shader
        GLES20.glUseProgram(mCameraShaderProgram);

        setUniformsAndAttribs();
        setExtraTextures();
        drawElements();
        onDrawCleanup();
    }


    /**
     * base amount of attributes needed for rendering camera to screen
     */
    protected void setUniformsAndAttribs() {
        int textureParamHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "camTexture");
        int textureTranformHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, "camTextureTransform");
        textureCoordinateHandle = GLES20.glGetAttribLocation(mCameraShaderProgram, "camTexCoordinate");
        positionHandle = GLES20.glGetAttribLocation(mCameraShaderProgram, "position");


        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 4 * 2, vertexBuffer);

        //camera texture
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTexturesIds[0]);
        GLES20.glUniform1i(textureParamHandle, 0);

        GLES20.glEnableVertexAttribArray(textureCoordinateHandle);
        GLES20.glVertexAttribPointer(textureCoordinateHandle, 2, GLES20.GL_FLOAT, false, 4 * 2, textureBuffer);

        GLES20.glUniformMatrix4fv(textureTranformHandle, 1, false, mCameraTransformMatrix, 0);
    }

    /**
     * override this and copy if u want to add your own mTexturesIds
     * if u need different uv coordinates, refer to {@link #setupTextures()}
     * for how to create your own buffer
     */
    protected void setExtraTextures() {
        for (int i = 0; i < mTextureArray.size(); i++) {
            Texture tex = mTextureArray.get(i);
            int imageParamHandle = GLES20.glGetUniformLocation(mCameraShaderProgram, tex.uniformName);
            int imageCParamHandle = GLES20.glGetAttribLocation(mCameraShaderProgram, tex.uniformName + "_txCoordinate");
            GLES20.glActiveTexture(tex.texId);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexturesIds[tex.texNum]);
            GLES20.glUniform1i(imageParamHandle, tex.texNum);
        }
    }

    private void setupInitialTexture() {
        for (int i = 0; i < mTextureArray.size(); i++) {
            Texture data = mTextureArray.get(i);
            GLES20.glActiveTexture(data.texId);
            checkGlError("Texture generate");
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexturesIds[data.texNum]);
            checkGlError("Texture bind");
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
            if (data.wrapping) {
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_REPEAT);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_REPEAT);
            } else {
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            }
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, data.memslot, 0);
            GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
            checkGlError("declare image bind");
            data.memslot.recycle();
        }
    }

    /**
     * calling {@link #addTexture(int, String, boolean)} ()} or {@link #addTexture(int, Bitmap, String, boolean, boolean)}
     * has to be in this override
     * {@link #addTextures()}
     */
    protected void addTextures() {
        /**
         * e.g. example

         addTexture(R.drawable.ic_nike, "image_logo", false);
         addTexture(R.drawable.ic_hkcloud, "image_cloud", true);
         */
    }

    private void setupTextures() {
        ByteBuffer texturebb = ByteBuffer.allocateDirect(textureCoords.length * 4);
        texturebb.order(ByteOrder.nativeOrder());
        textureBuffer = texturebb.asFloatBuffer();
        textureBuffer.put(textureCoords);
        textureBuffer.position(0);
        addTextures();
        // mTexturesIds = new int[mTextureArray.size() + 1];
        // Generate the max amount texture ids
        GLES20.glGenTextures(mTextureArray.size(), mTexturesIds, 0);
        checkGlError("Texture generate");
        setupInitialTexture();
    }

    /**
     * creates a new texture with specified resource id and returns the
     * tex id num upon completion
     *
     * @param resource_id the resource id
     * @param uniformName the item name
     * @return this is the number
     */
    protected final int addTexture(final @DrawableRes int resource_id, final String uniformName, final boolean wrapping_repeating) {
        int texId = mTextureConsts[mTextureArray.size()];
        if (mTextureArray.size() + 1 >= MAX_TEXTURES)
            throw new IllegalStateException("Too many textures! Please don't use so many :(");
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeResource(mContext.getResources(), resource_id, options);
        return addTexture(texId, bmp, uniformName, true, wrapping_repeating);
    }

    protected final int addTextureFromAssetsFolder(final String path_name, final String uniformName, final boolean repeating) throws Exception {
        int texId = mTextureConsts[mTextureArray.size()];
        if (mTextureArray.size() + 1 >= MAX_TEXTURES)
            throw new IllegalStateException("Too many textures! Please don't use so many :(");
        InputStream stream = mContext.getAssets().open(path_name);
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inScaled = false;
        Bitmap bmp = BitmapFactory.decodeStream(stream, null, options);
        stream.close();
        return addTexture(texId, bmp, uniformName, true, repeating);
    }

    private int addTexture(int texId, Bitmap bitmap, String uniformName, boolean recycle, boolean wrapping_repeating) {
        int num = mTextureArray.size() + 1;
        Texture tex = new Texture(num, texId, uniformName, bitmap, wrapping_repeating);
        if (!mTextureArray.contains(tex)) {
            mTextureArray.add(tex);
            Log.d(TAG, "addedTexture() " + mTexturesIds[num] + " : " + tex);
        }
        return num;
    }

    /**
     * updates specific texture and recycles bitmap used for updating
     *
     * @param texNum       the texture id
     * @param drawingCache the bitmap update part
     */
    public void updateTexture(final int texNum, Bitmap drawingCache) {
        GLES20.glActiveTexture(mTextureConsts[texNum - 1]);
        checkGlError("Texture generate");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTexturesIds[texNum]);
        checkGlError("Texture bind");
        GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, drawingCache);
        checkGlError("Tex Sub Image");

        drawingCache.recycle();
    }


    protected void drawElements() {
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length, GLES20.GL_UNSIGNED_SHORT, drawListBuffer);
    }

    /***
     * Since we are not using a buffer object, disable vertex arrays for this attribute.
     */
    protected void onDrawCleanup() {
        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(textureCoordinateHandle);
    }

    /**
     * utility for checking GL errors
     *
     * @param op
     */
    public void checkGlError(String op) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e("SurfaceTest", op + ": glError " + GLUtils.getEGLErrorString(error));
        }
    }

    //getters and setters

    public void setViewport(int viewportWidth, int viewportHeight) {
        mViewportWidth = viewportWidth;
        mViewportHeight = viewportHeight;
    }

    public float[] getCameraTransformMatrix() {
        return mCameraTransformMatrix;
    }

    public SurfaceTexture getPreviewTexture() {
        return mPreviewTexture;
    }

    public RenderHandler getRenderHandler() {
        return mHandler;
    }

    public void setOnRendererReadyListener(OnRendererReadyListener listener) {
        mOnRendererReadyListener = listener;

    }

    public interface OnCaptureStillImageComplete {
        void complete(final String path_data);
    }

    public void setOnCaptureStillImageComplete(OnCaptureStillImageComplete callback) {
        mOnCaptureStillImageComplete = callback;
    }

    /**
     * Triggers our built-in MediaRecorder to start recording
     *
     * @param outputFile a {@link File} where we'll be saving the completed render
     */
    public void startRecording(File outputFile) {
        mOutputFile = outputFile;

        if (mOutputFile == null)
            throw new RuntimeException("No output file specified! Make sure to call setOutputFile prior to recording!");

        synchronized (this) {
            mIsRecording = true;
            mMediaRecorder.start();
        }
    }

    /**
     * stops our mediarecorder if its still running and starts our copy from temp to regular
     */
    public void stopRecording() {
        synchronized (this) {
            if (!mIsRecording)
                return;

            mMediaRecorder.stop();
            mMediaRecorder.release();

            mIsRecording = false;

            try {
                copyFile(mTempOutputFile, mOutputFile);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isRecording() {
        synchronized (this) {
            return mIsRecording;
        }
    }

    public final void capture() {
        if (!mCaptureStillImage) {
            mCaptureStillImage = true;
        }
    }

    /**
     * Copies file recorded to our temp file into the user-defined file upon completion
     */
    protected void copyFile(File src, File dst) throws IOException {
        FileChannel inChannel = new FileInputStream(src).getChannel();
        FileChannel outChannel = new FileOutputStream(dst).getChannel();

        try {
            inChannel.transferTo(0, inChannel.size(), outChannel);
        } finally {
            if (inChannel != null)
                inChannel.close();

            if (outChannel != null)
                outChannel.close();
        }
    }

    public void setCameraFragment(Camera2BasicSetup cameraFragment) {
        mCameraFragment = cameraFragment;
    }

    /**
     * Internal class for storing refs to mTexturesIds for rendering
     */
    private class Texture {
        public int texNum;
        public int texId;
        public String uniformName;
        public Bitmap memslot;
        public boolean wrapping;

        private Texture(int texNum, int texId, String uniformName, Bitmap mem, boolean wrapping) {
            this.texNum = texNum;
            this.texId = texId;
            this.uniformName = uniformName;
            this.memslot = mem;
            this.wrapping = wrapping;
        }

        @Override
        public String toString() {
            return "[Texture] num: " + texNum + " id: " + texId + ", uniformName: " + uniformName;
        }

    }

    /**
     * {@link Handler} responsible for communication between this render thread and the UI thread.
     * <p/>
     * For now, the only thing we really need to worry about is shutting down the thread upon completion
     * of recording, since we cannot access the {@link android.media.MediaRecorder} surface once
     * {@link MediaRecorder#stop()} is called.
     */
    public static class RenderHandler extends Handler {
        private static final String TAG = RenderHandler.class.getSimpleName();

        private static final int MSG_SHUTDOWN = 0;

        /**
         * Our camera renderer ref, weak since we're dealing with static class so it doesn't leak
         */
        private WeakReference<Cam2Renderer> mWeakRenderer;

        /**
         * Call from render thread.
         */
        public RenderHandler(Cam2Renderer rt) {
            mWeakRenderer = new WeakReference<>(rt);
        }

        /**
         * Sends the "shutdown" message, which tells the render thread to halt.
         * Call from UI thread.
         */
        public void sendShutdown() {
            sendMessage(obtainMessage(RenderHandler.MSG_SHUTDOWN));
        }

        @Override
        public void handleMessage(Message msg) {
            Cam2Renderer renderer = mWeakRenderer.get();
            if (renderer == null) {
                Log.w(TAG, "RenderHandler.handleMessage: weak ref is null");
                return;
            }

            int what = msg.what;
            switch (what) {
                case MSG_SHUTDOWN:
                    renderer.shutdown();
                    break;
                default:
                    throw new RuntimeException("unknown message " + what);
            }
        }
    }

    /**
     * Interface for callbacks when render thread completes its setup
     */
    public interface OnRendererReadyListener {
        /**
         * Called when {@link #onSetupComplete()} is finished with its routine
         */
        void onRendererReady();

        /**
         * Called once the looper is killed and our {@link #run()} method completes
         */
        void onRendererFinished();
    }


}
