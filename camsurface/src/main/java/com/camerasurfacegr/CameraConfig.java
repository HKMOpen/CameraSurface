package com.camerasurfacegr;

import android.Manifest;
import android.util.Log;
import android.util.SparseIntArray;
import android.view.Surface;

/**
 * Created by zJJ on 7/4/2016.
 */
public class CameraConfig {


    /**
     * Conversion from screen rotation to JPEG orientation.
     */
    public static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    public static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();

    public static final int REQUEST_VIDEO_PERMISSIONS = 1;
    public static final int REQUEST_CAMERA_PERMISSION = 1;

    public static final String FRAGMENT_DIALOG = "dialog";

    public static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    public static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;

    public static final String[] VIDEO_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Permissions required to take a picture.
     */
    public static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
    };

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 90);
        ORIENTATIONS.append(Surface.ROTATION_90, 0);
        ORIENTATIONS.append(Surface.ROTATION_180, 270);
        ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    /**
     * Tag for the {@link Log}.
     */
    public static final String TAG = "Camera2BasicFragment";

    /**
     * Camera state: Showing camera preview.
     */
    public static final int STATE_PREVIEW = 0;

    /**
     * Camera state: Waiting for the focus to be locked.
     */
    public static final int STATE_WAITING_LOCK = 1;

    /**
     * Camera state: Waiting for the exposure to be precapture state.
     */
    public static final int STATE_WAITING_PRECAPTURE = 2;

    /**
     * Camera state: Waiting for the exposure state to be something other than precapture.
     */
    public static final int STATE_WAITING_NON_PRECAPTURE = 3;

    /**
     * Camera state: Device is closed.
     */
    public static final int STATE_CLOSED = 5;

    /**
     * Camera state: Device is opened, but is not capturing.
     */
    public static final int STATE_OPENED = 6;

    /**
     * Camera state: Waiting for 3A convergence before capturing a photo.
     */
    public static final int STATE_WAITING_FOR_3A_CONVERGENCE = 7;

    /**
     * Camera state: Picture was taken.
     */
    public static final int STATE_PICTURE_TAKEN = 4;

    /**
     * Max preview width that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_WIDTH = 1920;
    /**
     * Max preview height that is guaranteed by Camera2 API
     */
    public static final int MAX_PREVIEW_HEIGHT = 1080;

    /**
     * Timeout for the pre-capture sequence.
     */
    public static final long PRECAPTURE_TIMEOUT_MS = 1000;

    /**
     * Tolerance when comparing aspect ratios.
     */
    public static final double ASPECT_RATIO_TOLERANCE = 0.005;
}
