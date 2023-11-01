package com.nefrock.edgeocr_example.barcode;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.util.Pair;

import com.google.common.util.concurrent.ListenableFuture;
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.model.Barcode;
import com.nefrock.edgeocr.model.BarcodeFormat;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class BarcodeScannerActivity extends AppCompatActivity {
    EdgeVisionAPI api;
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private BarcodeAnalyzer barcodeAnalyzer;
    private CameraOverlay cameraOverlay;
    private ImageAnalysis imageAnalysis;
    //ダイアログを表示するか（表示中はスキャンを辞める）
    private boolean showDialog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanner);
        // Initialize EdgeOCR
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            api.setBarcodesNToConfirm(Collections.singletonList(new Pair<>(BarcodeFormat.QRCode, 5)));
            showDialog = getIntent().getBooleanExtra("show_dialog", false);
            barcodeAnalyzer = new BarcodeAnalyzer(api);
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }
        // Wait for overlay to be drawn
        cameraOverlay = findViewById(R.id.overlay);
        cameraOverlay.post(() -> cameraOverlay.setCrop(0f, 0f, 1f, 1f));

        if (cameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{"android.permission.CAMERA"}, 10);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 10) {
            if (cameraPermissionGranted()) {
                startCamera();
            } else {
                Toast.makeText(this,
                        "Permissions not granted by the user.",
                        Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }

    private boolean cameraPermissionGranted() {
        return ContextCompat.checkSelfPermission(
                this, "android.permission.CAMERA") == PackageManager.PERMISSION_GRANTED;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture
                = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            Size targetResolution = new Size(1080, 1080);
            // Acquire camera and set up preview
            ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Lifecycle binding failed", e);
                return;
            }
            Preview preview = new Preview.Builder()
                    .setTargetResolution(targetResolution)
                    .build();
            PreviewView previewView = findViewById(R.id.previewView);
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            // Set up image analysis using EdgeOCR
            imageAnalysis = new ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(targetResolution)
                    .build();
            cameraOverlay = findViewById(R.id.overlay);
            imageAnalysis.setAnalyzer(analysisExecutor, barcodeAnalyzer);

            barcodeAnalyzer.setCallback((allDetections) -> {
                runOnUiThread(() -> cameraOverlay.setBoxes(allDetections));
                if(!showDialog) {
                    return;
                }
                if (allDetections.size() == 0) {
                    return;
                }
                runOnUiThread(() -> showDialog(allDetections));
            });

            // Bind use cases to camera
            ViewPort viewPort = new ViewPort.Builder(new Rational(1, 1), preview.getTargetRotation())
                    .build();
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .setViewPort(viewPort)
                    .build();

            try {
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup);
            } catch(Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showDialog(List<Detection<Barcode>> detections) {
        barcodeAnalyzer.stop();
        StringBuilder messageBuilder = new StringBuilder();
        for (Detection<Barcode> detection : detections) {
            messageBuilder.append(detection.getScanObject().getText()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener((dialogInterface) -> {
                    barcodeAnalyzer.resume();
                    api.resetScanningState();
                })
                .setPositiveButton("OK", null)
                .show();
    }
}
