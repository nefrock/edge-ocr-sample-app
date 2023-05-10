package com.nefrock.edgeocr_example.analysers;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageProxy;

import com.nefrock.edgeocr.api.EdgeVisionAPI;
import com.nefrock.edgeocr.model.Detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WhitelistTextAnalyser extends AnalyserWithCallback {

    private final EdgeVisionAPI api;
    private volatile boolean isActive;
    private final Set<String> whiteList;

    public WhitelistTextAnalyser(EdgeVisionAPI api) {
        this.api = api;
        this.isActive = true;
        this.whiteList = new HashSet<String>(){
            {
                add("090-1234-5678");
                add("090-0000-1234");
                add("090-2222-3456");
                add("090-4444-5555");
                add("090-6666-7777");
                add("090-8888-9999");
            }
        };
    }

    @Override @androidx.camera.core.ExperimentalGetImage
    public void analyze(@NonNull ImageProxy image) {
        try {
            if (!isActive) return;
            if (callback == null) return;
            if (!api.isReady()) throw new RuntimeException("Model not loaded!");
            List<Detection> detections = api.scanTexts(image, cropLeft, cropTop, cropSize).getDetections();
            ArrayList<Detection> filteredDetections = new ArrayList<>();
            for (Detection detection : detections) {
                if(whiteList.contains(detection.getText())) {
                    filteredDetections.add(detection);
                    break;
                }
            }
            callback.call(filteredDetections, detections);
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
