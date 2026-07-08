package win.smartown.android.library.certificateCamera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.util.Log;

public class CameraUtils {

    private static final String TAG = "CameraUtils";

    public static boolean hasCamera(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA);
    }

    public static Camera openCamera() {
        try {
            return Camera.open();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to open camera: " + e.getMessage());
            return null;
        }
    }
}
