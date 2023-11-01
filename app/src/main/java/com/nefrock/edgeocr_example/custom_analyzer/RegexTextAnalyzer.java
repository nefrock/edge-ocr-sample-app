package com.nefrock.edgeocr_example.custom_analyzer;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.error.EdgeError;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanResult;
import com.nefrock.edgeocr.model.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class RegexTextAnalyzer extends AnalyzerWithCallback {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    private final Pattern regexPattern;

    public RegexTextAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        //2023.9.30、のような日付のみスキャンする(2020年代のみ)
        this.regexPattern = Pattern.compile(".*(202\\d\\.\\d{1,2}\\.\\d{1,2}).*");
        this.isActive = true;
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");

            ScanResult scanResult = api.scanTexts(image);
            List<Detection<Text>> detections = scanResult.getTextDetections();
            List<Detection<Text>> filteredDetections = new ArrayList<>();
            for (Detection<Text> detection : detections ) {
                String text = detection.getScanObject().getText();
                Matcher matcher = regexPattern.matcher(text);
                if(matcher.find()) {
                    String newText = matcher.group(1);
                    detection.getScanObject().setText(newText);
                    filteredDetections.add(detection);
                }
            }
            callback.call(filteredDetections, detections);
        } catch (EdgeError e) {
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
