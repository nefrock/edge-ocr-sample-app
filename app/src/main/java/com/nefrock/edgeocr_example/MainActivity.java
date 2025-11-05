package com.nefrock.edgeocr_example;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.nefrock.edgeocr.BarcodeFormat;
import com.nefrock.edgeocr.EdgeError;
import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.Model;
import com.nefrock.edgeocr.ModelSettings;
import com.nefrock.edgeocr.NefrockLicenseAPI;

import com.nefrock.edgeocr_example.barcode.BarcodeScannerActivity;
import com.nefrock.edgeocr_example.barcode_bitmap.BarcodeBitmapActivity;
import com.nefrock.edgeocr_example.barcode_recognition_only_mode.BarcodeRecognitionOnlyModeActivity;
import com.nefrock.edgeocr_example.camera_overlay.CameraOverlayTextScannerActivity;
import com.nefrock.edgeocr_example.crop.CropTextScannerActivity;
import com.nefrock.edgeocr_example.detection_filter.DetectionFilterScannerActivity;
import com.nefrock.edgeocr_example.detection_filter.GetCenterDetectionFilter;
import com.nefrock.edgeocr_example.ntimes_scan.NtimesTextScanActivity;
import com.nefrock.edgeocr_example.ntimes_scan.PostCodeTextMapper;
import com.nefrock.edgeocr_example.simple_text.SimpleTextScannerActivity;
import com.nefrock.edgeocr_example.text_bitmap.TextBitmapActivity;

import java.util.Collections;

@ExperimentalCamera2Interop public class MainActivity extends AppCompatActivity {
    void addMarginsForEdgeToEdge() {
        View view = this.getWindow().getDecorView().findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.simple_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), SimpleTextScannerActivity.class);
            loadModelAndStartActivity(intent, "model-d320x320");
        });

        findViewById(R.id.camera_overlay_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), CameraOverlayTextScannerActivity.class);
            loadModelAndStartActivity(intent, "model-d320x320_with_barcode");
        });

        findViewById(R.id.free_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), CropTextScannerActivity.class);
            loadModelAndStartActivity(intent, "model-d320x320");
        });

        findViewById(R.id.detection_filter_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), DetectionFilterScannerActivity.class);
            ModelSettings modelSettings = new ModelSettings();
            modelSettings.setDetectionFilter(new GetCenterDetectionFilter());
            loadModelAndStartActivity(intent, "model-d320x320", modelSettings);
        });

        findViewById(R.id.ntimes_scan_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), NtimesTextScanActivity.class);
            ModelSettings settings = new ModelSettings();
            settings.setTextNToConfirm(5);
            settings.setTextMapper(new PostCodeTextMapper());
            loadModelAndStartActivity(intent, "model-d320x320", settings);
        });

        findViewById(R.id.barcode_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeScannerActivity.class);
            intent.putExtra("show_dialog", true);
            ModelSettings modelSettings = new ModelSettings();
            modelSettings.setBarcodeNToConfirm(Collections.singletonMap(BarcodeFormat.QRCode, 5));
            loadModelAndStartActivity(intent, "edgeocr_barcode_default", modelSettings);
        });

        findViewById(R.id.barcode_dpm_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeRecognitionOnlyModeActivity.class);
            intent.putExtra("show_dialog", true);
            ModelSettings modelSettings = new ModelSettings();
            modelSettings.setBarcodeNToConfirm(Collections.singletonMap(BarcodeFormat.Any, 1));
            loadModelAndStartActivity(intent, "barcode_dpm", modelSettings);
        });

        findViewById(R.id.text_bitmap_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), TextBitmapActivity.class);
            loadModelAndStartActivity(intent, "model-d320x320");
        });

        findViewById(R.id.barcode_bitmap_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeBitmapActivity.class);
            ModelSettings modelSettings = new ModelSettings();
            modelSettings.setBarcodeNToConfirm(Collections.singletonMap(BarcodeFormat.Any, 1));
            loadModelAndStartActivity(intent, "edgeocr_barcode_default", modelSettings);
        });

        // TODO:ライセンスキー部分のコメントアウト
        NefrockLicenseAPI licenseAPI;
        try {
            licenseAPI = new NefrockLicenseAPI.Builder(this)
                .withLicenseKey(<your key>)
                .build();
        } catch (EdgeError edgeError) {
            Toast.makeText(getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG)
                .show();
            return;
        }

        Button activationButton = findViewById(R.id.activation_button);
        activationButton.setText("アクティベーション状態を確認");
        activationButton.setEnabled(false);

        licenseAPI.isActivated(
            license -> new Handler(
                    Looper.getMainLooper()).post(() -> activationButton.setText("アクティベート済み")),
            edgeError -> new Handler(Looper.getMainLooper()).post(() -> {
                activationButton.setText("アクティベート");
                activationButton.setEnabled(true);
            })
        );
        activationButton.setOnClickListener(view -> licenseAPI.activate(
            license -> {
                // Make Toast in Main Thread
                new Handler(Looper.getMainLooper()).post(() -> {
                    activationButton.setText("アクティベート済み");
                    activationButton.setEnabled(false);
                    Toast.makeText(
                        getApplicationContext(), "アクティベーション完了", Toast.LENGTH_LONG).show();
                });
            },
            edgeError -> {
                // Make Toast in Main Thread
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                    getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG).show());
            }
        ));
        addMarginsForEdgeToEdge();
    }

    private void loadModelAndStartActivity(Intent intent, String modelUid) {
        loadModelAndStartActivity(intent, modelUid, new ModelSettings());
    }

    private void loadModelAndStartActivity(Intent intent, String modelUid, ModelSettings modelSettings) {
        runOnUiThread(()->findViewById(R.id.progressLayout).setVisibility(android.view.View.VISIBLE));
        EdgeVisionAPI api;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
        } catch (EdgeError e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Model model = null;

        for (Model candidate : api.availableModelsWithExperimental()) {
            if (candidate.getUID().equals(modelUid)) {
                model = candidate;
                break;
            }
        }
        if (model == null) {
            Toast.makeText(getApplicationContext(), "モデルが見つかりません", Toast.LENGTH_LONG).show();
            return;
        }
        api.useModel(model, modelSettings, modelInformation -> {
            intent.putExtra("model_aspect_ratio", modelInformation.getAspectRatio());
            startActivity(intent);
        }, edgeError -> {
            new Handler(Looper.getMainLooper()).post(() -> {
                Toast.makeText(getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG).show();
            });
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.progressLayout).setVisibility(android.view.View.GONE);
    }
}
