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
import java.util.Arrays;
import java.util.List;

import info.debatty.java.stringsimilarity.Levenshtein;
class EditDistanceTextAnalyzer extends AnalyzerWithCallback {

    private final EdgeVisionAPI api;
    private volatile boolean isScanning;
    private final Levenshtein metrics;
    private final List<String> candidates;

    public EditDistanceTextAnalyzer(EdgeVisionAPI api) {
        this.api = api;
        this.isScanning = true;
        this.metrics = new Levenshtein();
        this.candidates = Arrays.asList("東京都", "神奈川県", "群馬県", "埼玉県", "茨城県", "栃木県", "千葉県");
    }

    @Override
    @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isScanning)
                return;
            if (callback == null)
                return;
            if (!api.isReady())
                throw new RuntimeException("Model not loaded!");

            ScanResult scanResult = api.scanTexts(image);
            List<Detection<Text>> rawDetections = scanResult.getTextDetections();
            List<Detection<Text>> filteredDetections = new ArrayList<>();
            for (Detection<Text> detection : rawDetections) {
                // N文字の間違いを許容する
                double minDist = 2;
                String text = detection.getScanObject().getText();
                Detection<Text> targetDetection = null;
                String minText = "";
                for (String candidate : candidates) {
                    double currentDist = metrics.distance(candidate, text);
                    if (currentDist < minDist) {
                        targetDetection = detection;
                        minDist = currentDist;
                        minText = candidate;
                    }
                }
                if (targetDetection != null) {
                    targetDetection.getScanObject().setText(minText);
                    filteredDetections.add(targetDetection);
                }
            }
            callback.call(filteredDetections, scanResult.getTextDetections());
        } catch (EdgeError e) {
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
