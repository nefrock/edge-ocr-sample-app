package com.nefrock.edgeocr_example.crop;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.View;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraControl;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.common.util.concurrent.ListenableFuture;

import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.ui.CameraOverlay;

import com.nefrock.edgeocr_example.R;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@ExperimentalCamera2Interop public class CropTextScannerActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService focusExecutor = Executors.newSingleThreadExecutor();
    private CropFreeStyleTextAnalyzer cropFreeStyleTextAnalyzer;
    private CameraOverlay cameraOverlay;
    private SeekBar hbCropBar, vbCropBar, hsCropBar, vsCropBar;
    private ImageAnalysis imageAnalysis;
    private Camera camera;
    private SurfaceOrientedMeteringPointFactory meteringPointFactory;
    //ダイアログを表示するか（表示中はスキャンを辞める）
    EdgeVisionAPI api;

    void addMarginsForEdgeToEdge() {
        View view = this.getWindow().getDecorView().findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_range_text_scanner);
        // Initialize EdgeOCR
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            cropFreeStyleTextAnalyzer = new CropFreeStyleTextAnalyzer(api);
        } catch (Exception e) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR", e);
            return;
        }

        hbCropBar = findViewById(R.id.hb_crop_bar);
        vbCropBar = findViewById(R.id.vb_crop_bar);
        hsCropBar = findViewById(R.id.hs_crop_bar);
        vsCropBar = findViewById(R.id.vs_crop_bar);
        // Loop over seekbars and set their initial values
        for (SeekBar seekBar : new SeekBar[] {hbCropBar, vbCropBar, hsCropBar, vsCropBar}) {
            seekBar.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(
                                SeekBar seekBar, int progress, boolean fromUser) {
                            if (fromUser) adaptOverlayWeights();
                        }
                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}
                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });
            seekBar.setMax(100);
        }
        hbCropBar.setProgress(50);
        vbCropBar.setProgress(50);

        cameraOverlay = findViewById(R.id.overlay);

        float aspectRatio = getIntent().getFloatExtra("model_aspect_ratio", 1.0f);
        hsCropBar.setProgress((int) (100 * Math.min(1.0f, aspectRatio)));
        vsCropBar.setProgress((int) (100 * Math.min(1.0f, 1.0f / aspectRatio)));
        adaptOverlayWeights();
        cropFreeStyleTextAnalyzer.setCallback((allDetections) -> {
            runOnUiThread(() -> cameraOverlay.setBoxes(allDetections));
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
            }
        );
        addMarginsForEdgeToEdge();
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
            imageAnalysis.setAnalyzer(analysisExecutor, cropFreeStyleTextAnalyzer);

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
                        this, CameraSelector.DEFAULT_BACK_CAMERA,
                        useCaseGroup);
                meteringPointFactory = new SurfaceOrientedMeteringPointFactory(1.0f, 1.0f, imageAnalysis);
                // Focus on center of model input once every second
                focusExecutor.execute(() -> {
                    ListenableFuture<FocusMeteringResult> future = null;
                    while(!Thread.currentThread().isInterrupted()) {
                        try {
                            if (future != null) {
                                try {
                                    FocusMeteringResult result = future.get();
                                    if (!result.isFocusSuccessful()) {
                                        Log.d("EdgeOCRExample", "[customAutoFocus] failed: " + result);
                                    }
                                } catch (ExecutionException e) {
                                    if (e.getCause() instanceof CameraControl.OperationCanceledException) {
                                        // Do nothing, if there is no AF available on the device.
                                    } else {
                                        Log.e("EdgeOCRExample", "[customAutoFocus] Failed to get focus result", e);
                                    }
                                }
                            }
                            MeteringPoint point = buildMeteringPoint();
                            if (point != null) {
                                FocusMeteringAction action =
                                    new FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                                        .setAutoCancelDuration(1, TimeUnit.SECONDS)
                                        .build();
                                future = camera.getCameraControl().startFocusAndMetering(action);
                            }
                            Thread.sleep(1000);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("EdgeOCRExample", "[startCamera] Use case binding failed", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private static class OverlayParams {
        float widthPercent;
        float heightPercent;
        float horizontalBias;
        float verticalBias;

        OverlayParams(
                float widthPercent, float heightPercent, float horizontalBias, float verticalBias) {
            this.widthPercent = widthPercent;
            this.heightPercent = heightPercent;
            this.horizontalBias = horizontalBias;
            this.verticalBias = verticalBias;
        }
    }

    private OverlayParams getOverlayParams() {
        float horizontal = hbCropBar.getProgress() / 100f;
        float vertical = vbCropBar.getProgress() / 100f;
        float width = hsCropBar.getProgress() / 100f;
        float height = vsCropBar.getProgress() / 100f;
        cropFreeStyleTextAnalyzer.setCrop(horizontal, vertical, width, height);
        return new OverlayParams(width, height, horizontal, vertical);
    }

    private MeteringPoint buildMeteringPoint() {
        // Get center of the overlay
        OverlayParams params = getOverlayParams();
        float centerX = (1 - params.widthPercent) * params.horizontalBias + params.widthPercent / 2;
        float centerY = (1 - params.heightPercent) * params.verticalBias + params.heightPercent / 2;
        // Skip focusing if we are close to the center of the image
        if (Math.abs(centerX - 0.5) < 0.05 && Math.abs(centerY - 0.5) < 0.05) {
            Log.d("EdgeOCRExample", "[buildMeteringPoint] Skip focusing on center of the image");
            return null;
        }
        MeteringPoint point;
        int rotation = camera.getCameraInfo().getSensorRotationDegrees();
        if (rotation == 0) {
            point = meteringPointFactory.createPoint(centerX, centerY);
        } else if (rotation == 90) {
            point = meteringPointFactory.createPoint(centerY, 1 - centerX);
        } else if (rotation == 180) {
            point = meteringPointFactory.createPoint(1 - centerX, 1 - centerY);
        } else {
            point = meteringPointFactory.createPoint(1 - centerY, centerX);
        }
        return point;
    }

    private void adaptOverlayWeights() {
        OverlayParams params = getOverlayParams();
        cameraOverlay.setCrop(params.horizontalBias, params.verticalBias, params.widthPercent, params.heightPercent);
    }
}
