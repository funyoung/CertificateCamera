package win.smartown.android.library.certificateCamera;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraPreview {

    private static final String TAG = "CameraPreview";

    private final Context context;
    private final LifecycleOwner lifecycleOwner;
    private final PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private Camera camera;
    private int lensFacing = CameraSelector.LENS_FACING_BACK;

    public CameraPreview(Context context, LifecycleOwner lifecycleOwner, PreviewView previewView) {
        this.context = context;
        this.lifecycleOwner = lifecycleOwner;
        this.previewView = previewView;
    }

    public void startCamera() {
        ProcessCameraProvider.getInstance(context).addListener(new Runnable() {
            @Override
            public void run() {
                try {
                    cameraProvider = ProcessCameraProvider.getInstance(context).get();
                } catch (ExecutionException | InterruptedException e) {
                    Log.e(TAG, "Failed to get ProcessCameraProvider: " + e.getMessage());
                    return;
                }
                bindCameraUseCases();
            }
        }, ContextCompat.getMainExecutor(context));
    }

    private void bindCameraUseCases() {
        if (cameraProvider == null) {
            return;
        }
        cameraProvider.unbindAll();

        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        imageCapture = new ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                .build();

        CameraSelector cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(lensFacing)
                .build();

        try {
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture);
        } catch (Exception e) {
            Log.e(TAG, "Failed to bind camera use cases: " + e.getMessage());
        }
    }

    public void switchCamera() {
        if (cameraProvider == null) {
            return;
        }
        try {
            if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                    lensFacing = CameraSelector.LENS_FACING_FRONT;
                }
            } else {
                if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                    lensFacing = CameraSelector.LENS_FACING_BACK;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to check camera availability: " + e.getMessage());
            return;
        }
        bindCameraUseCases();
    }

    public boolean isFrontCamera() {
        return lensFacing == CameraSelector.LENS_FACING_FRONT;
    }

    public void takePhoto(final ImageCapture.OnImageCapturedCallback callback) {
        if (imageCapture != null) {
            imageCapture.takePicture(Executors.newSingleThreadExecutor(), callback);
        }
    }

    public boolean switchFlashLight() {
        if (camera != null) {
            Integer torchState = camera.getCameraInfo().getTorchState().getValue();
            if (torchState != null && torchState == TorchState.OFF) {
                camera.getCameraControl().enableTorch(true);
                return true;
            } else {
                camera.getCameraControl().enableTorch(false);
                return false;
            }
        }
        return false;
    }


    public void release() {
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }
}
