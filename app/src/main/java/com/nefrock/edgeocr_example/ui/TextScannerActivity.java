package com.nefrock.edgeocr_example.ui;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.ClipboardManager;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager.LayoutParams;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.ExperimentalCamera2Interop;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.CameraControl;
import androidx.camera.core.FocusMeteringAction;
import androidx.camera.core.FocusMeteringResult;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.MeteringPoint;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.SurfaceOrientedMeteringPointFactory;
import androidx.camera.core.UseCaseGroup;
import androidx.camera.core.ViewPort;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
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
    private final ExecutorService focusExecutor = Executors.newSingleThreadExecutor();
    private AnalyserWithCallback imageAnalyser;
    private float aspectRatio = 1.0f;
    private ConstraintLayout overlay;
    private BoxesOverlay boxesOverlay;
    private SeekBar hCropBar, vCropBar, sCropBar;
    private ImageAnalysis imageAnalysis;
    private ImageCapture imageCapture;
    private boolean overlayDrawn = false;
    private Camera camera;
    private SurfaceOrientedMeteringPointFactory meteringPointFactory;
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

        overlay = findViewById(R.id.overlay);
        boxesOverlay = findViewById(R.id.boxesOverlay);

        // Wait for overlay to be drawn
        overlay.post(() -> overlayDrawn = true);

        api.useModel(model, (ModelInformation modelInformation) -> {
            while (!overlayDrawn) {
                try {
                    Thread.sleep(30);
                } catch (InterruptedException e) {
                    Log.e("EdgeOCRExample", "[onCreate] Interrupted while waiting for overlay to be drawn", e);
                    return;
                }
            }
            aspectRatio = modelInformation.getAspectRatio();
            adaptOverlayWeights();
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

        hCropBar = findViewById(R.id.hCropBar);
        vCropBar = findViewById(R.id.vCropBar);
        sCropBar = findViewById(R.id.sCropBar);
        // Loop over seekbars and set their initial values
        for (SeekBar seekBar : new SeekBar[] {hCropBar, vCropBar, sCropBar}) {
            seekBar.setOnSeekBarChangeListener(
                    new SeekBar.OnSeekBarChangeListener() {
                        @Override
                        public void onProgressChanged(
                                SeekBar seekBar, int progress, boolean fromUser) {
                            adaptOverlayWeights();
                        }
                        @Override
                        public void onStartTrackingTouch(SeekBar seekBar) {}
                        @Override
                        public void onStopTrackingTouch(SeekBar seekBar) {}
                    });
            seekBar.setMax(100);
        }
        hCropBar.setProgress(0);
        vCropBar.setProgress(50);
        sCropBar.setProgress(100);

        Button reportButton = findViewById(R.id.reportButton);
        reportButton.setOnClickListener(
            (View v) -> {
                imageAnalyser.stop();
                api.resetScanningState();
                // Capture the image
                imageCapture.takePicture(
                    analysisExecutor,
                    new ImageCapture.OnImageCapturedCallback() {
                        @Override
                        public void onCaptureSuccess(ImageProxy image) {
                            try {
                                api.reportImage(image, imageAnalyser.cropLeft, imageAnalyser.cropTop, imageAnalyser.cropSize, "test");
                            } catch (Exception e) {
                                Log.e("EdgeOCRExample", Log.getStackTraceString(e));
                            } finally {
                                image.close();
                            }
                            imageAnalyser.resume();
                        }

                        @Override
                        public void onError(ImageCaptureException exception) {
                            Log.e("EdgeOCRExample", "[onCaptureSuccess] Failed to capture image", exception);
                        }
                    });
            });

        Button centerCaptureButton = findViewById(R.id.centerCaptureButton);
        centerCaptureButton.setOnClickListener(
            (View v) -> {
                String centerText = boxesOverlay.getCenterText();
                if (centerText == null) {
                    return;
                }
                Toast.makeText(
                    getApplicationContext(), "クリップボードにコピーしました: " + centerText,
                    Toast.LENGTH_SHORT).show();
                ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setText(centerText);
            });

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

    private class OverlayParams {
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
        float horizontal = hCropBar.getProgress() / 100f;
        float vertical = vCropBar.getProgress() / 100f;
        float size = sCropBar.getProgress() / 100f;
        imageAnalyser.setCrop(horizontal, vertical, size);
        float overlayAspectRatio = (float) overlay.getWidth() / overlay.getHeight();
        float widthPercent = size * Math.min(1, aspectRatio / overlayAspectRatio);
        float heightPercent = size * Math.min(1, overlayAspectRatio / aspectRatio);
        return new OverlayParams(widthPercent, heightPercent, horizontal, vertical);
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
        ConstraintLayout.LayoutParams boxesOverlayLayoutParams = (ConstraintLayout.LayoutParams) boxesOverlay.getLayoutParams();
        boxesOverlayLayoutParams.matchConstraintPercentWidth = params.widthPercent;
        boxesOverlayLayoutParams.matchConstraintPercentHeight = params.heightPercent;
        boxesOverlayLayoutParams.horizontalBias = params.horizontalBias;
        boxesOverlayLayoutParams.verticalBias = params.verticalBias;
        runOnUiThread(() -> {
            boxesOverlay.setLayoutParams(boxesOverlayLayoutParams);
        });
    }
}
