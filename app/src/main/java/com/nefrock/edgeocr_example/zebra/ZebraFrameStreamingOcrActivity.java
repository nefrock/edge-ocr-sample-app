package com.nefrock.edgeocr_example.zebra;

import static com.nefrock.edgeocr_example.zebra.ZebraIntentKeys.DATAWEDGE_API_ACTION;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.nefrock.edgeocr.EdgeError;
import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.ScanResult;
import com.nefrock.edgeocr.ui.CameraOverlay;
import com.nefrock.edgeocr_example.R;
import android.view.WindowManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ZebraFrameStreamingOcrActivity extends AppCompatActivity {

    public static final String NOTIFICATION_ACTION = "com.symbol.datawedge.api.NOTIFICATION_ACTION";
    public static final String NOTIFICATION_TYPE_WORKFLOW_STATUS = "WORKFLOW_STATUS";
    public static final String INTENT_OUTPUT_ACTION = "com.symbol.genericdata.INTENT_OUTPUT";
    public static final String RESULT_ACTION = "com.symbol.datawedge.api.RESULT_ACTION";

    private final String TAG = "ZebraOCR";
    private ImageView imageView;
    private CameraOverlay overlay;
    private TextView statusText;
    private TextView fpsText;
    private Button triggerButton;
    private Button clearButton;

    private EdgeVisionAPI api;
    private long startTime = 0L;
    private int frameCount = 0;
    private volatile boolean capturing = false;
    private boolean imageRatioSet = false;
    private volatile boolean keepScanning = false;
    private volatile long lastFrameAt = 0L;
    private final Handler keepAliveHandler = new Handler(Looper.getMainLooper());
    private final Runnable keepAliveTask = new Runnable() {
        @Override public void run() {
            if (!isFinishing() && keepScanning) {
                long now = System.currentTimeMillis();
                boolean stalled = (now - lastFrameAt) > 2500;
                if (stalled || !capturing) {
                    Log.d(TAG, "KeepAlive: restart scanning");
                    startSoftTrigger();
                }
                keepAliveHandler.postDelayed(this, 2000);
            }
        }
    };

    private final ExecutorService ocrExecutor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean ocrRunning = new AtomicBoolean(false);
    private final AtomicReference<Bitmap> latestBitmap = new AtomicReference<>(null);

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (NOTIFICATION_ACTION.equals(action)) {
                Bundle b = intent.getBundleExtra("com.symbol.datawedge.api.NOTIFICATION");
                if (b == null) return;
                String type = b.getString("NOTIFICATION_TYPE");
                if (NOTIFICATION_TYPE_WORKFLOW_STATUS.equals(type)) {
                    String status = b.getString("STATUS", "");
                    statusText.setText("Workflow: " + status);
                    if ("CAPTURING_STARTED".equals(status)) {
                        capturing = true;
                        frameCount = 0;
                        startTime = System.currentTimeMillis();
                        requestOneFrame();
                    } else if ("IDLE".equals(status) || "CAPTURING_STOPPED".equals(status)) {
                        capturing = false;
                        if (keepScanning) {
                            startSoftTrigger();
                        }
                    }
                }
            } else if (INTENT_OUTPUT_ACTION.equals(action)) {
                handleIntentOutput(intent);
            } else if (RESULT_ACTION.equals(action)) {
                // Debug errors of REQUEST_FRAME
                String cid = intent.getStringExtra("COMMAND_IDENTIFIER");
                if (Objects.equals(intent.getStringExtra("RESULT"), "FAILURE") && Objects.equals(cid, "REQUEST_FRAME")) {
                    Bundle info = intent.getBundleExtra("RESULT_INFO");
                    if (info != null) {
                        StringBuilder sb = new StringBuilder();
                        Set<String> keys = info.keySet();
                        for (String k : keys) sb.append(k).append(": ").append(info.get(k)).append("\n");
                        Log.w(TAG, "REQUEST_FRAME failed: \n" + sb);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_zebra_ocr);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        imageView = findViewById(R.id.imageView);
        overlay = findViewById(R.id.camera_overlay);
        statusText = findViewById(R.id.statusText);
        fpsText = findViewById(R.id.fpsText);
        triggerButton = findViewById(R.id.startSoftTriggerBtn);
        clearButton = findViewById(R.id.clearBtn);

        try {
            api = new EdgeVisionAPI.Builder(this).fromAssets("models").build();
        } catch (Exception e) {
            Log.e(TAG, "EdgeOCR init error", e);
        }

        float modelAspectRatio = getIntent().getFloatExtra("model_aspect_ratio", 1.0f);
        overlay.setAspectRatio(modelAspectRatio);

        setIntentFilter();
        registerForWorkflowStatus();
        createProfile();

        triggerButton.setOnClickListener(v -> {
            keepScanning = true;
            startSoftTrigger();
        });
        clearButton.setOnClickListener(v -> clearFrame());

        // KeepAlive 開始
        keepScanning = true;
        keepAliveHandler.postDelayed(keepAliveTask, 2000);
    }

    private void clearFrame() {
        frameCount = 0;
        startTime = System.currentTimeMillis();
        imageView.setImageDrawable(null);
        overlay.setBoxes(new ArrayList<>());
        fpsText.setText("FPS: 0.0");
    }

    private void handleIntentOutput(Intent intent) {
        try {
            String jsonData = intent.getStringExtra(ZebraIntentKeys.DATA_TAG);
            if (jsonData == null) return;
            JSONArray arr = new JSONArray(jsonData);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                if (obj.has("uri")) {
                    String uri = obj.getString("uri");
                    // 次フレーム要求を前倒ししてスループット確保
                    if (capturing) requestOneFrame();

                    Bitmap bmp = readBitmapFromProvider(uri, obj);
                    if (bmp != null) {
                        imageView.setImageBitmap(bmp);
                        if (!imageRatioSet) {
                            androidx.constraintlayout.widget.ConstraintLayout.LayoutParams lp = (androidx.constraintlayout.widget.ConstraintLayout.LayoutParams) imageView.getLayoutParams();
                            lp.dimensionRatio = bmp.getWidth() + ":" + bmp.getHeight();
                            imageView.setLayoutParams(lp);
                            imageRatioSet = true;
                        }
                        // 最新フレームのみOCRへ（バックログは破棄）
                        scheduleOcr(bmp);
                        frameCount++;
                        updateFps();
                        lastFrameAt = System.currentTimeMillis();
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "INTENT_OUTPUT parse error", e);
        }
    }

    private void runOcr(Bitmap bmp) {
        if (api == null || bmp == null) return;
        try {
            long t0 = System.nanoTime();
            ScanResult result = api.scan(bmp);
            long ms = (System.nanoTime() - t0) / 1_000_000L;
            Log.d(TAG, "OCR time: " + ms + "ms");
            overlay.setBoxes(result.getTextDetections());
        } catch (EdgeError e) {
            Log.e(TAG, "scan error", e);
        }
    }

    private void scheduleOcr(Bitmap bmp) {
        latestBitmap.set(bmp);
        if (!ocrRunning.compareAndSet(false, true)) return; // 既に実行中
        ocrExecutor.execute(() -> {
            try {
                while (true) {
                    Bitmap next = latestBitmap.getAndSet(null);
                    if (next == null) break;
                    runOcr(next);
                }
            } finally {
                ocrRunning.set(false);
                // ループ終了後に新規が入っていれば再スケジュール
                if (latestBitmap.get() != null && ocrRunning.compareAndSet(false, true)) {
                    ocrExecutor.execute(this::drainOcrQueue);
                }
            }
        });
    }

    private void drainOcrQueue() {
        try {
            while (true) {
                Bitmap next = latestBitmap.getAndSet(null);
                if (next == null) break;
                runOcr(next);
            }
        } finally {
            ocrRunning.set(false);
        }
    }

    private void updateFps() {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed <= 0) return;
        final double fps = frameCount / (elapsed / 1000.0);
        new Handler(Looper.getMainLooper()).post(() -> fpsText.setText(String.format("FPS: %.1f", fps)));
    }

    private void setIntentFilter() {
        IntentFilter f = new IntentFilter();
        f.addAction(NOTIFICATION_ACTION);
        f.addAction(INTENT_OUTPUT_ACTION);
        f.addAction(RESULT_ACTION);
        f.addCategory(Intent.CATEGORY_DEFAULT);
        registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
    }

    private void registerForWorkflowStatus() {
        Bundle b = new Bundle();
        b.putString("com.symbol.datawedge.api.APPLICATION_NAME", getPackageName());
        b.putString("com.symbol.datawedge.api.NOTIFICATION_TYPE", NOTIFICATION_TYPE_WORKFLOW_STATUS);
        Intent i = new Intent();
        i.setAction(DATAWEDGE_API_ACTION);
        i.putExtra("com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION", b);
        sendBroadcast(i);
    }

    private void createProfile() {
        // Create / update a DataWedge profile for imager frame streaming
        Bundle bMain = new Bundle();

        // WORKFLOW plugin
        Bundle bConfigWorkflow = new Bundle();
        bConfigWorkflow.putString("PLUGIN_NAME", "WORKFLOW");
        bConfigWorkflow.putString("RESET_CONFIG", "true");
        bConfigWorkflow.putString("workflow_input_enabled", "true");
        bConfigWorkflow.putString("selected_workflow_name", "imager_frames_streaming");
        bConfigWorkflow.putString("workflow_input_source", "1"); // 1: imager

        Bundle paramList = new Bundle();
        paramList.putString("workflow_name", "imager_frames_streaming");
        paramList.putString("workflow_input_source", "1");
        Bundle paramSetImageStreamModule = new Bundle();
        paramSetImageStreamModule.putString("module", "ImagerModule");
        Bundle moduleParams = new Bundle();
        moduleParams.putString("imagecapture_mode", "1"); // image only
        moduleParams.putString("session_timeout", "60000");
        moduleParams.putString("illumination", "on");
        moduleParams.putString("viewfinder_enablement", "1");
        paramSetImageStreamModule.putBundle("module_params", moduleParams);
        ArrayList<Bundle> paramSetList = new ArrayList<>();
        paramSetList.add(paramSetImageStreamModule);
        paramList.putParcelableArrayList("workflow_params", paramSetList);
        ArrayList<Bundle> workFlowList = new ArrayList<>();
        workFlowList.add(paramList);
        bConfigWorkflow.putParcelableArrayList("PARAM_LIST", workFlowList);

        // INTENT output plugin
        Bundle bConfigIntent = new Bundle();
        bConfigIntent.putString("PLUGIN_NAME", "INTENT");
        bConfigIntent.putString("RESET_CONFIG", "true");
        Bundle bParamsIntent = new Bundle();
        bParamsIntent.putString("intent_output_enabled", "true");
        bParamsIntent.putString("intent_action", INTENT_OUTPUT_ACTION);
        bParamsIntent.putString("intent_category", Intent.CATEGORY_DEFAULT);
        bParamsIntent.putInt("intent_delivery", 2); // broadcast
        bParamsIntent.putString("intent_use_content_provider", "true");
        bConfigIntent.putBundle("PARAM_LIST", bParamsIntent);

        ArrayList<Bundle> pluginList = new ArrayList<>();
        pluginList.add(bConfigWorkflow);
        pluginList.add(bConfigIntent);

        bMain.putParcelableArrayList("PLUGIN_CONFIG", pluginList);

        // associate app
        Bundle appList = new Bundle();
        appList.putString("PACKAGE_NAME", getPackageName());
        appList.putStringArray("ACTIVITY_LIST", new String[]{"*"});
        bMain.putParcelableArray("APP_LIST", new Bundle[]{appList});

        bMain.putString("PROFILE_NAME", "EdgeOCRFrameStream");
        bMain.putString("PROFILE_ENABLED", "true");
        bMain.putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST");
        bMain.putString("RESET_CONFIG", "true");

        Intent iSetConfig = new Intent();
        iSetConfig.setAction("com.symbol.datawedge.api.ACTION");
        iSetConfig.setPackage("com.symbol.datawedge");
        iSetConfig.putExtra("com.symbol.datawedge.api.SET_CONFIG", bMain);
        iSetConfig.putExtra("SEND_RESULT", "COMPLETE_RESULT");
        iSetConfig.putExtra("COMMAND_IDENTIFIER", "CREATE_PROFILE");
        sendBroadcast(iSetConfig);
    }

    private void startSoftTrigger() {
        Intent i = new Intent();
        i.setAction(DATAWEDGE_API_ACTION);
        i.setPackage("com.symbol.datawedge");
        i.putExtra("com.symbol.datawedge.api.SOFT_SCAN_TRIGGER", "START_SCANNING");
        sendBroadcast(i);
    }

    private void requestOneFrame() {
        Intent i = new Intent();
        i.setAction(DATAWEDGE_API_ACTION);
        i.setPackage("com.symbol.datawedge");
        i.putExtra("com.symbol.datawedge.api.REQUEST_FRAME", "");
        i.setFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        i.putExtra("SEND_RESULT", "LAST_RESULT");
        i.putExtra("COMMAND_IDENTIFIER", "REQUEST_FRAME");
        sendBroadcast(i);
    }

    private Bitmap readBitmapFromProvider(String uri, JSONObject meta) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (Cursor c = getContentResolver().query(Uri.parse(uri), null, null, null)) {
                if (c != null && c.moveToFirst()) {
                    baos.write(c.getBlob(c.getColumnIndexOrThrow(ZebraIntentKeys.RAW_DATA)));
                    String next = c.getString(c.getColumnIndexOrThrow(ZebraIntentKeys.DATA_NEXT_URI));
                    while (next != null && !next.isEmpty()) {
                        try (Cursor cNext = getContentResolver().query(Uri.parse(next), null, null, null)) {
                            if (cNext != null && cNext.moveToFirst()) {
                                baos.write(cNext.getBlob(cNext.getColumnIndexOrThrow(ZebraIntentKeys.RAW_DATA)));
                                next = cNext.getString(cNext.getColumnIndexOrThrow(ZebraIntentKeys.DATA_NEXT_URI));
                            } else {
                                break;
                            }
                        }
                    }
                }
            }

            int width = meta.getInt(ZebraIntentKeys.IMAGE_WIDTH);
            int height = meta.getInt(ZebraIntentKeys.IMAGE_HEIGHT);
            int stride = meta.getInt(ZebraIntentKeys.STRIDE);
            int orientation = meta.getInt(ZebraIntentKeys.ORIENTATION);
            String format = meta.getString(ZebraIntentKeys.IMAGE_FORMAT);
            return ZebraImageProcessing.toBitmap(baos.toByteArray(), format, orientation, stride, width, height);
        } catch (Exception e) {
            Log.e(TAG, "provider read error", e);
            return null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        ocrExecutor.shutdownNow();
        keepScanning = false;
        keepAliveHandler.removeCallbacksAndMessages(null);
        // Unregister workflow status
        Bundle b = new Bundle();
        b.putString("com.symbol.datawedge.api.APPLICATION_NAME", getPackageName());
        b.putString("com.symbol.datawedge.api.NOTIFICATION_TYPE", NOTIFICATION_TYPE_WORKFLOW_STATUS);
        Intent i = new Intent();
        i.setAction(DATAWEDGE_API_ACTION);
        i.putExtra("com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION", b);
        sendBroadcast(i);
    }
}
