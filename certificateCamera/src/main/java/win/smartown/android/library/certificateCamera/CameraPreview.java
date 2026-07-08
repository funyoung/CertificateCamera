package win.smartown.android.library.certificateCamera;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.Camera;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.List;

public class CameraPreview extends SurfaceView implements SurfaceHolder.Callback {

    private static String TAG = CameraPreview.class.getName();

    private final Object cameraLock = new Object();
    private Camera camera;

    public CameraPreview(Context context) {
        super(context);
        init();
    }

    public CameraPreview(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public CameraPreview(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        SurfaceHolder surfaceHolder = getHolder();
        surfaceHolder.addCallback(this);
        surfaceHolder.setKeepScreenOn(true);
    }

    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (cameraLock) {
            camera = CameraUtils.openCamera();
            if (camera != null) {
                try {
                    camera.setPreviewDisplay(holder);
                    Camera.Parameters parameters = camera.getParameters();
                    setCameraOrientation(parameters);
                    Camera.Size bestPreviewSize = getBestSize(parameters.getSupportedPreviewSizes(), 16.0f / 9.0f);
                    if (bestPreviewSize != null) {
                        parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
                    } else {
                        bestPreviewSize = getBestSize(parameters.getSupportedPreviewSizes(), -1);
                        if (bestPreviewSize != null) {
                            parameters.setPreviewSize(bestPreviewSize.width, bestPreviewSize.height);
                        }
                    }
                    Camera.Size bestPictureSize = getBestSize(parameters.getSupportedPictureSizes(), 16.0f / 9.0f);
                    if (bestPictureSize == null) {
                        bestPictureSize = getBestSize(parameters.getSupportedPictureSizes(), -1);
                    }
                    if (bestPictureSize != null) {
                        parameters.setPictureSize(bestPictureSize.width, bestPictureSize.height);
                    }
                    List<String> focusModes = parameters.getSupportedFocusModes();
                    if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                    } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
                    } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                        parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                    }
                    if (parameters.getSupportedWhiteBalance() != null
                            && parameters.getSupportedWhiteBalance().contains(Camera.Parameters.WHITE_BALANCE_AUTO)) {
                        parameters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
                    }
                    if (parameters.getSupportedSceneModes() != null
                            && parameters.getSupportedSceneModes().contains(Camera.Parameters.SCENE_MODE_AUTO)) {
                        parameters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);
                    }
                    camera.setParameters(parameters);
                    camera.startPreview();
                    focus();
                } catch (Exception e) {
                    Log.d(TAG, "Error setting camera preview: " + e.getMessage());
                    try {
                        Camera.Parameters parameters = camera.getParameters();
                        setCameraOrientation(parameters);
                        List<String> focusModes = parameters.getSupportedFocusModes();
                        if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
                        } else if (focusModes != null && focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                            parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                        }
                        camera.setParameters(parameters);
                        camera.startPreview();
                        focus();
                    } catch (Exception e1) {
                        Log.e(TAG, "Fallback camera setup failed: " + e1.getMessage());
                        camera = null;
                    }
                }
            }
        }
    }

    private void setCameraOrientation(Camera.Parameters parameters) {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            camera.setDisplayOrientation(90);
            parameters.setRotation(90);
        } else {
            camera.setDisplayOrientation(0);
            parameters.setRotation(0);
        }
    }

    public void surfaceChanged(SurfaceHolder holder, int format, int w, int h) {
    }

    public void surfaceDestroyed(SurfaceHolder holder) {
        release();
    }

    private Camera.Size getBestSize(List<Camera.Size> sizes, float targetRatio) {
        if (sizes == null || sizes.isEmpty()) {
            return null;
        }
        Camera.Size bestSize = null;
        for (Camera.Size size : sizes) {
            if (targetRatio > 0) {
                if (Math.abs((float) size.width / (float) size.height - targetRatio) < 0.01f) {
                    if (bestSize == null || size.width > bestSize.width) {
                        bestSize = size;
                    }
                }
            } else {
                if (bestSize == null || size.width > bestSize.width) {
                    bestSize = size;
                }
            }
        }
        return bestSize;
    }

    private void release() {
        synchronized (cameraLock) {
            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }
        }
    }

    public void focus() {
        synchronized (cameraLock) {
            if (camera != null) {
                try {
                    Camera.Parameters parameters = camera.getParameters();
                    String focusMode = parameters.getFocusMode();
                    if (Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE.equals(focusMode)
                            || Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO.equals(focusMode)) {
                        return;
                    }
                    camera.autoFocus(new Camera.AutoFocusCallback() {
                        @Override
                        public void onAutoFocus(boolean success, Camera cam) {
                            if (!success && cam != null) {
                                try {
                                    cam.autoFocus(this);
                                } catch (Exception e) {
                                    Log.d(TAG, "AutoFocus retry failed: " + e.getMessage());
                                }
                            }
                        }
                    });
                } catch (Exception e) {
                    Log.d(TAG, "AutoFocus failed: " + e.getMessage());
                }
            }
        }
    }

    public boolean switchFlashLight() {
        synchronized (cameraLock) {
            if (camera != null) {
                Camera.Parameters parameters = camera.getParameters();
                if (Camera.Parameters.FLASH_MODE_OFF.equals(parameters.getFlashMode())) {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                    camera.setParameters(parameters);
                    return true;
                } else {
                    parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                    camera.setParameters(parameters);
                    return false;
                }
            }
            return false;
        }
    }

    public void takePhoto(Camera.PictureCallback pictureCallback) {
        synchronized (cameraLock) {
            if (camera != null) {
                camera.takePicture(null, null, pictureCallback);
            }
        }
    }

    public void startPreview() {
        synchronized (cameraLock) {
            if (camera != null) {
                camera.startPreview();
            }
        }
    }

}
