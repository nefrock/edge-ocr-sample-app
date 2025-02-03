package com.nefrock.edgeocr_example.barcode_recognition_only_mode;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2CameraControl;
import androidx.camera.camera2.interop.CaptureRequestOptions;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.core.TorchState;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import com.nefrock.edgeocr.Barcode;
import com.nefrock.edgeocr.CropRect;
import com.nefrock.edgeocr.EdgeError;
import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.ScanOptions;
import com.nefrock.edgeocr.ScanResult;
import com.nefrock.edgeocr.ui.CameraOverlay;

import com.nefrock.edgeocr_example.R;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class BarcodeRecognitionOnlyModeActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private ImageAnalysis imageAnalysis;

    private EdgeVisionAPI api = null;
    private CameraOverlay cameraOverlay;
    private SeekBar zoomBar;
    private Camera camera;
    private boolean runAnalysis = true;
    private boolean showDialog = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_barcode_recognition_only);
        // Initialize EdgeOCR
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            showDialog = getIntent().getBooleanExtra("show_dialog", false);
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }
        float modelAspectRatio = getIntent().getFloatExtra("model_aspect_ratio", 1.0f);
        cameraOverlay = findViewById(R.id.camera_overlay);
        // cameraOverlay.setAspectRatio(modelAspectRatio);
        cameraOverlay.setCrop(0.5f, 0.5f, 0.20f, 0.20f);

        zoomBar = findViewById(R.id.zoom_bar);
        zoomBar.setMax(100);
        zoomBar.setProgress(70);
        zoomBar.setOnSeekBarChangeListener(
            new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(
                        SeekBar zoomBar, int progress, boolean fromUser) {
                    if (camera == null) return;
                    camera.getCameraControl().setLinearZoom(progress / 100.0f);
                }
                @Override
                public void onStartTrackingTouch(SeekBar zoomBar) {}
                @Override
                public void onStopTrackingTouch(SeekBar zoomBar) {}
            });

        Button torchButton = findViewById(R.id.torch_button);
        torchButton.setOnClickListener(v -> {
            if (camera == null) return;
            boolean torchEnabled = camera.getCameraInfo().getTorchState().getValue() == TorchState.ON;
            camera.getCameraControl().enableTorch(!torchEnabled);
        });

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
            Preview preview = new Preview.Builder().build();
            PreviewView previewView = findViewById(R.id.previewView);
            preview.setSurfaceProvider(previewView.getSurfaceProvider());
            // Set up image analysis using EdgeOCR
            imageAnalysis = new ImageAnalysis.Builder()
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setTargetResolution(targetResolution)
                    .build();
            imageAnalysis.setAnalyzer(analysisExecutor, image -> {
                if (!api.isReady() || !runAnalysis) {
                    image.close();
                    return;
                }
                try {
                    ScanOptions scanOptions = new ScanOptions();
                    scanOptions.setCropRect(new CropRect(0.5f, 0.5f, 0.20f, 0.20f));
                    scanOptions.setScanMode(ScanOptions.ScanMode.RECOGNITION_ONLY);
                    ScanResult scanResult = api.scan(image, scanOptions);
                    List<Barcode> detections = scanResult.getBarcodeDetections();
                    cameraOverlay.setBoxes(detections);
                    List<Barcode> nonEmpty = new java.util.ArrayList<>();
                    for (Barcode detection : detections) {
                        if (detection.getText().length() > 0) {
                            nonEmpty.add(detection);
                        }
                    }
                    if (showDialog && nonEmpty.size() > 0) {
                        runOnUiThread(() -> showDialog(detections));
                    }
                } catch (EdgeError e) {
                    Log.e("EdgeOCRExample", "[startCamera] Failed to analyze image", e);
                } finally {
                    image.close();
                }
            });
            // Aspect ratio is ignored since we use FIT
            Rational aspectRatio = new Rational(1, 1);
            ViewPort viewPort = new ViewPort.Builder(aspectRatio, preview.getTargetRotation())
                    .build();
            // Bind use cases to camera
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .setViewPort(viewPort)
                    .build();
            try {
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup);
                Camera2CameraControl camera2CameraControl = Camera2CameraControl.from(camera.getCameraControl());
                CaptureRequestOptions captureRequestOptions = new CaptureRequestOptions.Builder()
                            .setCaptureRequestOption(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_OFF)
                            .build();
                camera2CameraControl.setCaptureRequestOptions(captureRequestOptions);
                camera.getCameraControl().setLinearZoom(0.7f);
            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void showDialog(List<Barcode> detections) {
        runAnalysis = false;
        StringBuilder messageBuilder = new StringBuilder();
        for (Barcode detection : detections) {
            messageBuilder.append(detection.getText()).append("\n");
        }
        new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener((dialogInterface) -> {
                    runAnalysis = true;
                    api.resetScanningState();
                })
                .setPositiveButton("OK", null)
                .show();
    }
}
