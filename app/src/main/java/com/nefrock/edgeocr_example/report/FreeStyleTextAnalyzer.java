package com.nefrock.edgeocr_example.report;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.model.Text;

import java.util.List;

class FreeStyleTextAnalyzer implements ImageAnalysis.Analyzer {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    private FreeStyleTextAnalyzerCallback callback;

    public FreeStyleTextAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.isActive = true;
    }

    @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");

            ScanResult scanResult;
            scanResult = api.scanTexts(image);
            callback.call(scanResult.getTextDetections());
        } catch (EdgeError e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    void setCallback(FreeStyleTextAnalyzerCallback callback) {
        this.callback = callback;
    }

    void stop() {
        isActive = false;
    }

    void resume() {
        isActive = true;
    }

    interface FreeStyleTextAnalyzerCallback {
        void call(List<Detection<Text>> allDetections);
    }
}
