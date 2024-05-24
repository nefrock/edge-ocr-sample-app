package com.nefrock.edgeocr_example.crop;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.CropRect;
import com.nefrock.edgeocr.Detection;
import com.nefrock.edgeocr.EdgeError;
import com.nefrock.edgeocr.EdgeVisionAPI;
import com.nefrock.edgeocr.ScanOptions;
import com.nefrock.edgeocr.ScanResult;

import java.util.List;

class CropFreeStyleTextAnalyzer implements ImageAnalysis.Analyzer {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    public Float cropHorizontalBias, cropVerticalBias, cropWidth, cropHeight;
    private FreeStyleTextAnalyzerCallback callback;

    public CropFreeStyleTextAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.isActive = true;
    }

    synchronized void setCrop(float horizontalBias, float verticalBias, float width, float height) {
        cropHorizontalBias = horizontalBias;
        cropVerticalBias = verticalBias;
        cropWidth = width;
        cropHeight = height;
    }

    @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");

            ScanResult scanResult;
            synchronized (this) {
                ScanOptions scanOptions = new ScanOptions();
                scanOptions.setCropRect(new CropRect(cropHorizontalBias, cropVerticalBias, cropWidth, cropHeight));
                scanResult = api.scan(image, scanOptions);
            }
            callback.call(scanResult.getDetections());
        } catch (EdgeError e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    void setCallback(FreeStyleTextAnalyzerCallback callback) {
        this.callback = callback;
    }

    interface FreeStyleTextAnalyzerCallback {
        void call(List<Detection> allDetections);
    }
}
