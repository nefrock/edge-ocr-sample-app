package com.nefrock.edgeocr_example.analysers;

import androidx.camera.core.ImageAnalysis.Analyzer;

public abstract class AnalyserWithCallback implements Analyzer {
    protected AnalysisCallback callback;
    public float cropLeft, cropTop, cropSize;

    public void setCallback(AnalysisCallback callback) {
        this.callback = callback;
    }

    public void setCrop(float left, float top, float size) {
        cropLeft = left;
        cropTop = top;
        cropSize = size;
    }

    abstract public void stop();

    abstract public void resume();
}
