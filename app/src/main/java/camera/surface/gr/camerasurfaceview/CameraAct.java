package camera.surface.gr.camerasurfaceview;

/**
 * Created by zJJ on 7/2/2016.
 */

import android.annotation.TargetApi;
import android.app.Activity;
import android.os.Build;
import android.os.Bundle;

import com.camerasurfacegr.AdvancePositionCam;

public class CameraAct extends Activity {

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if (null == savedInstanceState) {
            getFragmentManager().beginTransaction()
                    .replace(R.id.container, AdvancePositionCam.newInstance())
                    .commit();
        }
    }

}