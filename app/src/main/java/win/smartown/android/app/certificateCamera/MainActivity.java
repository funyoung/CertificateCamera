package win.smartown.android.app.certificateCamera;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;

import win.smartown.android.library.certificateCamera.CameraActivity;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private int pendingType;

    private final ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if (result.getResultCode() == CameraActivity.RESULT_CODE && result.getData() != null) {
                        String path = CameraActivity.getResult(result.getData());
                        if (!TextUtils.isEmpty(path)) {
                            imageView.setImageBitmap(BitmapFactory.decodeFile(path));
                        }
                    }
                }
            }
    );

    private final ActivityResultLauncher<String> permissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestPermission(),
            new ActivityResultCallback<Boolean>() {
                @Override
                public void onActivityResult(Boolean granted) {
                    if (granted) {
                        CameraActivity.openCertificateCamera(MainActivity.this, pendingType, cameraLauncher);
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        imageView = (ImageView) findViewById(R.id.main_image);
    }

    private void takePhoto(int type) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingType = type;
            permissionLauncher.launch(Manifest.permission.CAMERA);
            return;
        }
        CameraActivity.openCertificateCamera(this, type, cameraLauncher);
    }

    public void frontIdCard(View view) {
        takePhoto(CameraActivity.TYPE_IDCARD_FRONT);
    }

    public void backIdCard(View view) {
        takePhoto(CameraActivity.TYPE_IDCARD_BACK);
    }

    public void businessLicensePortrait(View view) {
        takePhoto(CameraActivity.TYPE_COMPANY_PORTRAIT);
    }

    public void businessLicenseLandscape(View view) {
        takePhoto(CameraActivity.TYPE_COMPANY_LANDSCAPE);
    }

}
