package com.nefrock.edgeocr_example.analysers;

import java.util.List;

import com.nefrock.edgeocr.model.Detection;

public interface AnalysisCallback {

    void call(List<Detection> filteredDetections, List<Detection> allDetections);
}
