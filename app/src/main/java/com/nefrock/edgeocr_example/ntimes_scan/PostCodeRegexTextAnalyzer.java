package com.nefrock.edgeocr_example.ntimes_scan;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanConfirmationStatus;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.model.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class PostCodeRegexTextAnalyzer implements ImageAnalysis.Analyzer {
    private AnalysisCallback callback;
    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    private final Pattern regexPattern;
    public PostCodeRegexTextAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.isActive = true;
        this.regexPattern = Pattern.compile("(\\d{3})-(\\d{4})");
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");

            ScanResult scanResult = api.scanTexts(image);
            List<Detection<Text>> filteredDetections = new ArrayList<>();
            List<Detection<Text>> notTargetDetections = new ArrayList<>();
            for (Detection<Text> detection : scanResult.getTextDetections()) {
                String text = detection.getScanObject().getText();
                Matcher matcher = regexPattern.matcher(text);
                if(matcher.find()) {
                    if (detection.getStatus() == ScanConfirmationStatus.Confirmed) {
                        filteredDetections.add(detection);
                    } else {
                        notTargetDetections.add(detection);
                    }
                }
            }
            callback.call(filteredDetections, notTargetDetections);
        } catch (EdgeError e) {
            Log.e("EdgeOCRExample", Log.getStackTraceString(e));
        } finally {
            image.close();
        }
    }

    public void setCallback(AnalysisCallback callback) {
        this.callback = callback;
    }

    public void stop() {
        isActive = false;
    }

    public void resume() {
        isActive = true;
    }
}