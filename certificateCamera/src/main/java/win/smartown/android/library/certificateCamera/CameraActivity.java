package win.smartown.android.library.certificateCamera;

import android.Manifest;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.MediaActionSound;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;

public class CameraActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "CameraActivity";

    public interface OnCameraErrorListener {
        void onCameraError(String message);
    }

    private static WeakReference<OnCameraErrorListener> errorListenerRef;

    public final static int TYPE_IDCARD_FRONT = 1;
    public final static int TYPE_IDCARD_BACK = 2;
    public final static int TYPE_COMPANY_PORTRAIT = 3;
    public final static int TYPE_COMPANY_LANDSCAPE = 4;

    public final static int REQUEST_CODE = 0X13;
    public final static int RESULT_CODE = 0X14;

    private final static String EXTRA_TYPE = "type";
    private final static String EXTRA_RESULT = "result";

    private static final float RATIO_16_9 = 16.0f / 9.0f;
    private static final float CROP_RATIO_COMPANY = 43.0f / 30.0f;
    private static final float CROP_RATIO_IDCARD = 75.0f / 47.0f;
    private static final float CROP_SCALE_COMPANY = 0.8f;
    private static final float CROP_SCALE_IDCARD = 0.75f;

    public static void openCertificateCamera(android.app.Activity activity, int type) {
        openCertificateCamera(activity, type, (OnCameraErrorListener) null);
    }

    public static void openCertificateCamera(android.app.Activity activity, int type, OnCameraErrorListener listener) {
        errorListenerRef = listener != null ? new WeakReference<>(listener) : null;
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        activity.startActivityForResult(intent, REQUEST_CODE);
    }

    public static void openCertificateCamera(android.app.Activity activity, int type, ActivityResultLauncher<Intent> launcher) {
        openCertificateCamera(activity, type, launcher, null);
    }

    public static void openCertificateCamera(android.app.Activity activity, int type, ActivityResultLauncher<Intent> launcher, OnCameraErrorListener listener) {
        errorListenerRef = listener != null ? new WeakReference<>(listener) : null;
        Intent intent = new Intent(activity, CameraActivity.class);
        intent.putExtra(EXTRA_TYPE, type);
        launcher.launch(intent);
    }

    public static String getResult(Intent data) {
        if (data != null) {
            return data.getStringExtra(EXTRA_RESULT);
        }
        return "";
    }

    private static final int PERMISSION_REQUEST_CODE = 0X15;

    private CameraPreview cameraPreview;
    private PreviewView previewView;
    private View containerView;
    private ImageView cropView;
    private ImageView flashImageView;
    private ImageView switchImageView;
    private View optionView;
    private View resultView;
    private View processingView;
    private ImageView resultPreviewView;

    private int type;
    private String cropFilePath;
    private MediaActionSound mediaActionSound;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, PERMISSION_REQUEST_CODE);
            return;
        }
        initCameraView();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initCameraView();
            } else {
                notifyError("相机权限被拒绝，无法使用拍照功能");
                finish();
            }
        }
    }

    private void initCameraView() {
        type = getIntent().getIntExtra(EXTRA_TYPE, 0);
        if (type != TYPE_IDCARD_FRONT && type != TYPE_IDCARD_BACK
                && type != TYPE_COMPANY_PORTRAIT && type != TYPE_COMPANY_LANDSCAPE) {
            finish();
            return;
        }
        if (type == TYPE_COMPANY_PORTRAIT) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        }
        setContentView(R.layout.activity_camera);

        previewView = (PreviewView) findViewById(R.id.camera_surface);
        cameraPreview = new CameraPreview(this, this, previewView);
        cameraPreview.startCamera();

        float screenMinSize = Math.min(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        float maxSize = screenMinSize * RATIO_16_9;
        RelativeLayout.LayoutParams layoutParams;
        if (type == TYPE_COMPANY_PORTRAIT) {
            layoutParams = new RelativeLayout.LayoutParams((int) screenMinSize, (int) maxSize);
        } else {
            layoutParams = new RelativeLayout.LayoutParams((int) maxSize, (int) screenMinSize);
        }
        layoutParams.addRule(RelativeLayout.CENTER_IN_PARENT);
        previewView.setLayoutParams(layoutParams);

        containerView = findViewById(R.id.camera_crop_container);
        cropView = (ImageView) findViewById(R.id.camera_crop);
        resultPreviewView = (ImageView) findViewById(R.id.camera_result_preview);
        if (type == TYPE_COMPANY_PORTRAIT) {
            float width = (int) (screenMinSize * CROP_SCALE_COMPANY);
            float height = (int) (width * CROP_RATIO_COMPANY);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) height);
            LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams((int) width, (int) height);
            containerView.setLayoutParams(containerParams);
            cropView.setLayoutParams(cropParams);
            resultPreviewView.setLayoutParams(cropParams);
        } else if (type == TYPE_COMPANY_LANDSCAPE) {
            float height = (int) (screenMinSize * CROP_SCALE_COMPANY);
            float width = (int) (height * CROP_RATIO_COMPANY);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams((int) width, ViewGroup.LayoutParams.MATCH_PARENT);
            LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams((int) width, (int) height);
            containerView.setLayoutParams(containerParams);
            cropView.setLayoutParams(cropParams);
            resultPreviewView.setLayoutParams(cropParams);
        } else {
            float height = (int) (screenMinSize * CROP_SCALE_IDCARD);
            float width = (int) (height * CROP_RATIO_IDCARD);
            LinearLayout.LayoutParams containerParams = new LinearLayout.LayoutParams((int) width, ViewGroup.LayoutParams.MATCH_PARENT);
            LinearLayout.LayoutParams cropParams = new LinearLayout.LayoutParams((int) width, (int) height);
            containerView.setLayoutParams(containerParams);
            cropView.setLayoutParams(cropParams);
            resultPreviewView.setLayoutParams(cropParams);
        }
        switch (type) {
            case TYPE_IDCARD_FRONT:
                cropView.setImageResource(R.mipmap.camera_idcard_front);
                break;
            case TYPE_IDCARD_BACK:
                cropView.setImageResource(R.mipmap.camera_idcard_back);
                break;
            case TYPE_COMPANY_PORTRAIT:
                cropView.setImageResource(R.mipmap.camera_company);
                break;
            case TYPE_COMPANY_LANDSCAPE:
                cropView.setImageResource(R.mipmap.camera_company_landscape);
                break;
        }

        flashImageView = (ImageView) findViewById(R.id.camera_flash);
        switchImageView = (ImageView) findViewById(R.id.camera_switch);
        optionView = findViewById(R.id.camera_option);
        resultView = findViewById(R.id.camera_result);
        processingView = findViewById(R.id.camera_processing);

        previewView.setOnClickListener(this);
        findViewById(R.id.camera_close).setOnClickListener(this);
        findViewById(R.id.camera_take).setOnClickListener(this);
        flashImageView.setOnClickListener(this);
        if (switchImageView != null) {
            switchImageView.setOnClickListener(this);
        }
        findViewById(R.id.camera_result_ok).setOnClickListener(this);
        findViewById(R.id.camera_result_cancel).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (id == R.id.camera_surface) {
            return;
        } else if (id == R.id.camera_close) {
            finish();
        } else if (id == R.id.camera_take) {
            takePhoto();
        } else if (id == R.id.camera_flash) {
            boolean isFlashOn = cameraPreview.switchFlashLight();
            flashImageView.setImageResource(isFlashOn ? R.mipmap.camera_flash_on : R.mipmap.camera_flash_off);
        } else if (id == R.id.camera_switch) {
            cameraPreview.switchCamera();
        } else if (id == R.id.camera_result_ok) {
            goBack();
        } else if (id == R.id.camera_result_cancel) {
            optionView.setVisibility(View.VISIBLE);
            resultView.setVisibility(View.GONE);
            resultPreviewView.setVisibility(View.GONE);
            resultPreviewView.setImageBitmap(null);
            cropView.setVisibility(View.VISIBLE);
        }
    }

    private void takePhoto() {
        optionView.setVisibility(View.GONE);
        processingView.setVisibility(View.VISIBLE);
        playShutterSound();

        final float cropLeft = cropView.getLeft();
        final float cropTop = cropView.getTop();
        final float cropRight = cropView.getRight();
        final float cropBottom = cropView.getBottom();
        final float containerLeft = containerView.getLeft();
        final float containerTop = containerView.getTop();
        final float containerRight = containerView.getRight();
        final float containerBottom = containerView.getBottom();
        final int previewWidth = previewView.getWidth();
        final int previewHeight = previewView.getHeight();

        cameraPreview.takePhoto(new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy image) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            byte[] data = imageProxyToBytes(image);
                            image.close();

                            File originalFile = getOriginalFile();
                            try (FileOutputStream fos = new FileOutputStream(originalFile)) {
                                fos.write(data);
                            }

                            int rotation = getExpectedRotation();

                            BitmapFactory.Options options = new BitmapFactory.Options();
                            options.inJustDecodeBounds = true;
                            BitmapFactory.decodeFile(originalFile.getPath(), options);

                            int reqWidth = previewView.getWidth();
                            int reqHeight = previewView.getHeight();

                            boolean isRotated90or270 = (rotation == 90 || rotation == 270);
                            int srcWidth = isRotated90or270 ? options.outHeight : options.outWidth;
                            int srcHeight = isRotated90or270 ? options.outWidth : options.outHeight;

                            options.inSampleSize = calculateInSampleSize(srcWidth, srcHeight, reqWidth, reqHeight);
                            options.inJustDecodeBounds = false;
                            options.inPreferredConfig = Bitmap.Config.RGB_565;

                            Bitmap bitmap = BitmapFactory.decodeFile(originalFile.getPath(), options);
                            if (bitmap == null) {
                                notifyError("图片解码失败");
                                return;
                            }

                            if (rotation != 0) {
                                Matrix matrix = new Matrix();
                                matrix.postRotate(rotation);
                                Bitmap rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                                if (rotated != bitmap) {
                                    bitmap.recycle();
                                }
                                bitmap = rotated;
                            }

                            float left, top, right, bottom;
                            if (type == TYPE_COMPANY_PORTRAIT) {
                                left = cropLeft / (float) previewWidth;
                                top = (containerTop - (float) previewView.getTop()) / (float) previewHeight;
                                right = cropRight / (float) previewWidth;
                                bottom = containerBottom / (float) previewHeight;
                            } else {
                                left = (containerLeft - (float) previewView.getLeft()) / (float) previewWidth;
                                top = cropTop / (float) previewHeight;
                                right = containerRight / (float) previewWidth;
                                bottom = cropBottom / (float) previewHeight;
                            }

                            left = Math.max(0f, Math.min(left, 1f));
                            top = Math.max(0f, Math.min(top, 1f));
                            right = Math.max(left, Math.min(right, 1f));
                            bottom = Math.max(top, Math.min(bottom, 1f));

                            int cropX = (int) (left * bitmap.getWidth());
                            int cropY = (int) (top * bitmap.getHeight());
                            int cropWidth = (int) ((right - left) * bitmap.getWidth());
                            int cropHeight = (int) ((bottom - top) * bitmap.getHeight());

                            cropWidth = Math.min(cropWidth, bitmap.getWidth() - cropX);
                            cropHeight = Math.min(cropHeight, bitmap.getHeight() - cropY);

                            if (cropWidth <= 0 || cropHeight <= 0) {
                                bitmap.recycle();
                                notifyError("裁剪区域无效");
                                return;
                            }

                            Bitmap cropBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropWidth, cropHeight);
                            bitmap.recycle();

                            final File cropFile = getCropFile();
                            try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(cropFile))) {
                                cropBitmap.compress(Bitmap.CompressFormat.JPEG, getResources().getInteger(R.integer.crop_jpeg_quality), bos);
                                bos.flush();
                            }
                            originalFile.delete();
                            cropFilePath = cropFile.getPath();
                            final Bitmap previewBitmap = cropBitmap;
                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    cropView.setVisibility(View.GONE);
                                    resultPreviewView.setVisibility(View.VISIBLE);
                                    resultPreviewView.setImageBitmap(previewBitmap);
                                    processingView.setVisibility(View.GONE);
                                    resultView.setVisibility(View.VISIBLE);
                                }
                            });

                        } catch (IOException e) {
                            Log.e(TAG, "IO error during photo processing", e);
                            notifyError("IO错误: " + e.getMessage());
                        } catch (Exception e) {
                            Log.e(TAG, "Unexpected error during photo processing", e);
                            notifyError("处理照片时出错: " + e.getMessage());
                        }
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                processingView.setVisibility(View.GONE);
                                optionView.setVisibility(View.VISIBLE);
                            }
                        });
                    }
                }).start();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e(TAG, "Photo capture failed: " + exception.getMessage());
                notifyError("拍照失败: " + exception.getMessage());
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        processingView.setVisibility(View.GONE);
                        optionView.setVisibility(View.VISIBLE);
                    }
                });
            }
        });
    }

    private byte[] imageProxyToBytes(ImageProxy image) {
        ImageProxy.PlaneProxy[] planes = image.getPlanes();
        ByteBuffer yBuffer = planes[0].getBuffer();
        ByteBuffer uBuffer = planes[1].getBuffer();
        ByteBuffer vBuffer = planes[2].getBuffer();
        int ySize = yBuffer.remaining();
        int uSize = uBuffer.remaining();
        int vSize = vBuffer.remaining();
        byte[] nv21 = new byte[ySize + uSize + vSize];
        yBuffer.get(nv21, 0, ySize);
        vBuffer.get(nv21, ySize, vSize);
        uBuffer.get(nv21, ySize + vSize, uSize);
        return nv21;
    }

    private static int calculateInSampleSize(int srcWidth, int srcHeight, int reqWidth, int reqHeight) {
        int inSampleSize = 1;
        if (srcHeight > reqHeight || srcWidth > reqWidth) {
            final int halfHeight = srcHeight / 2;
            final int halfWidth = srcWidth / 2;
            while ((halfHeight / inSampleSize) >= reqHeight
                    && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2;
            }
        }
        return inSampleSize;
    }

    private int getExpectedRotation() {
        if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT) {
            return 90;
        }
        return 0;
    }

    private File getImageCacheDir() {
        File dir = getCacheDir();
        new Thread(new Runnable() {
            @Override
            public void run() {
                cleanCacheDir(dir);
            }
        }).start();
        return dir;
    }

    private void cleanCacheDir(File dir) {
        if (dir == null || !dir.exists()) {
            return;
        }
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        long now = System.currentTimeMillis();
        long maxAge = 24 * 60 * 60 * 1000L;
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".jpg")) {
                if (now - file.lastModified() > maxAge) {
                    file.delete();
                }
            }
        }
    }

    private String getTypePrefix() {
        switch (type) {
            case TYPE_IDCARD_FRONT:
                return "idCardFront";
            case TYPE_IDCARD_BACK:
                return "idCardBack";
            case TYPE_COMPANY_PORTRAIT:
            case TYPE_COMPANY_LANDSCAPE:
                return "companyInfo";
            default:
                return "picture";
        }
    }

    private File getOriginalFile() {
        return new File(getImageCacheDir(), getTypePrefix() + "_" + System.currentTimeMillis() + ".jpg");
    }

    private File getCropFile() {
        return new File(getImageCacheDir(), getTypePrefix() + "Crop_" + System.currentTimeMillis() + ".jpg");
    }

    private void notifyError(final String message) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (errorListenerRef != null) {
                    OnCameraErrorListener listener = errorListenerRef.get();
                    if (listener != null) {
                        listener.onCameraError(message);
                    }
                }
            }
        });
    }

    private void playShutterSound() {
        try {
            if (mediaActionSound == null) {
                mediaActionSound = new MediaActionSound();
            }
            mediaActionSound.play(MediaActionSound.SHUTTER_CLICK);
        } catch (Exception e) {
            Log.d(TAG, "Shutter sound failed: " + e.getMessage());
        }
    }

    private void goBack() {
        Intent intent = new Intent();
        intent.putExtra(EXTRA_RESULT, cropFilePath);
        setResult(RESULT_CODE, intent);
        finish();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraPreview != null) {
            cameraPreview.release();
        }
        if (mediaActionSound != null) {
            mediaActionSound.release();
            mediaActionSound = null;
        }
    }
}
