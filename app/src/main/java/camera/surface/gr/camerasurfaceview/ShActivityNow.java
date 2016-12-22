package camera.surface.gr.camerasurfaceview;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.SurfaceTexture;
import android.util.Log;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.camerasurfacegr.GLCameraCoreActivity;
import com.camerasurfacegr.gles.Cam2Renderer;

import camera.surface.gr.camerasurfaceview.RenderFilters.LipsKissRender;

/**
 * Created by zJJ on 7/4/2016.
 */
public class ShActivityNow extends GLCameraCoreActivity implements SeekBar.OnSeekBarChangeListener {

    @Override
    protected int layoutRes() {
        return R.layout.sh_cam_v2;
    }

    private SeekBar mSeekbar;
    private Button swapcam, recordcam, mcapture, pos;
    private TextView hmx;

    @Override
    protected void bindview(TextureView textureview) {
        hmx = (TextView) findViewById(R.id.debugt);
        mSeekbar = (SeekBar) findViewById(R.id.seek_bar);
        mSeekbar.getProgressDrawable().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        mSeekbar.getThumb().setColorFilter(Color.RED, PorterDuff.Mode.SRC_IN);
        recordcam = (Button) findViewById(R.id.btn_record);
        swapcam = (Button) findViewById(R.id.btn_swap_camera);
        pos = (Button) findViewById(R.id.btn_random_position);
        mcapture = (Button) findViewById(R.id.btn_capture);
        recordcam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickRecord();
            }
        });
        swapcam.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onClickSwapCamera();
            }
        });
        mSeekbar.setOnSeekBarChangeListener(this);
        mcapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                captureStillImage();
            }
        });
        pos.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                cRenderer.setRandomPositionLogo();
            }
        });
    }

    LipsKissRender cRenderer;

    @Override
    protected LipsKissRender getRenderer(SurfaceTexture surface, int width, int height) {
        cRenderer = new LipsKissRender(this, surface, width, height);
        cRenderer.setErrorOutput(new Cam2Renderer.errorOutput() {
            @Override
            public void out(String ex) {
                finish();
            }
        });
        cRenderer.setOnCaptureStillImageComplete(new Cam2Renderer.OnCaptureStillImageComplete() {
            @Override
            public void complete(final String path_data) {
                Toast.makeText(ShActivityNow.this, "File Image Save complete: " + path_data, Toast.LENGTH_LONG).show();
            }
        });
        return cRenderer;
    }

    @Override
    protected boolean onScreenTouch(MotionEvent ev, float x, float y) {
        cRenderer.setTouchPoint(x, y);
        StringBuilder b = new StringBuilder();
        b.append("X:");
        b.append(x);
        b.append("\n");
        b.append("Y:");
        b.append(y);
        hmx.setText(b.toString());
        return true;
    }

    /**
     * Takes a value, assumes it falls between start1 and stop1, and maps it to a value
     * between start2 and stop2.
     * <p/>
     * For example, above, our slide goes 0-100, starting at 50. We map 0 on the slider
     * to .1f and 100 to 1.9f, in order to better suit our shader calculations
     */
    float map(float value, float start1, float stop1, float start2, float stop2) {
        return start2 + (stop2 - start2) * ((value - start1) / (stop1 - start1));
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
        Log.d("seek", "now at val: " + i);
        // cRenderer.setTileAmount(map(i, 0.f, 100.f, 0.1f, 1.9f));
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {

    }
}
