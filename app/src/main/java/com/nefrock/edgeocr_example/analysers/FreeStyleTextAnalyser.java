package com.nefrock.edgeocr_example.analysers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.model.ScanResult;

public class FreeStyleTextAnalyser extends AnalyserWithCallback {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;

    public FreeStyleTextAnalyser(EdgeVisionAPI api) {
        this.api = api;
        this.isActive = true;
    }

    @Override
    @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");

            ScanResult analysisResult = api.scanTexts(image, cropLeft, cropTop, cropSize);
            callback.call(analysisResult.getDetections(), analysisResult.getDetections());
        } catch (RuntimeException e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    @Override
    public void stop() {
        isActive = false;
    }

    @Override
    public void resume() {
        isActive = true;
    }
}
