package com.nefrock.edgeocr_example.detection_filter;

import android.graphics.RectF;

import com.nefrock.edgeocr.api.DetectionFilter;
import com.nefrock.edgeocr.model.Detection;
import com.nefrock.edgeocr.model.ScanObject;

import java.util.Collections;
import java.util.List;

public class GetCenterDetectionFilter extends DetectionFilter {
    @Override
    public List<Detection<? extends ScanObject>> filter(List<Detection<? extends ScanObject>> list) {
        Detection<? extends ScanObject> mostLikelyBox = null;
        double minDistance = Double.MAX_VALUE;
        for (Detection<? extends ScanObject> detection: list) {
            RectF box = detection.getBoundingBox();
            double distance = Math.pow(box.centerX()-0.5, 2) + Math.pow(box.centerY()-0.5, 2);
            if (distance < minDistance) {
                minDistance = distance;
                mostLikelyBox = detection;
            }
        }
        if ( mostLikelyBox != null) {
            return Collections.singletonList(mostLikelyBox);
        } else {
            return Collections.emptyList();
        }
    }
}