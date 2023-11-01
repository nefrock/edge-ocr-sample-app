package com.nefrock.edgeocr_example.custom_analyzer;

import androidx.camera.core.ImageAnalysis.Analyzer;

abstract class AnalyzerWithCallback implements Analyzer {
    protected AnalysisCallback callback;

    public void setCallback(AnalysisCallback callback) {
        this.callback = callback;
    }

    abstract public void stop();

    abstract public void resume();
}
