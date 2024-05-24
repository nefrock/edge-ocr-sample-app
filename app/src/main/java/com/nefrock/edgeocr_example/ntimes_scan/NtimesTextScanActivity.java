package com.nefrock.edgeocr_example.ntimes_scan;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.Model;
import com.nefrock.edgeocr.Text;
import com.nefrock.edgeocr.ui.CameraOverlay;

import com.nefrock.edgeocr_example.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalCamera2Interop public class NtimesTextScanActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService focusExecutor = Executors.newSingleThreadExecutor();
    private PostCodeRegexTextAnalyzer imageAnalyzer;
    private CameraOverlay cameraOverlay;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private Camera camera;
    EdgeVisionAPI api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ntimes_text_scanner);
        // Initialize EdgeOCR
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            imageAnalyzer = new PostCodeRegexTextAnalyzer(api);
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }

        cameraOverlay = findViewById(R.id.camera_overlay);

        float modelAspectRatio = getIntent().getFloatExtra("model_aspect_ratio", 1.0f);
        cameraOverlay.setAspectRatio(modelAspectRatio);
        imageAnalyzer.setCallback((filteredDetections, notTargetDetection) -> {
            runOnUiThread(() -> cameraOverlay.setBoxes(filteredDetections));
            if (filteredDetections.size() == 0) {
                return;
            }
            // UIスレッドによるダイアログ表示前にスキャンを止める
            imageAnalyzer.stop();
            runOnUiThread(() -> showDialog(filteredDetections));
        });
        if (cameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{"android.permission.CAMERA"}, 10);
        }

        SeekBar zoomBar = findViewById(R.id.zoomBar);
        zoomBar.setProgress(50);
        zoomBar.setMax(100);
        zoomBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    //ツマミがドラッグされると呼ばれる
                    @Override
                    public void onProgressChanged(
                            SeekBar zoomBar, int progress, boolean fromUser) {
                        camera.getCameraControl().setLinearZoom(progress / 100.0f);
                    }

                    //ツマミがタッチされた時に呼ばれる
                    @Override
                    public void onStartTrackingTouch(SeekBar zoomBar) {
                    }

                    //ツマミがリリースされた時に呼ばれる
                    @Override
                    public void onStopTrackingTouch(SeekBar zoomBar) {
                    }
                });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
        focusExecutor.shutdownNow();
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

    @ExperimentalCamera2Interop
    @OptIn(markerClass = {ExperimentalCamera2Interop.class})
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
            imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyzer);

            ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder();

            imageCapture = imageCaptureBuilder
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(preview.getTargetRotation())
                    .setTargetResolution(targetResolution)
                    .build();

            // Aspect ratio is ignored since we use FIT
            Rational aspectRatio = new Rational(1, 1);
            ViewPort viewPort = new ViewPort.Builder(aspectRatio, preview.getTargetRotation())
                    .build();
            // Bind use cases to camera
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .addUseCase(imageCapture)
                    .setViewPort(viewPort)
                    .build();
            try {
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA,
                        useCaseGroup);
            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
    private void showDialog(List<Text> detections) {
        StringBuilder messageBuilder = new StringBuilder();
        for (Text detection : detections) {
            messageBuilder.append(detection.getText()).append("\n");
        }
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener(dialogInterface -> {
                    imageAnalyzer.resume();
                    api.resetScanningState();
                })
                .setPositiveButton("OK", null).create();
        WindowManager.LayoutParams lp = alertDialog.getWindow().getAttributes();
        lp.alpha = 0.9f;
        lp.gravity = Gravity.BOTTOM;
        alertDialog.show();
    }
}
