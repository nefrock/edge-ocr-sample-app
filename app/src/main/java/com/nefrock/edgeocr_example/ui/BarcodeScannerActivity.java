package com.nefrock.edgeocr_example.ui;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.Model;
import com.nefrock.edgeocr.model.ModelInformation;
import com.nefrock.edgeocr_example.R;
import com.nefrock.edgeocr_example.analysers.AnalyserWithCallback;
import com.nefrock.edgeocr_example.analysers.BarcodeAnalyser;

public class BarcodeScannerActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private AnalyserWithCallback imageAnalyser;
    private BoxesOverlay boxesOverlay;
    private ImageAnalysis imageAnalysis;
    private boolean overlayDrawn = false;
    //ダイアログを表示するか（表示中はスキャンを辞める）
    private boolean showDialog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_scanner);
        // Initialize EdgeOCR
        List<Model> models;
        EdgeVisionAPI api;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            showDialog = getIntent().getBooleanExtra("show_dialog", false);
            models = api.availableModels();
            imageAnalyser = new BarcodeAnalyser(api);
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }
        // Wait for overlay to be drawn
        findViewById(R.id.boxesOverlay).post(() -> overlayDrawn = true);

        // Load the first available model.
        api.useModel(models.get(0), (ModelInformation modelInformation) -> {
            while (!overlayDrawn) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Log.e("EdgeOCRExample", "[onCreate] Interrupted while waiting for overlay to be drawn", e);
                    return;
                }
            }
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
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
            // Acquire camera and set up preview
            ProcessCameraProvider cameraProvider;
            try {
                cameraProvider = cameraProviderFuture.get();
            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Lifecycle binding failed", e);
                return;
            }
            Preview preview = new Preview.Builder().build();
            PreviewView previewView = findViewById(R.id.previewView);
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            // Set up image analysis using EdgeOCR
            imageAnalysis = new ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build();
            boxesOverlay = findViewById(R.id.boxesOverlay);
            imageAnalyser.setCallback((filteredDetections, allDetections) -> runOnUiThread(() -> boxesOverlay.setBoxes(allDetections)));
            imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyser);

            imageAnalyser.setCallback((filteredDetections, allDetections) -> {
                runOnUiThread(() -> boxesOverlay.setBoxes(allDetections));
                if(!showDialog) {
                    return;
                }
                if (filteredDetections.size() == 0) {
                    return;
                }
                runOnUiThread(() -> showDialog(filteredDetections));

            });

            // Bind use cases to camera
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
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

    private void showDialog(List<Detection> detections) {
        imageAnalyser.stop();
        StringBuilder messageBuilder = new StringBuilder();
        for (Detection detection : detections) {
            messageBuilder.append(detection.getText()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener(dialogInterface -> imageAnalyser.resume())
                .setPositiveButton("OK", null)
                .show();
    }
}
