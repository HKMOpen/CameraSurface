package com.camerasurfacegr;

/**
 * Created by zJJ on 7/3/2016.
 */
public class AdvancePositionRawCam extends Camera2RawFragment {
    public static AdvancePositionRawCam newInstance() {
        return new AdvancePositionRawCam();
    }

    /**
     * warnings! most camera doesn't support front len camera with raw sensors
     *
     * @return bool
     */
    @Override
    protected boolean useFrontCamera() {
        return true;
    }
}
