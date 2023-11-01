package com.nefrock.edgeocr_example.report;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Model;
import com.nefrock.edgeocr.model.ModelInformation;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class ReportScannerActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private FreeStyleTextAnalyzer freeStyleTextAnalyzer;
    private ImageAnalysis imageAnalysis;

    private EdgeVisionAPI api = null;
    private ImageCapture imageCapture;
    private CameraOverlay cameraOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_report_text_scanner);
        // Initialize EdgeOCR
        Model model = null;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            freeStyleTextAnalyzer = new FreeStyleTextAnalyzer(api);
            for (Model candidate : api.availableModels()) {
                if (candidate.getUID().equals("model-large")) {
                    model = candidate;
                    break;
                }
            }
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }

        if (model == null || api == null) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR");
            return;
        }

        cameraOverlay = findViewById(R.id.camera_overlay);

        api.useModel(model, (ModelInformation modelInformation) -> {
            freeStyleTextAnalyzer.setCallback((allDetections) -> {
                runOnUiThread(() -> cameraOverlay.setBoxes(allDetections));
            });
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
        if (cameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{"android.permission.CAMERA"}, 10);
        }

        Button reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(
                (View v) -> {
                    freeStyleTextAnalyzer.stop();
                    api.resetScanningState();
                    // Capture the image
                    imageCapture.takePicture(
                            analysisExecutor,
                            new ImageCapture.OnImageCapturedCallback() {
                                @Override
                                public void onCaptureSuccess(@NonNull ImageProxy image) {
                                    try {
                                        api.reportImage(image, "test");
                                    } catch (Exception e) {
                                        Log.e("EdgeOCRExample", Log.getStackTraceString(e));
                                    } finally {
                                        image.close();
                                    }
                                    freeStyleTextAnalyzer.resume();
                                }

                                @Override
                                public void onError(@NonNull ImageCaptureException exception) {
                                    Log.e("EdgeOCRExample", "[onCaptureSuccess] Failed to capture image", exception);
                                    freeStyleTextAnalyzer.resume();
                                }
                            });
                });
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
            imageAnalysis.setAnalyzer(analysisExecutor, freeStyleTextAnalyzer);

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
                cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA,
                        useCaseGroup);

            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
