package com.nefrock.edgeocr_example.analysers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.model.BarcodeFormat;
import com.nefrock.edgeocr.model.BarcodeScanOption;
import com.nefrock.edgeocr.model.ScanResult;

import java.util.Arrays;

public class BarcodeAnalyser extends AnalyserWithCallback {

    private final EdgeVisionAPI api;
    private final BarcodeScanOption scanOption;
    private volatile boolean isScanning;

    public BarcodeAnalyser(EdgeVisionAPI api) {
        this.api = api;
        this.scanOption = new BarcodeScanOption(Arrays.asList(BarcodeFormat.FORMAT_CODE_128, BarcodeFormat.FORMAT_EAN_13));
        this.isScanning = true;
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isScanning) return;
            if (callback == null) return;
            ScanResult analysisResult = api.scanBarcodes(image, scanOption);
            callback.call(analysisResult.getDetections(), analysisResult.getDetections());
        } catch (RuntimeException e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    @Override
    public void stop() {
        isScanning = false;
    }

    @Override
    public void resume() {
        isScanning = true;
    }
}
