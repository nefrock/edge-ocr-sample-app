package com.nefrock.edgeocr_example.custom_analyzer;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Gravity;
import android.view.WindowManager.LayoutParams;
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
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.Model;
import com.nefrock.edgeocr.model.ModelInformation;
import com.nefrock.edgeocr.model.ScanConfirmationStatus;
import com.nefrock.edgeocr.model.Text;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@ExperimentalCamera2Interop public class TextScannerActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService focusExecutor = Executors.newSingleThreadExecutor();
    private AnalyzerWithCallback imageAnalyzer;
    private CameraOverlay cameraOverlay;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private Camera camera;
    //ダイアログを表示するか（表示中はスキャンを辞める）
    private boolean showDialog = false;
    EdgeVisionAPI api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_text_scanner);
        // Initialize EdgeOCR
        Model model = null;
        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
            showDialog = getIntent().getBooleanExtra("show_dialog", false);
            String analyzerTypeFromMainIntent = getIntent().getStringExtra("analyser_type");
            imageAnalyzer = buildAnalyser(analyzerTypeFromMainIntent, api);
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

        if (model == null) {
            Log.e("EdgeOCRExample", "[onCreate] Failed to initialize EdgeOCR");
            return;
        }

        cameraOverlay = findViewById(R.id.overlay);

        api.useModel(model, (ModelInformation modelInformation) -> {
            cameraOverlay.setAspectRatio(modelInformation.getAspectRatio());
            api.setTextNToConfirm(5);
            imageAnalyzer.setCallback((filteredDetections, allDetections) -> {
                runOnUiThread(() -> cameraOverlay.setBoxes(allDetections));
                List<Detection<Text>> confirmedDetections = new ArrayList<>();
                for(Detection<Text> detection: filteredDetections) {
                    if (detection.getStatus() == ScanConfirmationStatus.Confirmed) {
                        confirmedDetections.add(detection);
                    }
                }
                if (!showDialog) {
                    return;
                }
                if (confirmedDetections.size() == 0) {
                    return;
                }
                // UIスレッドによるダイアログ表示前にスキャンを止める
                imageAnalyzer.stop();
                runOnUiThread(() -> showDialog(confirmedDetections));
            });
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
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

    private static AnalyzerWithCallback buildAnalyser(String typ, EdgeVisionAPI api) {
        switch (typ) {
            case "whitelist":
                return new WhitelistTextAnalyzer(api);
            case "regex":
                return new RegexTextAnalyzer(api);
            case "edit_distance":
                return new EditDistanceTextAnalyzer(api);
            default:
                throw new IllegalArgumentException("undefined analyser type found:" + typ);
        }
    }

    private void showDialog(List<Detection<Text>> detections) {
        StringBuilder messageBuilder = new StringBuilder();
        for (Detection<Text> detection : detections) {
            messageBuilder.append(detection.getScanObject().getText()).append("\n");
        }
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener(dialogInterface -> {
                    imageAnalyzer.resume();
                    api.resetScanningState();
                })
                .setPositiveButton("OK", null).create();
        LayoutParams lp = alertDialog.getWindow().getAttributes();
        lp.alpha = 0.9f;
        lp.gravity = Gravity.BOTTOM;
        alertDialog.show();
    }
}
