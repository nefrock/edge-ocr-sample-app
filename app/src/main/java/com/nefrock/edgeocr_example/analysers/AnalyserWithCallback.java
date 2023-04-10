package com.nefrock.edgeocr_example.analysers;

import androidx.camera.core.ImageAnalysis.Analyzer;

public interface AnalyserWithCallback extends Analyzer {

    void setCallback(AnalysisCallback callback);

    void stop();

    void resume();
}
