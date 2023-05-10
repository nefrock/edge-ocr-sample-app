package com.nefrock.edgeocr_example.analysers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanResult;

import java.util.ArrayList;
import java.util.List;

import info.debatty.java.stringsimilarity.Levenshtein;

public class EditDistanceTextAnalyser extends AnalyserWithCallback {

    private final EdgeVisionAPI api;
    private volatile boolean isScanning;
    private final Levenshtein metrics;
    private final List<String> candidates;

    public EditDistanceTextAnalyser(EdgeVisionAPI api) {
        this.api = api;
        this.isScanning = true;
        this.metrics = new Levenshtein();
        this.candidates = new ArrayList<String>() {
            {
                add("東京都");
                add("神奈川県");
                add("群馬県");
                add("埼玉県");
                add("茨城県");
                add("栃木県");
                add("千葉県");
            }
        };
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

            ScanResult analysisResult = api.scanTexts(image, cropLeft, cropTop, cropSize);
            List<Detection> rawDetections = analysisResult.getDetections();
            List<Detection> filteredDetections = new ArrayList<>();
            for (Detection detection : rawDetections) {
                // N文字の間違いを許容する
                double minDist = 2;
                String text = detection.getText();
                Detection d = null;
                String minText = "";
                for (String candidate : candidates) {
                    double currentDist = metrics.distance(candidate, text);
                    if (currentDist < minDist) {
                        d = detection;
                        minDist = currentDist;
                        minText = candidate;
                    }
                }
                if (d != null) {
                    Detection newDetection = new Detection(minText, d.getCategory(), d.getBoundingBox(),
                            d.getConfidence());
                    filteredDetections.add(newDetection);
                }
            }
            callback.call(filteredDetections, analysisResult.getDetections());
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
