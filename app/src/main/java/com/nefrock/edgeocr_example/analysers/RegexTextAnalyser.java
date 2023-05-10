package com.nefrock.edgeocr_example.analysers;

import java.util.ArrayList;
import java.util.List;
import android.util.Log;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanResult;

public class RegexTextAnalyser extends AnalyserWithCallback {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    private final Pattern regexPattern;

    public RegexTextAnalyser(EdgeVisionAPI api) {
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

            ScanResult analysisResult = api.scanTexts(image, cropLeft, cropTop, cropSize);
            List<Detection> rawDetections = analysisResult.getDetections();
            List<Detection> filteredDetection = new ArrayList<>();
            for (Detection rawDetection : rawDetections ) {
                String text = rawDetection.getText();
                Matcher matcher = regexPattern.matcher(text);
                if(matcher.find()) {
                    String newText = matcher.group(1);
                    Detection newDetection = new Detection(newText, rawDetection.getCategory(), rawDetection.getBoundingBox(), rawDetection.getConfidence());
                    filteredDetection.add(newDetection);
                }
            }
            callback.call(filteredDetection, rawDetections);
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
