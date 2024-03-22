package com.nefrock.edgeocr_example;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.api.NefrockLicenseAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Model;
import com.nefrock.edgeocr.model.ModelSettings;
import com.nefrock.edgeocr_example.barcode.BarcodeScannerActivity;
import com.nefrock.edgeocr_example.barcode_bitmap.BarcodeBitmapActivity;
import com.nefrock.edgeocr_example.camera_overlay.CameraOverlayTextScannerActivity;
import com.nefrock.edgeocr_example.crop.CropTextScannerActivity;
import com.nefrock.edgeocr_example.detection_filter.DetectionFilterScannerActivity;
import com.nefrock.edgeocr_example.detection_filter.GetCenterDetectionFilter;
import com.nefrock.edgeocr_example.ntimes_scan.NtimesTextScanActivity;
import com.nefrock.edgeocr_example.ntimes_scan.PostCodeTextMapper;
import com.nefrock.edgeocr_example.report.ReportScannerActivity;
import com.nefrock.edgeocr_example.simple_text.SimpleTextScannerActivity;
import com.nefrock.edgeocr_example.text_bitmap.TextBitmapActivity;

@ExperimentalCamera2Interop public class MainActivity extends AppCompatActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.simple_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), SimpleTextScannerActivity.class);
            loadModelAndStartActivity(intent);
        });

        findViewById(R.id.camera_overlay_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), CameraOverlayTextScannerActivity.class);
            loadModelAndStartActivity(intent);
        });

        findViewById(R.id.report_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), ReportScannerActivity.class);
            loadModelAndStartActivity(intent);
        });

        findViewById(R.id.free_ocr_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), CropTextScannerActivity.class);
            loadModelAndStartActivity(intent);
        });

        findViewById(R.id.detection_filter_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), DetectionFilterScannerActivity.class);
            ModelSettings modelSettings = new ModelSettings();
            modelSettings.setDetectionFilter(new GetCenterDetectionFilter());
            loadModelAndStartActivity(intent, modelSettings);
        });

        findViewById(R.id.ntimes_scan_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), NtimesTextScanActivity.class);
            ModelSettings settings = new ModelSettings();
            settings.setNToConfirm(5);
            settings.setTextMapper(new PostCodeTextMapper());
            loadModelAndStartActivity(intent, settings);
        });

        findViewById(R.id.barcode_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeScannerActivity.class);
            intent.putExtra("show_dialog", true);
            startActivity(intent);
        });

        findViewById(R.id.text_bitmap_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), TextBitmapActivity.class);
            loadModelAndStartActivity(intent);
        });

        findViewById(R.id.barcode_bitmap_button).setOnClickListener(view -> {
            Intent intent = new Intent(getApplication(), BarcodeBitmapActivity.class);
            startActivity(intent);
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
    }

    private void loadModelAndStartActivity(Intent intent) {
        loadModelAndStartActivity(intent, new ModelSettings());
    }

    private void loadModelAndStartActivity(Intent intent, ModelSettings modelSettings) {
        runOnUiThread(()->findViewById(R.id.progressLayout).setVisibility(android.view.View.VISIBLE));
        EdgeVisionAPI api;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
        } catch (EdgeError e) {
            Toast.makeText(getApplicationContext(), e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }

        Model model = null;

        for (Model candidate : api.availableModels()) {
            if (candidate.getUID().equals("model-d320x320")) {
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
        }, edgeError -> Toast.makeText(getApplicationContext(), edgeError.getMessage(), Toast.LENGTH_LONG)
            .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        findViewById(R.id.progressLayout).setVisibility(android.view.View.GONE);
    }
}
