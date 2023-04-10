package com.nefrock.edgeocr_example.ui;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import android.app.AlertDialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager.LayoutParams;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Toast;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.camera.core.UseCaseGroup;
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
import com.nefrock.edgeocr_example.analysers.EditDistanceTextAnalyser;
import com.nefrock.edgeocr_example.analysers.FreeStyleTextAnalyser;
import com.nefrock.edgeocr_example.analysers.RegexTextAnalyser;
import com.nefrock.edgeocr_example.analysers.WhitelistTextAnalyser;

@ExperimentalCamera2Interop public class TextScannerActivity extends AppCompatActivity {

    private final ExecutorService analysisExecutor = Executors.newSingleThreadExecutor();
    private AnalyserWithCallback imageAnalyser;
    private BoxesOverlay boxesOverlay;
    private ImageAnalysis imageAnalysis;
    private boolean overlayDrawn = false;
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
            imageAnalyser = buildAnalyser(analyzerTypeFromMainIntent, api);
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

        // Wait for overlay to be drawn
        findViewById(R.id.boxesOverlay).post(() -> overlayDrawn = true);

        api.useModel(model, (ModelInformation modelInformation) -> {
            float aspectRatio = modelInformation.getAspectRatio();
            // Overlay consists of a top and bottom view, as well as the central
            // boxesOverlay. Use weights to ensure that the boxesOverlay is
            // has the same aspect ratio as the model.
            View overlay = findViewById(R.id.overlay);
            while (!overlayDrawn) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Log.e("EdgeOCRExample", "[onCreate] Interrupted while waiting for overlay to be drawn", e);
                    return;
                }
            }
            float overlayAspectRatio = (float) overlay.getWidth() / (float) overlay.getHeight();
            if (aspectRatio < overlayAspectRatio) {
                Log.e("EdgeOCRExample", "[onCreate] Aspect ratio of model is smaller than aspect ratio of overlay");
                return;
            }
            float boxesOverlayWeight = overlayAspectRatio / aspectRatio;
            boxesOverlay = findViewById(R.id.boxesOverlay);
            LinearLayout.LayoutParams boxesOverlayLayoutParams = (LinearLayout.LayoutParams) boxesOverlay.getLayoutParams();
            boxesOverlayLayoutParams.weight = boxesOverlayWeight;
            View overlayTop = findViewById(R.id.overlayTop);
            View overlayBottom = findViewById(R.id.overlayBottom);
            LinearLayout.LayoutParams overlayTopLayoutParams = (LinearLayout.LayoutParams) overlayTop.getLayoutParams();
            LinearLayout.LayoutParams overlayBottomLayoutParams = (LinearLayout.LayoutParams) overlayBottom.getLayoutParams();
            overlayTopLayoutParams.weight = (1 - boxesOverlayWeight) / 2;
            overlayBottomLayoutParams.weight = (1 - boxesOverlayWeight) / 2;
            runOnUiThread(() -> {
                overlayTop.setLayoutParams(overlayTopLayoutParams);
                overlayBottom.setLayoutParams(overlayBottomLayoutParams);
                boxesOverlay.setLayoutParams(boxesOverlayLayoutParams);
            });
            imageAnalyser.setCallback((filteredDetections, allDetections) -> {
                runOnUiThread(() -> boxesOverlay.setBoxes(allDetections));
                if (!showDialog) {
                    return;
                }
                if (filteredDetections.size() == 0) {
                    return;
                }
                runOnUiThread(() -> showDialog(filteredDetections));

            });
        }, (EdgeError e) -> Log.e("EdgeOCRExample", "[onCreate] Failed to load model", e));
        if (cameraPermissionGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(
                    this, new String[]{"android.permission.CAMERA"}, 10);
        }

        SeekBar seekBar = findViewById(R.id.seekBar);
        seekBar.setProgress(50);
        seekBar.setMax(100);
        seekBar.setOnSeekBarChangeListener(
                new SeekBar.OnSeekBarChangeListener() {
                    //ツマミがドラッグされると呼ばれる
                    @Override
                    public void onProgressChanged(
                            SeekBar seekBar, int progress, boolean fromUser) {
                        camera.getCameraControl().setLinearZoom(progress / 100.0f);
                    }

                    //ツマミがタッチされた時に呼ばれる
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    //ツマミがリリースされた時に呼ばれる
                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }

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

    @ExperimentalCamera2Interop
    @OptIn(markerClass = {ExperimentalCamera2Interop.class})
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
            imageAnalysis.setAnalyzer(analysisExecutor, imageAnalyser);

            ImageCapture.Builder imageCaptureBuilder = new ImageCapture.Builder();

            // 固定フォーカスの設定
            // 固定フォーカスを利用する場合は、次のコメントアウトを外してください
            // focusDistanceはユースケース、ハードウェア依存なので、実際に利用しながら決めてください。
//            Camera2Interop.Extender<ImageCapture> camera2Extender = new Camera2Interop.Extender<>(imageCaptureBuilder);
//            float focusDistance = 5.0f;
//            camera2Extender.setCaptureRequestOption(
//                    CaptureRequest.LENS_FOCUS_DISTANCE,
//                    focusDistance
//            );
//            camera2Extender.setCaptureRequestOption(
//                    CaptureRequest.CONTROL_AF_MODE,
//                    CaptureRequest.CONTROL_AF_MODE_OFF
//            );
            // 固定フォーカスの設定の終わり

            ImageCapture imageCapture = imageCaptureBuilder
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build();

            // Bind use cases to camera
            UseCaseGroup useCaseGroup = new UseCaseGroup.Builder()
                    .addUseCase(preview)
                    .addUseCase(imageAnalysis)
                    .addUseCase(imageCapture)
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

    private static AnalyserWithCallback buildAnalyser(String typ, EdgeVisionAPI api) {
        switch (typ) {
            case "free_style":
                return new FreeStyleTextAnalyser(api);
            case "whitelist":
                return new WhitelistTextAnalyser(api);
            case "regex":
                return new RegexTextAnalyser(api);
            case "edit_distance":
                return new EditDistanceTextAnalyser(api);
            default:
                throw new IllegalArgumentException("undefined analyser type found:" + typ);
        }
    }

    private void showDialog(List<Detection> detections) {
        imageAnalyser.stop();
        StringBuilder messageBuilder = new StringBuilder();
        for (Detection detection : detections) {
            messageBuilder.append(detection.getText()).append("\n");
        }
        AlertDialog alertDialog = new AlertDialog.Builder(this)
                .setTitle("検出")
                .setMessage(messageBuilder.toString())
                .setOnDismissListener(dialogInterface -> {
                    imageAnalyser.resume();
                    api.resetScanningState();
                })
                .setPositiveButton("OK", null).create();
        LayoutParams lp = alertDialog.getWindow().getAttributes();
        lp.alpha = 0.9f;
        lp.gravity = Gravity.BOTTOM;
        alertDialog.show();
    }
}
