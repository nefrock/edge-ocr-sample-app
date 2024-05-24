package com.nefrock.edgeocr_example.barcode;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.Barcode;
import com.nefrock.edgeocr.BarcodeFormat;
import com.nefrock.edgeocr.EdgeError;
import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.ScanConfirmationStatus;
import com.nefrock.edgeocr.ScanOptions;
import com.nefrock.edgeocr.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class BarcodeAnalyzer implements ImageAnalysis.Analyzer {

    private final EdgeVisionAPI api;
    private final ScanOptions scanOptions;
    private volatile boolean isActive;
    private BarcodeAnalyzerCallBack callback;

    public BarcodeAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.scanOptions = new ScanOptions();
        this.scanOptions.setBarcodeFormats(Collections.singletonList(BarcodeFormat.Any));
        this.isActive = true;
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            ScanResult scanResult = api.scan(image, scanOptions);
            List<Barcode> targetDetections = new ArrayList<>();
            for (Barcode detection : scanResult.getBarcodeDetections()) {
                if (detection.getStatus() == ScanConfirmationStatus.Confirmed) {
                    targetDetections.add(detection);
                }
            }
            callback.call(targetDetections);
        } catch (EdgeError e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    void setCallback(BarcodeAnalyzerCallBack callback) {
        this.callback = callback;
    }

    void stop() {
        isActive = false;
    }

    void resume() {
        isActive = true;
    }

    interface BarcodeAnalyzerCallBack {
        void call(List<Barcode> allDetections);
    }
}
