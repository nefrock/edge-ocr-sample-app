package com.nefrock.edgeocr_example.barcode;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.BarcodeScanOption;
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Barcode;
import com.nefrock.edgeocr.model.BarcodeFormat;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanConfirmationStatus;
import com.nefrock.edgeocr.model.ScanResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class BarcodeAnalyzer implements ImageAnalysis.Analyzer {

    private final EdgeVisionAPI api;
    private final BarcodeScanOption scanOption;
    private volatile boolean isActive;
    private BarcodeAnalyzerCallBack callback;

    public BarcodeAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.scanOption = new BarcodeScanOption(Collections.singletonList(BarcodeFormat.Any));
        this.isActive = true;
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            ScanResult scanResult = api.scanBarcodes(image, scanOption);
            List<Detection<Barcode>> targetDetections = new ArrayList<>();
            for (Detection<Barcode> detection : scanResult.getBarcodeDetections()) {
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
        void call(List<Detection<Barcode>> allDetections);
    }
}
