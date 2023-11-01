package com.nefrock.edgeocr_example.custom_analyzer;

import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.Text;

import java.util.List;
interface AnalysisCallback {

    void call(List<Detection<Text>> filteredDetections, List<Detection<Text>> allDetections);
}
