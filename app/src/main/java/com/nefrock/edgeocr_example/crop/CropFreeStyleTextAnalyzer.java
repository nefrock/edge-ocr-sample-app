package com.nefrock.edgeocr_example.crop;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.CropRect;
import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.api.ScanOption;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.model.Text;

import java.util.List;

class CropFreeStyleTextAnalyzer implements ImageAnalysis.Analyzer {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    public Float cropLeft, cropTop, cropWidth, cropHeight;
    private FreeStyleTextAnalyzerCallback callback;

    public CropFreeStyleTextAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.isActive = true;
    }

    synchronized void setCrop(float left, float top, float width, float height) {
        cropLeft = left;
        cropTop = top;
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
                scanResult = api.scanTexts(
                        image,
                        new ScanOption(
                                ScanOption.ScanMode.SCAN_MODE_TEXTS,
                                new CropRect(
                                        cropLeft,
                                        cropTop,
                                        cropWidth,
                                        cropHeight)
                        )
                );
            }
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

    interface FreeStyleTextAnalyzerCallback {
        void call(List<Detection<Text>> allDetections);
    }
}
