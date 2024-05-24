package com.nefrock.edgeocr_example.ntimes_scan;

import com.nefrock.edgeocr.Text;

import java.util.List;

interface AnalysisCallback {
    void call(List<Text> filteredDetections, List<Text> allDetections);
}
