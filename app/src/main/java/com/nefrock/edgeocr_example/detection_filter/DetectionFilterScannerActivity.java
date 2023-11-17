package com.nefrock.edgeocr_example.detection_filter;

import android.content.pm.PackageManager;
import android.graphics.RectF;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
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

import com.google.common.util.concurrent.ListenableFuture;
import com.nefrock.edgeocr.api.DetectionFilter;
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.Model;
import com.nefrock.edgeocr.model.ModelInformation;
import com.nefrock.edgeocr.model.ScanObject;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.model.Text;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DetectionFilterScannerActivity extends AppCompatActivity {
    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private ImageAnalysis imageAnalysis;

    private EdgeVisionAPI api = null;
    private CameraOverlay cameraOverlay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_overlay_text_scanner);
        // Initialize EdgeOCR
        Model model = null;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
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
            cameraOverlay.setAspectRatio(modelInformation.getAspectRatio());
            api.setDetectionFilter(new GetCenterDetectionFilter());
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
        if (cameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{"android.permission.CAMERA"}, 10);
        }
    }

    static class GetCenterDetectionFilter extends DetectionFilter {
        @Override
        public List<Detection<? extends ScanObject>> filter(List<Detection<? extends ScanObject>> list) {
            Detection<? extends ScanObject> mostLikelyBox = null;
            double minDistance = Double.MAX_VALUE;
            for (Detection<? extends ScanObject> detection: list) {
                RectF box = detection.getBoundingBox();
                double distance = Math.pow(box.centerX()-0.5, 2) + Math.pow(box.centerY()-0.5, 2);
                if (distance < minDistance) {
                    minDistance = distance;
                    mostLikelyBox = detection;
                }
            }
            if ( mostLikelyBox != null) {
                return Collections.singletonList(mostLikelyBox);
            } else {
                return Collections.emptyList();
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        analysisExecutor.shutdown();
        api.clearDetectionFilter();
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
            imageAnalysis.setAnalyzer(analysisExecutor, image -> {
                if (!api.isReady()) {
                    image.close();
                    return;
                }
                try {
                    ScanResult scanResult = api.scanTexts(image);
                    List<Detection<Text>> detections = scanResult.getTextDetections();
                    cameraOverlay.setBoxes(detections);
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
                cameraProvider.bindToLifecycle(
                        this, CameraSelector.DEFAULT_BACK_CAMERA, useCaseGroup);
            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }
}
